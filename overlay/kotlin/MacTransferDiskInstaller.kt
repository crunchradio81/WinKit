package com.crownparkcomputing.retrodos.winkit

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files

/**
 * Android-independent storage transaction for an RC24.6 Mac transfer.
 * No guest config, ready marker, network connection or emulator is created.
 *
 * Failure before the deny gate: no published disk. Failure after the deny gate:
 * leave the deny gate and any reserved partial destination in place. Never erase
 * or overwrite an existing guest, including interrupted or stale configurations.
 */
internal object MacTransferDiskInstaller {
    data class Receipt(val bytes: Long, val sha256: String)
    private const val DISK = "YARDOMOB.raw"

    @Synchronized
    fun install(
        privateFiles: File,
        source: InputStream,
        freeBytes: Long,
        parseManifest: (String) -> MacTransferArchiveVerifier.Manifest,
        // Test-only failure seam; normal Android callers leave this at its default.
        afterDenyGate: () -> Unit = {}
    ): Receipt {
        require(privateFiles.isDirectory && !Files.isSymbolicLink(privateFiles.toPath())) {
            "Invalid private storage root"
        }
        val machineDir = File(privateFiles, "machines")
        if (!machineDir.exists()) require(machineDir.mkdir()) { "Cannot create machine storage" }
        require(machineDir.isDirectory && !Files.isSymbolicLink(machineDir.toPath())) {
            "Invalid machine storage"
        }
        val disk = File(machineDir, DISK)
        val gate = File(machineDir, "YARDOMOB.network-gate.json")
        val config = File(machineDir, "YARDOMOB.offline.conf")
        val ready = File(machineDir, "OFFLINE_READY")

        for (existing in listOf(disk, gate, config, ready)) {
            require(!existing.exists() && !Files.isSymbolicLink(existing.toPath())) {
                "Existing guest state at ${existing.name}; refusing overwrite"
            }
        }

        val stage = File(machineDir, ".mac-transfer-${java.util.UUID.randomUUID()}.partial")
        require(stage.createNewFile()) { "Cannot reserve private transfer stage" }
        try {
            val receipt = MacTransferArchiveVerifier.stage(source, stage, freeBytes, parseManifest)
            // The disk cannot be published before an independently durable deny
            // gate is present. A failure afterwards never deletes that gate.
            require(gate.createNewFile()) { "Concurrent guest transfer or deny gate exists" }
            FileOutputStream(gate, false).use { output ->
                val gateBody = """
                    {"ethernet_enabled":false,"identity_verified":false,
                     "required_computer_name":"YARDOMOB","clone_sha256":"${receipt.sha256}"}
                """.trimIndent()
                output.write(gateBody.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            afterDenyGate()
            val published = stage.inputStream().use {
                GuestStore(privateFiles).importVerified(it, receipt.sha256, receipt.bytes)
            }
            check(published.image == disk && published.sha256 == receipt.sha256 && published.bytes == receipt.bytes) {
                "Disk publication receipt mismatch"
            }
            return Receipt(published.bytes, published.sha256)
        } finally {
            // Only temporary scratch data may be cleared automatically.
            // Preserve the deny gate, any partial published image, config, and
            // ready state for explicit/manual diagnostic recovery.
            stage.delete()
        }
    }
}
