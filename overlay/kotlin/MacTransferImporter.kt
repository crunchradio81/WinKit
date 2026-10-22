package com.crownparkcomputing.retrodos.winkit

import android.content.Context
import android.net.Uri
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * RC24.6 Android integration. The ZIP decoder is tested on a plain JVM.
 * This Android adapter creates the private deny gate before publishing any guest.
 * It does not generate an offline config, identity, ready marker or LAN access.
 */
internal object MacTransferImporter {
    data class Receipt(val bytes: Long, val sha256: String)

    fun importZip(context: Context, uri: Uri): Receipt {
        val root = context.filesDir
        require(root.isDirectory && !java.nio.file.Files.isSymbolicLink(root.toPath())) {
            "Invalid private storage root"
        }
        val store = GuestStore(root)
        val destination = store.installedImage()
        require(!destination.exists() && !java.nio.file.Files.isSymbolicLink(destination.toPath())) {
            "Existing guest must not be overwritten"
        }
        val machines = destination.parentFile ?: error("Guest directory unavailable")
        if (!machines.exists()) require(machines.mkdir()) { "Cannot create machine storage" }
        require(machines.isDirectory && !java.nio.file.Files.isSymbolicLink(machines.toPath())) {
            "Invalid machine storage"
        }
        val gate = File(machines, "YARDOMOB.network-gate.json")
        require(!gate.exists() && !java.nio.file.Files.isSymbolicLink(gate.toPath())) {
            "Existing network gate; refusing import"
        }
        val stage = File(machines, ".mac-transfer-${java.util.UUID.randomUUID()}.partial")
        require(stage.createNewFile()) { "Cannot reserve private staging file" }
        try {
            val verified = context.contentResolver.openInputStream(uri)?.use { input ->
                MacTransferArchiveVerifier.stage(
                    input, stage, StatFs(machines.absolutePath).availableBytes
                ) { manifestText ->
                    val meta = JSONObject(manifestText)
                    val target = meta.getJSONObject("target")
                    val disk = meta.getJSONObject("disk")
                    MacTransferArchiveVerifier.Manifest(
                        schema = meta.getString("schema"),
                        guestName = target.getString("guest_name"),
                        privateDiskRequired = target.getBoolean("private_disk_required"),
                        ethernetEnabled = target.getBoolean("ethernet_enabled"),
                        identitySetupRequired = target.getBoolean("requires_first_boot_identity_setup"),
                        diskEntry = disk.getString("entry"),
                        diskFormat = disk.getString("format"),
                        sectorBytes = disk.getInt("sector_bytes"),
                        diskBytes = disk.getLong("bytes"),
                        diskSha256 = disk.getString("sha256")
                    )
                }
            } ?: error("Unable to read selected Mac transfer ZIP")

            // Persistent network deny marker is reserved BEFORE the disk is published.
            // If publication fails, leave the marker intact; don't silently unlock.
            require(gate.createNewFile()) { "Concurrent import or security gate exists" }
            FileOutputStream(gate, false).use { out ->
                val gateText = JSONObject()
                    .put("ethernet_enabled", false)
                    .put("identity_verified", false)
                    .put("required_computer_name", "YARDOMOB")
                    .put("clone_sha256", verified.sha256)
                    .toString(2)
                out.write(gateText.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            val receipt = stage.inputStream().use { stream ->
                store.importVerified(stream, verified.sha256, verified.bytes)
            }
            return Receipt(receipt.bytes, receipt.sha256)
        } finally {
            stage.delete() // Only disposable scratch file. Never erase a guest or network gate.
        }
    }
}
