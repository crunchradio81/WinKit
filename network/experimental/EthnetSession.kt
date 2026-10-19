// SPDX-License-Identifier: GPL-2.0-or-later
package org.winkit.transport

import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.SecureRandom

/** Transport for use at the *emulator's NE2000 TX/RX boundary*.
 * Does not boot or emulate any DOS/Windows OS by itself.
 * Call pump() repeatedly on a dedicated I/O thread (not the Android UI thread).
 */
class EthnetSession private constructor(
    private val socket: DatagramSocket,
    private val clientId: Long,
    private val serverId: Long,
    private val incomingSessionId: Long,
    private val outgoingSessionId: Long,
    private var ns: Int,
    private var nr: Int
): Closeable {
    private var lastOutboundHello = System.nanoTime()
    private var lastInbound = System.nanoTime()
    private var closed = false

    companion object {
        private val rng = SecureRandom()
        private fun id(): Long = (rng.nextInt().toLong() and 0xffffffffL).let { if (it == 0L) 1L else it }
        private fun avp(type: Int, bytes: ByteArray, vendor: Int = 0) = EthnetWire.avp(type, bytes, vendor)
        private fun cap() = avp(62, EthnetWire.shortValue(5))
        private fun isSafeTailnetAddress(s: String): Boolean {
            val x = s.split('.')
            if (x.size != 4 || x.any { it.length !in 1..3 || it.any { c -> !c.isDigit() } }) return false
            val a = x.map { it.toIntOrNull() ?: return false }
            return a.all { it in 0..255 } && a[0] == 100 && a[1] in 64..127
        }
        /** Set allowLoopbackForTests only in isolated tests, never production UI. */
        fun connect(host: String, port: Int = 1234, guestMac: ByteArray, allowLoopbackForTests: Boolean = false): EthnetSession {
            require(port in 1024..65535)
            require(guestMac.size == 6 && (guestMac[0].toInt() and 3) == 2) { "Unique locally administered unicast guest MAC required" }
            require(isSafeTailnetAddress(host) || (allowLoopbackForTests && host == "127.0.0.1")) {
                "Require numeric Tailscale IPv4; LAN/loopback only permitted in isolated tests"
            }
            val clientId = id()
            val clientSession = id()
            val serverSession = id()
            val sock = DatagramSocket(null)
            try {
                sock.bind(InetSocketAddress(0))
                sock.connect(InetAddress.getByName(host), port)
                sock.soTimeout = 350
                var ns = 0
                var nr = 0
                fun send(payload: ByteArray) = sock.send(DatagramPacket(payload, payload.size))
                fun sendControl(targetId: Long, type: EthnetWire.Message, attrs: List<EthnetWire.Attribute> = emptyList()) {
                    send(EthnetWire.control(targetId, ns++, nr, type, attrs))
                    ns = ns and 0xffff
                }
                fun recv(expected: EthnetWire.Message): EthnetWire.Control {
                    val until = System.nanoTime() + 5_000_000_000L
                    val buf = ByteArray(EthnetWire.MAX_DATAGRAM)
                    while (System.nanoTime() < until) {
                        try {
                            val packet = DatagramPacket(buf, buf.size)
                            sock.receive(packet)
                            val control = EthnetWire.parseControl(buf.copyOf(packet.length)) ?: continue
                            if (control.connectionId != clientId) continue
                            if (control.message() == expected.number) {
                                nr = (control.ns + 1) and 0xffff
                                return control
                            }
                        } catch (_: SocketTimeoutException) {}
                    }
                    throw IOException("Timed out waiting for DOSBox-X ETHNET $expected from $host:$port")
                }
                sendControl(0, EthnetWire.Message.SCCRQ, listOf(
                    avp(61, EthnetWire.intValue(clientId)), avp(3, EthnetWire.intValue(3)),
                    avp(60, EthnetWire.intValue(id())), cap(),
                    avp(8, "WinKit Android".toByteArray(Charsets.US_ASCII)),
                    avp(7, "WinKit-Android".toByteArray(Charsets.US_ASCII)),
                    avp(EthnetWire.DOSBOX_MAC_ATTR, guestMac.copyOf(), EthnetWire.DOSBOX_VENDOR)
                ))
                val sccrp = recv(EthnetWire.Message.SCCRP)
                val serverId = sccrp.number(61) ?: 0
                val caps = sccrp.attribute(62)
                if (serverId == 0L || sccrp.number(3) == 0L || caps == null || !caps.contentEquals(EthnetWire.shortValue(5)))
                    throw IOException("Incompatible ETHNET server handshake")
                sendControl(serverId, EthnetWire.Message.SCCCN)
                sendControl(serverId, EthnetWire.Message.ICRQ, listOf(
                    avp(63, EthnetWire.intValue(clientSession)), avp(64, EthnetWire.intValue(serverSession)),
                    cap(), avp(8, "WinKit Android".toByteArray(Charsets.US_ASCII))
                ))
                val icrp = recv(EthnetWire.Message.ICRP)
                if (icrp.number(63) != serverSession || icrp.number(64) != clientSession ||
                    !(icrp.attribute(62) ?: byteArrayOf()).contentEquals(EthnetWire.shortValue(5))) {
                    throw IOException("Incompatible ETHNET session identifiers")
                }
                sendControl(serverId, EthnetWire.Message.ICCN, listOf(
                    avp(63, EthnetWire.intValue(clientSession)), avp(64, EthnetWire.intValue(serverSession))
                ))
                return EthnetSession(sock, clientId, serverId, clientSession, serverSession, ns, nr)
            } catch (ex: Exception) {
                sock.close()
                throw ex
            }
        }
    }

    private fun control(type: EthnetWire.Message, attrs: List<EthnetWire.Attribute> = emptyList()) {
        val p = EthnetWire.control(serverId, ns, nr, type, attrs)
        ns = (ns + 1) and 0xffff
        socket.send(DatagramPacket(p, p.size))
    }
    fun sendEthernet(frame: ByteArray) {
        check(!closed)
        val data = EthnetWire.data(outgoingSessionId, frame)
        socket.send(DatagramPacket(data, data.size))
    }
    /** Call at least every second; emits HELLO and handles incoming frames. */
    fun pump(onEthernet: (ByteArray) -> Unit) {
        check(!closed)
        val now = System.nanoTime()
        if (now - lastInbound > 15_000_000_000L) throw IOException("ETHNET idle timeout; reconnect required")
        if (now - lastOutboundHello > 2_000_000_000L) {
            control(EthnetWire.Message.HELLO)
            lastOutboundHello = now
        }
        val buf = ByteArray(EthnetWire.MAX_DATAGRAM)
        try {
            val packet = DatagramPacket(buf, buf.size)
            socket.receive(packet)
            val bytes = buf.copyOf(packet.length)
            val cp = EthnetWire.parseControl(bytes)
            if (cp != null && cp.connectionId == clientId) {
                lastInbound = System.nanoTime()
                nr = (cp.ns + 1) and 0xffff
                if (cp.message() == EthnetWire.Message.HELLO.number) control(EthnetWire.Message.ACK)
            } else {
                val frame = EthnetWire.parseData(bytes)
                if (frame != null && frame.sessionId == incomingSessionId) {
                    lastInbound = System.nanoTime()
                    onEthernet(frame.frame)
                }
            }
        } catch (_: SocketTimeoutException) {}
    }
    override fun close() {
        if (closed) return
        try { control(EthnetWire.Message.STOP) } catch (_: Exception) {}
        closed = true
        socket.close()
    }
}
