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
            // A same-directory hard link is an atomic NO-REPLACE publish on app-private storage.
            // Traditional rename could overwrite a destination created after the preceding check.
            java.nio.file.Files.createLink(dst.toPath(), tmp.toPath())
            return@synchronized Receipt(dst, actual, total)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    // Java 8-compatible check: no following of the final path component.
    private fun File.isSymbolicLink(): Boolean = java.nio.file.Files.isSymbolicLink(toPath())
}
