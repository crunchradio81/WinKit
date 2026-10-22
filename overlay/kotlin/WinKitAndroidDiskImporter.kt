package com.crownparkcomputing.retrodos.winkit

import android.content.Context
import android.net.Uri
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Mac WinKit disk-transfer ZIP -> existing GuestStore machines/YARDOMOB.raw.
 * Data stays app-private. A clone is NOT a configured YARDOMOB Windows guest.
 * This importer never sets an offline-ready marker or enables Ethernet.
 */
object WinKitAndroidDiskImporter {
    private const val FORMAT = "winkit-android-disk-transfer-v1"
    private const val IMAGE = "disk/YARDOMOB.raw"
    private const val LIMIT = 65536
    data class Receipt(val image: File, val bytes: Long, val sha256: String)

    fun importArchive(context: Context, uri: Uri): Receipt {
        val image = GuestStore(context.filesDir).installedImage()
        val folder = image.parentFile ?: error("Missing guest folder")
        require(context.filesDir.isDirectory &&
                !java.nio.file.Files.isSymbolicLink(context.filesDir.toPath()))
        if (!folder.exists()) require(folder.mkdir())
        require(folder.isDirectory && !java.nio.file.Files.isSymbolicLink(folder.toPath()))
        require(!image.exists() && !java.nio.file.Files.isSymbolicLink(image.toPath())) {
            "Existing guest disk will never be replaced"
        }
        val tmp = File(folder, ".transfer-${UUID.randomUUID()}.partial")
        require(tmp.createNewFile())
        var actualBytes = 0L
        var actualHash = ""
        var manifest: JSONObject? = null
        val names = mutableListOf<String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(!entry.isDirectory && entry.name !in names)
                        names.add(entry.name)
                        require(names.size <= 3) { "Unexpected transfer ZIP content" }
                        when (names.size) {
                            1 -> {
                                require(entry.name == "manifest.json")
                                val obj = JSONObject(String(small(zip), Charsets.UTF_8))
                                val disk = obj.getJSONObject("disk")
                                val target = obj.getJSONObject("target")
                                require(obj.getString("schema") == FORMAT)
                                require(target.getString("guest_name") == "YARDOMOB")
                                require(target.getBoolean("private_disk_required") &&
                                        !target.getBoolean("ethernet_enabled") &&
                                        target.getBoolean("requires_first_boot_identity_setup"))
                                require(disk.getString("entry") == IMAGE)
                                require(disk.getString("format") == "raw-sector-image")
                                require(disk.getInt("sector_bytes") == 512)
                                val bytes = disk.getLong("bytes")
                                require(bytes >= 512 && bytes % 512L == 0L && bytes <= 128L * 1024 * 1024 * 1024)
                                require(disk.getString("sha256").matches(Regex("[0-9a-f]{64}")))
                                val free = StatFs(folder.absolutePath).availableBytes
                                require(free > 64L * 1024 * 1024 &&
                                        bytes <= (free - 64L * 1024 * 1024) / 2L) {
                                    "Insufficient private storage for a protected copy"
                                }
                                manifest = obj
                            }
                            2 -> {
                                require(entry.name == "README.txt")
                                small(zip)
                            }
                            3 -> {
                                require(entry.name == IMAGE)
                                val expected = (manifest ?: error("Missing manifest"))
                                    .getJSONObject("disk")
                                val limit = expected.getLong("bytes")
                                val md = MessageDigest.getInstance("SHA-256")
                                FileOutputStream(tmp, false).use { output ->
                                    val buf = ByteArray(1024 * 1024)
                                    while (true) {
                                        val n = zip.read(buf)
                                        if (n < 0) break
                                        if (n == 0) continue
                                        actualBytes = Math.addExact(actualBytes, n.toLong())
                                        require(actualBytes <= limit) { "Image exceeds manifest length" }
                                        output.write(buf, 0, n)
                                        md.update(buf, 0, n)
                                    }
                                    output.fd.sync()
                                }
                                actualHash = md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                                require(actualBytes == limit && actualHash == expected.getString("sha256")) {
                                    "Image length or SHA-256 mismatch"
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("Unable to open selected transfer ZIP")
            require(names == listOf("manifest.json", "README.txt", IMAGE)) {
                "Incomplete or unexpected transfer ZIP"
            }
            val gate = File(folder, "YARDOMOB.network-gate.json")
            require(!gate.exists() && !java.nio.file.Files.isSymbolicLink(gate.toPath()))
            val gateTmp = File(folder, ".network-${UUID.randomUUID()}.partial")
            require(gateTmp.createNewFile())
            try {
                FileOutputStream(gateTmp).use { output ->
                    output.write(JSONObject()
                        .put("ethernet_enabled", false)
                        .put("identity_verified", false)
                        .put("required_computer_name", "YARDOMOB")
                        .put("clone_sha256", actualHash)
                        .toString(2).toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                // Publish network DENY before the guest disk is discoverable.
                NoReplacePublisher.publish(gateTmp, gate)
            } finally {
                gateTmp.delete()
            }
            NoReplacePublisher.publish(tmp, image)
            // No OFFLINE_READY marker; separate Android offline boot gate remains mandatory.
            return Receipt(image, actualBytes, actualHash)
        } finally {
            tmp.delete()
        }
    }

    private fun small(zip: ZipInputStream): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            require(bytes.size() + n <= LIMIT) { "Manifest/README too large" }
            bytes.write(buf, 0, n)
        }
        return bytes.toByteArray()
    }
}
