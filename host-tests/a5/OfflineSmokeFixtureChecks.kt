import com.crownparkcomputing.retrodos.winkit.OfflineDiskGeometry
import com.crownparkcomputing.retrodos.winkit.OfflineSmokeFixture
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

fun main(args: Array<String>) {
    var n = 0
    fun check(v: Boolean) { n++; if (!v) error("A5 on-device fixture check $n failed") }
    val root = Files.createTempDirectory("winkit-a5-kotlin").toFile()
    try {
        val img = OfflineSmokeFixture.install(root)
        val config = File(root, "machines/YARDOMOB.offline.conf")
        val marker = File(root, "machines/OFFLINE_READY")
        check(img.length() == OfflineSmokeFixture.IMAGE_BYTES)
        check(config.isFile && marker.readText() == "a2-offline\n")
        check(OfflineDiskGeometry.matches(config.readText(),img))
        check(img.inputStream().use { data ->
            val hash = MessageDigest.getInstance("SHA-256")
            val buf=ByteArray(65536)
            while(true) { val c=data.read(buf); if(c<0) break; if(c>0) hash.update(buf,0,c) }
            hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        } == args[0])
        try { OfflineSmokeFixture.install(root); error("overwrote live fixture") }
        catch (_: IllegalArgumentException) { n++ }
        val markerOld=marker.readBytes()
        check(markerOld.contentEquals("a2-offline\n".toByteArray()))
        val other=Files.createTempDirectory("winkit-a5-existing").toFile()
        try {
            val machines=File(other,"machines");check(machines.mkdir())
            val existing=File(machines,"YARDOMOB.raw");existing.writeText("existing guest disk")
            try { OfflineSmokeFixture.install(other); error("overwrote someone else's guest") }
            catch (_: IllegalArgumentException) { n++ }
            check(existing.readText()=="existing guest disk")
            check(!File(machines,"OFFLINE_READY").exists())
        } finally { other.deleteRecursively() }
        println("A5 independent Kotlin smoke install: $n assertions passed")
    } finally { root.deleteRecursively() }
}
