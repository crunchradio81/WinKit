import com.crownparkcomputing.retrodos.winkit.GuestStore
import com.crownparkcomputing.retrodos.winkit.OfflineSmokeFixture
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    var passed = 0
    fun ok(v: Boolean, label: String) { check(v) { label }; passed++ }
    val base = Files.createTempDirectory("winkit-a10-no-replace-").toFile()
    try {
        val probe = File(base, "probe")
        val staged = File(base, ".stage")
        staged.writeText("A")
        probe.writeText("CRITICAL ORIGINAL")
        try { Files.createLink(probe.toPath(), staged.toPath()); error("overwritten existing") }
        catch (_: FileAlreadyExistsException) { passed++ }
        ok(probe.readText() == "CRITICAL ORIGINAL", "preexisting file unchanged")
        staged.delete()
        probe.delete()
        staged.writeText("immutable bytes")
        Files.createLink(probe.toPath(), staged.toPath())
        staged.delete()
        ok(probe.readText() == "immutable bytes", "stage deletion preserves published data")
        probe.delete()

        // A destination created *during* import may not be overwritten.
        val root = File(base, "guest-race"); ok(root.mkdir(), "root made")
        val bytes = ByteArray(4096) { (it xor 55).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val store = GuestStore(root)
        val competing = "YARDOMOB original copy; do not replace"
        var injected = false
        try {
            store.importVerified(ByteArrayInputStream(bytes), hash, bytes.size.toLong()) {
                if (!injected) { store.installedImage().writeText(competing); injected = true }
            }
            error("import replaced competing disk")
        } catch (_: IllegalArgumentException) { passed++ }
        ok(injected, "simulated competing writer")
        ok(store.installedImage().readText() == competing, "competing HDD untouched")
        ok(File(root,"machines").listFiles()!!.none { it.name.endsWith(".partial") }, "import staging removed")

        // Two processes acting like two instances: exactly one publishes a disk.
        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val raceRoot = File(base, "simultaneous"); ok(raceRoot.mkdir(), "parallel root")
        val futures = (0 until 2).map {
            pool.submit<Boolean> {
                ready.countDown(); gate.await(10, TimeUnit.SECONDS)
                try {
                    GuestStore(raceRoot).importVerified(ByteArrayInputStream(bytes), hash, bytes.size.toLong())
                    true
                } catch (_: Exception) { false }
            }
        }
        ok(ready.await(10, TimeUnit.SECONDS), "parallel callers started")
        gate.countDown()
        ok(futures.count { it.get(30, TimeUnit.SECONDS) } == 1, "exactly one importer succeeded")
        pool.shutdownNow()
        val published=File(raceRoot,"machines/YARDOMOB.raw")
        ok(published.readBytes().contentEquals(bytes), "winner HDD complete")
        ok(published.parentFile.listFiles()!!.none { it.name.endsWith(".partial") }, "no transient files")

        // Fixture never replaces a pre-existing config/marker or guest disk.
        for (name in listOf("YARDOMOB.raw","YARDOMOB.offline.conf","OFFLINE_READY")) {
            val root3=File(base,"fixture-${name.replace('.','_')}"); ok(root3.mkdir(),"fixture root")
            val dir=File(root3,"machines"); ok(dir.mkdir(),"machines root")
            File(dir,name).writeText("RESERVED ORIGINAL")
            try { OfflineSmokeFixture.install(root3); error("fixture accepted occupied $name") }
            catch (_: IllegalArgumentException) { passed++ }
            ok(File(dir,name).readText()=="RESERVED ORIGINAL", "fixture left $name intact")
        }
        println("PASS A10: $passed no-replace/concurrency assertions (host JVM only)")
    } finally { base.deleteRecursively() }
}
