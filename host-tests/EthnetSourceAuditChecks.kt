import org.winkit.transport.EthnetWire

private fun decode(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
fun main() {
    var n = 0
    fun ck(result: Boolean, reason: String) { check(result) { reason }; n++ }
    val mac = decode("02574b331103")
    val packet = EthnetWire.control(0,0,0, EthnetWire.Message.SCCRQ, listOf(
        EthnetWire.avp(61, decode("11223344")),
        EthnetWire.avp(3, decode("00000003")),
        EthnetWire.avp(62, decode("0005")),
        EthnetWire.avp(0x4000, mac, 0xD05B)
    ))
    // Control bytes independently transcribed from DOSBox-X ctrlmsg_hdr(), AVP header functions.
    val expected = "c803003c0000000000000000" +
        "8008000000000001" + "000a0000003d11223344" +
        "000a0000000300000003" + "00080000003e0005" +
        "000cd05b400002574b331103"
    ck(encode(packet) == expected, "SCCRQ golden bytes")
    val parsed = EthnetWire.parseControl(decode(expected)) ?: error("source-derived control packet rejected")
    ck(parsed.message() == 1, "message")
    ck(parsed.number(61) == 0x11223344L, "local conn id")
    ck(parsed.number(3) == 3L, "framing capabilities")
    ck(parsed.attribute(62)?.contentEquals(decode("0005")) == true, "pseudowire=ethernet")
    ck(parsed.attribute(0x4000,0xd05b)?.contentEquals(mac) == true,"vendor MAC")
    val max = ByteArray(EthnetWire.MAX_DATAGRAM - 8) { 1 }
    ck(EthnetWire.data(2, max).size == EthnetWire.MAX_DATAGRAM, "upstream max datagram")
    ck(EthnetWire.parseData(EthnetWire.data(2,max))?.frame?.size == 4088, "upstream max frame")
    ck(EthnetWire.parseControl(packet.copyOf(packet.size-1)) == null, "truncation")
    val forbiddenHiddenAvp = packet.copyOf(); forbiddenHiddenAvp[12] = (0xC0).toByte()
    ck(EthnetWire.parseControl(forbiddenHiddenAvp) == null,"hidden AVP")
    println("PASS $n upstream-source-derived ETHNET Kotlin codec assertions (OFFLINE ONLY)")
}
