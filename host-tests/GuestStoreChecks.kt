import org.winkit.android.GuestStore
import org.winkit.android.GuestIdentity
import org.winkit.android.BootProfile
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest

fun main() {
    var passed = 0
    fun checkThat(value: Boolean, why: String) { check(value) { why }; passed++ }
    fun rejected(why: String, action: () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalArgumentException) { failed = true } catch (_: IllegalStateException) { failed = true }
        checkThat(failed, why)
    }
    val root = Files.createTempDirectory("winkit-a1-store-").toFile()
    try {
        val bytes = ByteArray(512 * 9) { (it * 13).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val store = GuestStore(root)
        rejected("bad SHA is rejected") { store.importVerified(ByteArrayInputStream(bytes), "bad") }
        rejected("checksum mismatch fails") { store.importVerified(ByteArrayInputStream(bytes), "0".repeat(64)) }
        checkThat(!store.installedImage().exists(), "no artifact left on mismatch")
        rejected("misaligned image rejected") { store.importVerified(ByteArrayInputStream(bytes.copyOf(513)), hash) }
        val receipt = store.importVerified(ByteArrayInputStream(bytes), hash, bytes.size.toLong())
        checkThat(receipt.image.name == "YARDOMOB.raw", "correct private destination")
        checkThat(receipt.bytes == 4608L, "correct size")
        checkThat(receipt.sha256 == hash, "hash recorded")
        checkThat(receipt.image.readBytes().contentEquals(bytes), "bytewise import")
        rejected("cannot overwrite existing installed image") { store.importVerified(ByteArrayInputStream(bytes), hash) }
        checkThat(receipt.image.readBytes().contentEquals(bytes), "original installed disk unchanged")
        checkThat(BootProfile.auditImageName("YARDOMOB.raw"), "only safe image filename")
        checkThat(!BootProfile.networkingPermitted(true, true), "network hard disabled")
        repeat(100) { checkThat(GuestIdentity.validUnicastMac(GuestIdentity.newLocalMac()), "valid LAA") }
        checkThat(!GuestIdentity.validUnicastMac("FF:FF:FF:FF:FF:FF"), "reject broadcast")
        checkThat(!GuestIdentity.validUnicastMac("00:00:00:00:00:00"), "reject nonlocal")
        val badRoot = Files.createTempDirectory("winkit-a1-symlink-").toFile()
        try {
            val outside = Files.createTempDirectory("winkit-a1-outside-")
            try {
                Files.createSymbolicLink(badRoot.toPath().resolve("machines"), outside)
                rejected("machine dir symlink rejected") { GuestStore(badRoot).importVerified(ByteArrayInputStream(bytes), hash) }
                checkThat(Files.list(outside).use { it.count() } == 0L, "no external symlink write")
            } finally { outside.toFile().deleteRecursively() }
        } finally { badRoot.deleteRecursively() }
        println("PASS $passed Kotlin storage + identity assertions (JVM, NOT Android)")
    } finally { root.deleteRecursively() }
}
