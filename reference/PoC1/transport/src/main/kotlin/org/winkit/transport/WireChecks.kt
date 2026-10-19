// SPDX-License-Identifier: GPL-2.0-or-later
package org.winkit.transport

fun runWireChecks() {
    var n = 0
    fun ck(value: Boolean, name: String) { check(value) { name }; n++ }
    val mac = byteArrayOf(2, 0x57, 0x4b, 0x31, 0x10, 0x01)
    val attrs = listOf(
        EthnetWire.avp(61, EthnetWire.intValue(0x12345678L)),
        EthnetWire.avp(62, EthnetWire.shortValue(5)),
        EthnetWire.avp(0x4000, mac, 0xD05B)
    )
    val encoded = EthnetWire.control(0, 0, 0, EthnetWire.Message.SCCRQ, attrs)
    val decoded = EthnetWire.parseControl(encoded)
    ck(decoded != null, "valid control parse")
    ck(decoded!!.message() == EthnetWire.Message.SCCRQ.number, "control message type")
    ck(decoded.number(61) == 0x12345678L, "connection id avp")
    ck(decoded.attribute(62)?.contentEquals(byteArrayOf(0, 5)) == true, "pseudowire caps")
    ck(decoded.attribute(0x4000, 0xD05B)?.contentEquals(mac) == true, "vendor mac avp")
    ck(EthnetWire.parseControl(encoded.copyOf(encoded.size - 1)) == null, "truncated control")
    val malformed = encoded.copyOf().also { it[12] = 0; it[13] = 4 }
    ck(EthnetWire.parseControl(malformed) == null, "short avp length")
    ck(EthnetWire.parseControl(byteArrayOf()) == null, "empty control")
    val frame = byteArrayOf(-1,-1,-1,-1,-1,-1,2,0x57,0x4b,0x31,0x10,0x01,0,14)
    val packet = EthnetWire.data(0x10203040, frame)
    ck(packet.size == frame.size + 8, "frame envelope size")
    ck(EthnetWire.parseData(packet)?.sessionId == 0x10203040L, "data session id")
    ck(EthnetWire.parseData(packet)?.frame?.contentEquals(frame) == true, "frame roundtrip")
    ck(EthnetWire.parseData(packet.copyOf(10)) == null, "short frame rejected")
    ck(EthnetWire.parseData(packet.copyOf().also { it[0] = 1 }) == null, "nondata rejected")
    println("$n ETHNET wire checks passed")
}
