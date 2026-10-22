package com.crownparkcomputing.retrodos.winkit

import android.content.Context
import android.net.Uri
import android.os.StatFs
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** RC24.6 v1 Mac export receiver. Only stages a private disk; never authorizes boot or LAN. */
internal object MacTransferImporter {
    data class Receipt(val bytes: Long, val sha256: String)
    private const val ENTRY = "disk/YARDOMOB.raw"
    private val hashPattern = Regex("[a-f0-9]{64}")

    fun importZip(context: Context, uri: Uri): Receipt {
        val store = GuestStore(context.filesDir)
        val destination = store.installedImage()
        require(!destination.exists()) { "Existing guest must not be overwritten" }
        val machines = destination.parentFile!!
        if (!machines.exists()) require(machines.mkdir()) { "Cannot create private machine folder" }
        require(machines.isDirectory && !java.nio.file.Files.isSymbolicLink(machines.toPath()))
        val gate = File(machines, "YARDOMOB.network-gate.json")
        require(!gate.exists()) { "Existing network gate; refusing import" }
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                val first = zip.nextEntry ?: error("Missing manifest")
                require(!first.isDirectory && first.name == "manifest.json")
                val metaBytes = limited(zip, 65536)
                zip.closeEntry()
                val manifest = JSONObject(String(metaBytes, Charsets.UTF_8))
                require(manifest.getString("schema") == "winkit-android-disk-transfer-v1")
                val target = manifest.getJSONObject("target")
                require(target.getString("guest_name") == "YARDOMOB")
                require(target.getBoolean("private_disk_required"))
                require(!target.getBoolean("ethernet_enabled"))
                require(target.getBoolean("requires_first_boot_identity_setup"))
                val disk = manifest.getJSONObject("disk")
                require(disk.getString("entry") == ENTRY && disk.getString("format") == "raw-sector-image")
                require(disk.getInt("sector_bytes") == 512)
                val bytes = disk.getLong("bytes")
                val hash = disk.getString("sha256")
                require(bytes >= 512 && bytes % 512L == 0L && bytes <= 128L * 1024 * 1024 * 1024)
                require(hashPattern.matches(hash))
                require(bytes <= (StatFs(machines.path).availableBytes - 64L * 1024 * 1024) / 2L) {
                    "Insufficient storage for private guest"
                }
                val readme = zip.nextEntry ?: error("Missing README")
                require(!readme.isDirectory && readme.name == "README.txt")
                limited(zip, 65536)
                zip.closeEntry()
                val entry = zip.nextEntry ?: error("Missing disk")
                require(!entry.isDirectory && entry.name == ENTRY)
                // Verify complete ZIP integrity (including CRC and extra-entry rejection)
                // before allowing GuestStore to publish the disk.
                val stage = File(machines, ".mac-transfer-${java.util.UUID.randomUUID()}.partial")
                require(stage.createNewFile()) { "Cannot stage transferred disk" }
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var copied = 0L
                    FileOutputStream(stage).use { out ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            if (n == 0) continue
                            copied = Math.addExact(copied, n.toLong())
                            require(copied <= bytes) { "Transfer exceeds declared size" }
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                        }
                        out.fd.sync()
                    }
                    zip.closeEntry() // checks ZIP CRC before disk publication
                    require(zip.nextEntry == null) { "Unexpected ZIP entries" }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                    require(copied == bytes && actualHash == hash) { "Transfer integrity mismatch" }
                    val gateText = JSONObject().put("ethernet_enabled", false)
                        .put("identity_verified", false).put("required_computer_name", "YARDOMOB")
                        .put("clone_sha256", hash).toString(2)
                    require(gate.createNewFile()) { "Concurrent import gate" }
                    FileOutputStream(gate).use { f ->
                        f.write(gateText.toByteArray(Charsets.UTF_8))
                        f.fd.sync()
                    }
                    val receipt = stage.inputStream().use { store.importVerified(it, hash, bytes) }
                    return Receipt(receipt.bytes, receipt.sha256)
                } finally {
                    stage.delete()
                }
            }
        } ?: error("Unable to read selected Mac transfer ZIP")
    }

    private fun limited(input: java.io.InputStream, cap: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) return bytes.toByteArray()
            if (n == 0) continue
            require(bytes.size() + n <= cap) { "Transfer metadata too large" }
            bytes.write(chunk, 0, n)
        }
    }
}
