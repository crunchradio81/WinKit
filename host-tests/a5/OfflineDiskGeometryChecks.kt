import com.crownparkcomputing.retrodos.winkit.OfflineDiskGeometry
import java.io.File
import java.io.RandomAccessFile

fun main(args: Array<String>) {
    val img = File(args[0]); val path = img.absolutePath
    var checks = 0
    fun check(cond: Boolean) { checks++; if (!cond) error("A5 Kotlin check $checks FAILED") }
    fun conf(chs: String = "512,63,16,8") = "[ne2000]\nne2000=false\n[ipx]\nipx=false\n[autoexec]\n"+
            "IMGMOUNT 2 \"$path\" -t hdd -fs none -size $chs\nBOOT C:\n"
    check(OfflineDiskGeometry.expectedBytes(conf(), path) == img.length())
    check(OfflineDiskGeometry.matches(conf(), img))
    for (g in listOf("1024,63,16,8", "512,64,16,8", "512,63,256,8", "512,63,16,65536", "0,63,16,8", "512,63,16,0"))
        check(OfflineDiskGeometry.expectedBytes(conf(g), path) == null)
    check(OfflineDiskGeometry.expectedBytes(conf("512,63,16,7"),path) != img.length())
    check(!OfflineDiskGeometry.matches(conf("512,63,16,7"),img))
    check(!OfflineDiskGeometry.matches(conf().replace("BOOT C:","ETHNET CONNECT 100.64.0.2\nBOOT C:"),img))
    check(!OfflineDiskGeometry.matches(conf(),File(path+"-not-found")))
    fun mutate(at: Long, content: ByteArray) {
        RandomAccessFile(img,"rw").use { f ->
            f.seek(at); val saved=ByteArray(content.size); f.readFully(saved)
            try { f.seek(at); f.write(content); check(!OfflineDiskGeometry.matches(conf(),img)) }
            finally { f.seek(at); f.write(saved) }
        }
        check(OfflineDiskGeometry.matches(conf(),img))
    }
    mutate(0,"QFI\u00fb".toByteArray(Charsets.ISO_8859_1))
    mutate(0,"KDMV".toByteArray())
    mutate(0,"vhdxfile".toByteArray())
    mutate(0,byteArrayOf(0x50,0x4b,0x03,0x04))
    mutate(0,"conectix".toByteArray())
    mutate(img.length()-512,"conectix".toByteArray())
    println("A5 Kotlin raw geometry + container: $checks assertions passed")
}
