// SPDX-License-Identifier: GPL-2.0-or-later
package org.winkit.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** DOSBox-X experimental ETHNET uses a custom L2TPv3-like UDP envelope.
 * This codec is NOT an ordinary L2TP implementation and is not encrypted.
 */
object EthnetWire {
    const val MAX_DATAGRAM = 4096
    const val MAX_FRAME = MAX_DATAGRAM - 8 // Upstream accepts any frame >=14 within its 4096-byte UDP receive buffer
    const val MIN_FRAME = 14
    const val PSEUDOWIRE_ETHERNET = 5
    const val DOSBOX_VENDOR = 0xD05B
    const val DOSBOX_MAC_ATTR = 0x4000

    enum class Message(val number: Int) {
        SCCRQ(1), SCCRP(2), SCCCN(3), STOP(4), HELLO(6), ICRQ(10), ICRP(11), ICCN(12), ACK(20)
    }

    data class Attribute(val vendor: Int, val type: Int, val payload: ByteArray, val mandatory: Boolean = false)
    data class Control(val connectionId: Long, val ns: Int, val nr: Int, val attributes: List<Attribute>) {
        fun attribute(type: Int, vendor: Int = 0): ByteArray? = attributes.firstOrNull { it.type == type && it.vendor == vendor }?.payload
        fun number(type: Int, vendor: Int = 0): Long? {
            val b = attribute(type, vendor) ?: return null
            return when (b.size) { 2 -> ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).short.toInt().and(0xffff).toLong()
                4 -> ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xffffffffL
                else -> null }
        }
        fun message(): Int = number(0)?.toInt() ?: -1
    }
    data class Data(val sessionId: Long, val frame: ByteArray)

    fun shortValue(n: Int): ByteArray {
        require(n in 0..0xffff)
        return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(n.toShort()).array()
    }
    fun intValue(n: Long): ByteArray {
        require(n in 0..0xffffffffL)
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(n.toInt()).array()
    }
    fun avp(type: Int, payload: ByteArray, vendor: Int = 0, mandatory: Boolean = false) =
        Attribute(vendor, type, payload, mandatory)

    fun control(connectionId: Long, ns: Int, nr: Int, message: Message, attributes: List<Attribute> = emptyList()): ByteArray {
        require(connectionId in 0..0xffffffffL && ns in 0..0xffff && nr in 0..0xffff)
        val attrs = listOf(avp(0, shortValue(message.number), mandatory = true)) + attributes
        val sz = 12 + attrs.sumOf { 6 + it.payload.size }
        require(sz <= MAX_DATAGRAM)
        val out = ByteBuffer.allocate(sz).order(ByteOrder.BIG_ENDIAN)
        out.put(0xc8.toByte()); out.put(3.toByte())
        out.putShort(sz.toShort()); out.putInt(connectionId.toInt()); out.putShort(ns.toShort()); out.putShort(nr.toShort())
        attrs.forEach {
            val l = 6 + it.payload.size
            require(l <= 1023 && it.vendor in 0..65535 && it.type in 0..65535)
            out.putShort(((if (it.mandatory) 0x8000 else 0) or l).toShort())
            out.putShort(it.vendor.toShort()); out.putShort(it.type.toShort()); out.put(it.payload)
        }
        return out.array()
    }

    fun parseControl(bytes: ByteArray): Control? {
        if (bytes.size !in 20..MAX_DATAGRAM || bytes[0] != 0xc8.toByte() || bytes[1] != 3.toByte()) return null
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if ((b.getShort(2).toInt() and 0xffff) != bytes.size) return null
        val connectionId = b.getInt(4).toLong() and 0xffffffffL
        val ns = b.getShort(8).toInt() and 0xffff
        val nr = b.getShort(10).toInt() and 0xffff
        val attrs = mutableListOf<Attribute>()
        var offset = 12
        while (offset < bytes.size) {
            if (bytes.size - offset < 6) return null
            val header = b.getShort(offset).toInt() and 0xffff
            val length = header and 0x3ff
            if (length < 6 || offset + length > bytes.size || (header and 0x4000) != 0) return null
            val vendor = b.getShort(offset + 2).toInt() and 0xffff
            val type = b.getShort(offset + 4).toInt() and 0xffff
            attrs += Attribute(vendor, type, bytes.copyOfRange(offset+6, offset+length), (header and 0x8000) != 0)
            offset += length
        }
        return Control(connectionId, ns, nr, attrs)
    }

    fun data(sessionId: Long, frame: ByteArray): ByteArray {
        require(sessionId in 1..0xffffffffL)
        require(frame.size in MIN_FRAME..MAX_FRAME)
        val out = ByteBuffer.allocate(8 + frame.size).order(ByteOrder.BIG_ENDIAN)
        out.put(0); out.put(3); out.putShort(0); out.putInt(sessionId.toInt()); out.put(frame)
        return out.array()
    }
    fun parseData(bytes: ByteArray): Data? {
        if (bytes.size !in (8+MIN_FRAME)..(8+MAX_FRAME) || bytes[0] != 0.toByte() || bytes[1] != 3.toByte() ||
            bytes[2] != 0.toByte() || bytes[3] != 0.toByte()) return null
        val session = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt(4).toLong() and 0xffffffffL
        if (session == 0L) return null
        return Data(session, bytes.copyOfRange(8, bytes.size))
    }
}
