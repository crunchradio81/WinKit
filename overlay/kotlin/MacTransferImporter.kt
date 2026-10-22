package com.crownparkcomputing.retrodos.winkit

import android.content.Context
import android.net.Uri
import android.os.StatFs
import org.json.JSONObject
import java.io.File

/**
 * Android SAF/JSON adapter for the JVM-testable disk publication transaction.
 * RC24.6 first boot remains offline; no existing machine is replaced.
 */
internal object MacTransferImporter {
    data class Receipt(val bytes: Long, val sha256: String)

    fun importZip(context: Context, uri: Uri): Receipt {
        val machines = File(context.filesDir, "machines")
        // This value is never trusted as proof the guest will boot; the installer
        // validates it against the manifest and reserves three disk copies.
        val freeBytes = StatFs(if (machines.isDirectory) machines.absolutePath else context.filesDir.absolutePath)
            .availableBytes
        val receipt = context.contentResolver.openInputStream(uri)?.use { stream ->
            MacTransferDiskInstaller.install(context.filesDir, stream, freeBytes) { manifestText ->
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
        return Receipt(receipt.bytes, receipt.sha256)
    }
}
