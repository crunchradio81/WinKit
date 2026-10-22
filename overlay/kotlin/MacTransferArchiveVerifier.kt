package com.crownparkcomputing.retrodos.winkit

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Core-only, independently JVM-testable RC24.6 ZIP validation. No Android API,
 * emulator, network, guest image, or Windows source is needed to test this.
 *
 * Caller supplies a fully validated typed manifest decoded from JSON; no
 * disk becomes boot-ready here. This writes ONLY to a pre-reserved stage file.
 */
internal object MacTransferArchiveVerifier {
    data class Manifest(
        val schema: String,
        val guestName: String,
        val privateDiskRequired: Boolean,
        val ethernetEnabled: Boolean,
        val identitySetupRequired: Boolean,
        val diskEntry: String,
        val diskFormat: String,
        val sectorBytes: Int,
        val diskBytes: Long,
        val diskSha256: String
    )
    data class Receipt(val bytes: Long, val sha256: String)

    private const val IMAGE_ENTRY = "disk/YARDOMOB.raw"
    private const val LIMIT = 65536
    private const val CAP = 128L * 1024 * 1024 * 1024
    private const val RESERVE = 64L * 1024 * 1024
    private val SHA = Regex("[a-f0-9]{64}")

    fun stage(
        source: InputStream,
        stageFile: File,
        freeBytes: Long,
        parseManifest: (String) -> Manifest
    ): Receipt {
        require(stageFile.isFile && !java.nio.file.Files.isSymbolicLink(stageFile.toPath())) {
            "Expected an exclusive private staging file"
        }
        ZipInputStream(source.buffered()).use { zip ->
            val first = zip.nextEntry ?: error("Missing manifest")
            require(!first.isDirectory && first.name == "manifest.json") { "Manifest must come first" }
            val manifest = parseManifest(String(readSmall(zip), Charsets.UTF_8))
            zip.closeEntry() // CRC check
            require(manifest.schema == "winkit-android-disk-transfer-v1") { "Wrong transfer schema" }
            require(manifest.guestName == "YARDOMOB") { "Wrong guest identity" }
            require(manifest.privateDiskRequired && !manifest.ethernetEnabled && manifest.identitySetupRequired) {
                "Unsafe first-boot target"
            }
            require(manifest.diskEntry == IMAGE_ENTRY && manifest.diskFormat == "raw-sector-image")
            require(manifest.sectorBytes == 512)
            val n = manifest.diskBytes
            require(n >= 512L && n % 512L == 0L && n <= CAP) { "Invalid raw disk size" }
            require(SHA.matches(manifest.diskSha256)) { "Invalid disk SHA-256" }
            // Stage + GuestStore's verified temporary copy + published guest disk.
            require(freeBytes > RESERVE && n <= (freeBytes - RESERVE) / 3L) {
                "Insufficient storage for three safely staged disk copies"
            }
            val readme = zip.nextEntry ?: error("Missing README")
            require(!readme.isDirectory && readme.name == "README.txt") { "Wrong README entry" }
            readSmall(zip)
            zip.closeEntry() // CRC check
            val disk = zip.nextEntry ?: error("Missing raw disk")
            require(!disk.isDirectory && disk.name == IMAGE_ENTRY) { "Unexpected disk entry" }
            val sha = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            FileOutputStream(stageFile, false).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val k = zip.read(buffer)
                    if (k < 0) break
                    if (k == 0) continue
                    copied = Math.addExact(copied, k.toLong())
                    require(copied <= n) { "Guest disk larger than manifest" }
                    sha.update(buffer, 0, k)
                    output.write(buffer, 0, k)
                }
                output.fd.sync()
            }
            zip.closeEntry() // CRC check BEFORE publication
            require(zip.nextEntry == null) { "Unexpected extra ZIP entries" }
            val actual = sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(copied == n && actual == manifest.diskSha256) { "Raw disk checksum mismatch" }
            return Receipt(copied, actual)
        }
    }

    private fun readSmall(zip: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) return out.toByteArray()
            if (n == 0) continue
            require(n <= LIMIT - out.size()) { "Transfer metadata exceeds 64 KiB" }
            out.write(buffer, 0, n)
        }
    }
}
