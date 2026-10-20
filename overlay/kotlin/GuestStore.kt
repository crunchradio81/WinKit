package com.crownparkcomputing.retrodos.winkit

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** App-private, checksum-gated, atomic-enough single-image import. No network or OS media included. */
class GuestStore(private val privateFiles: File) {
    data class Receipt(val image: File, val sha256: String, val bytes: Long)
    private val expectedFormat = Regex("[0-9a-fA-F]{64}")
    private val lock = Any()
    private fun digestHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    fun installedImage(): File = File(File(privateFiles, "machines"), "YARDOMOB.raw")

    fun importVerified(
        source: InputStream,
        expectedSha256: String,
        expectedSize: Long? = null,
        onProgress: (Long) -> Unit = {}
    ): Receipt = synchronized(lock) {
        require(expectedFormat.matches(expectedSha256)) { "SHA256 from export manifest is required" }
        if (expectedSize != null) require(expectedSize >= 512 && expectedSize % 512 == 0L)
        val machines = File(privateFiles, "machines")
        require(privateFiles.isDirectory && !privateFiles.isSymbolicLink()) { "Invalid app-private root" }
        if (!machines.exists()) require(machines.mkdir()) { "Cannot create machine storage" }
        require(machines.isDirectory && !machines.isSymbolicLink()) { "Invalid machine storage" }
        val dst = installedImage()
        require(!dst.exists() && !dst.isSymbolicLink()) { "Installed disk already exists; refusing overwrite" }
        // createNewFile is exclusive; random part avoids clashes with abandoned staging files.
        val tmp = File(machines, ".import-${java.util.UUID.randomUUID()}.partial")
        require(tmp.createNewFile()) { "Cannot allocate staging file" }
        val sha = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            FileOutputStream(tmp, false).use { output ->
                val buf = ByteArray(1024 * 1024)
                while (true) {
                    val read = source.read(buf)
                    if (read == -1) break
                    if (read == 0) continue
                    total = Math.addExact(total, read.toLong())
                    if (expectedSize != null && total > expectedSize) error("Image exceeds manifest image_bytes")
                    output.write(buf, 0, read)
                    sha.update(buf, 0, read)
                    onProgress(total)
                }
                output.fd.sync()
            }
            require(total >= 512 && total % 512L == 0L && (expectedSize == null || total == expectedSize)) {
                "Bad image byte count/sector alignment"
            }
            val actual = digestHex(sha.digest())
            require(actual.equals(expectedSha256, true)) { "Checksum mismatch; image rejected" }
            require(!dst.exists() && !dst.isSymbolicLink()) { "Destination appeared; image not installed" }
            // Android may deny hard links: exclusive, synced publication instead.
            NoReplacePublisher.publish(tmp, dst)
            return@synchronized Receipt(dst, actual, total)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    // Java 8-compatible check: no following of the final path component.
    private fun File.isSymbolicLink(): Boolean = java.nio.file.Files.isSymbolicLink(toPath())
}

/** Exclusively publishes from a staged app-private file without optional Android hard links.
 * This method does not publish OFFLINE_READY; callers write that marker LAST.
 * A failed partial copy leaves a reserved unready destination; never delete it.
 */
internal object NoReplacePublisher {
    fun publish(stage: File, destination: File) {
        require(stage.parentFile.canonicalFile == destination.parentFile.canonicalFile) {
            "Publication must stay in the same private directory"
        }
        require(stage.isFile && !java.nio.file.Files.isSymbolicLink(stage.toPath())) { "Invalid staged file" }
        java.io.FileInputStream(stage).channel.use { input ->
            java.nio.channels.FileChannel.open(destination.toPath(),
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.LinkOption.NOFOLLOW_LINKS).use { output ->
                val buffer = java.nio.ByteBuffer.allocateDirect(256 * 1024)
                var copied = 0L
                while (true) {
                    buffer.clear()
                    val n = input.read(buffer)
                    if (n == -1) break
                    if (n == 0) continue
                    buffer.flip()
                    while (buffer.hasRemaining()) output.write(buffer)
                    copied = Math.addExact(copied, n.toLong())
                }
                output.force(true)
                check(copied == stage.length()) { "Staged file changed during exclusive publication" }
            }
        }
        // Crash/IO failure leaves reserved name; without OFFLINE_READY no guest boot.
    }
}
