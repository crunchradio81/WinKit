package org.winkit.android

import java.security.SecureRandom

/** A unique NIC address does not rename Windows: name must be changed in guest OFFLINE. */
object GuestIdentity {
    const val COMPUTER_NAME = "YARDOMOB"
    fun newLocalMac(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte() // locally administered unicast
        return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }
    fun validUnicastMac(mac: String): Boolean {
        val parts = mac.split(":")
        if (parts.size != 6 || parts.any { it.length != 2 || it.toIntOrNull(16) == null }) return false
        val first = parts[0].toInt(16)
        return first and 3 == 2 && parts.any { it != "00" }
    }
}
