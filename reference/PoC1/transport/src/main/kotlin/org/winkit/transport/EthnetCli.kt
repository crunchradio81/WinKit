// SPDX-License-Identifier: GPL-2.0-or-later
package org.winkit.transport

/** Developer-only host-JVM smoke tool. Does not run DOS or Windows. */
fun main(args: Array<String>) {
    if (args.size == 1 && args[0] == "--unit-checks") {
        runWireChecks()
        return
    }
    if (args.size !in 2..3 || !args[0].matches(Regex("[0-9.]+"))) {
        System.err.println("Usage: java -jar transport.jar <numeric-tailscale-IPv4> <seconds> (for tests use 127.0.0.1)")
        kotlin.system.exitProcess(2)
    }
    val seconds = args[1].toIntOrNull() ?: error("seconds must be integer")
    require(seconds in 1..30)
    val localTest = args[0] == "127.0.0.1"
    EthnetSession.connect(args[0], 1234, byteArrayOf(2, 0x57, 0x4b, 0x31, 0x10, 0x01), localTest).use { s ->
        println("ETHNET_TRANSPORT_HANDSHAKE_OK (not guest connectivity)")
        if (args.size == 3) {
            require(localTest && args[2] == "--loopback-test-frame")
            val frame = byteArrayOf(-1, -1, -1, -1, -1, -1, 2, 0x57, 0x4b, 0x31, 0x10, 0x01, 0, 14)
            s.sendEthernet(frame)
        }
        val until = System.nanoTime() + seconds * 1_000_000_000L
        while (System.nanoTime() < until) s.pump { println("RECEIVED_ETHERNET_FRAME bytes=${it.size}") }
    }
}
