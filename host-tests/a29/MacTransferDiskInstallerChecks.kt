package com.crownparkcomputing.retrodos.winkit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Actual disk-publication transaction tests; synthetic disk only. */
fun main() {
    val diskBytes = ByteArray(512 * 128) { (it * 19 + it / 7).toByte() }
    val digest = MessageDigest.getInstance("SHA-256").digest(diskBytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val manifest = MacTransferArchiveVerifier.Manifest(
        schema = "winkit-android-disk-transfer-v1",
        guestName = "YARDOMOB",
        privateDiskRequired = true,
        ethernetEnabled = false,
        identitySetupRequired = true,
        diskEntry = "disk/YARDOMOB.raw",
        diskFormat = "raw-sector-image",
        sectorBytes = 512,
        diskBytes = diskBytes.size.toLong(),
        diskSha256 = digest
    )
    val json = """{"schema":"winkit-android-disk-transfer-v1","target":{"guest_name":"YARDOMOB","private_disk_required":true,"ethernet_enabled":false,"requires_first_boot_identity_setup":true},"disk":{"entry":"disk/YARDOMOB.raw","format":"raw-sector-image","sector_bytes":512,"bytes":${diskBytes.size},"sha256":"$digest"}}"""
    val zipBytes = ByteArrayOutputStream().also { stream ->
        ZipOutputStream(stream).use { zip ->
            for ((name, payload) in listOf(
                "manifest.json" to json.toByteArray(),
                "README.txt" to "Synthetic test. Offline only.".toByteArray(),
                "disk/YARDOMOB.raw" to diskBytes
            )) {
                zip.putNextEntry(ZipEntry(name).also { entry ->
                    entry.method = ZipEntry.STORED
                    entry.size = payload.size.toLong()
                    entry.compressedSize = payload.size.toLong()
                    entry.crc = CRC32().also { it.update(payload) }.value
                })
                zip.write(payload)
                zip.closeEntry()
            }
        }
    }.toByteArray()
    val free = 70L * 1024 * 1024 // enough for three copies of the synthetic disk

    fun fresh(): File = Files.createTempDirectory("winkit-a29-transaction-").toFile()
    fun drop(root: File) {
        root.walkBottomUp().forEach { file ->
            if (Files.isSymbolicLink(file.toPath())) Files.deleteIfExists(file.toPath())
            else file.delete()
        }
    }
    fun withRoot(action: (File) -> Unit) {
        val root = fresh()
        try { action(root) } finally { drop(root) }
    }
    fun machine(root: File) = File(root, "machines")
    fun installed(root: File) = File(machine(root), "YARDOMOB.raw")
    fun deny(root: File) = File(machine(root), "YARDOMOB.network-gate.json")
    fun config(root: File) = File(machine(root), "YARDOMOB.offline.conf")
    fun ready(root: File) = File(machine(root), "OFFLINE_READY")
    fun checkNoScratch(root: File) {
        check(machine(root).listFiles().orEmpty().none {
            it.name.startsWith(".mac-transfer-") || it.name.startsWith(".import-")
        }) { "Scratch file retained" }
    }
    fun install(
        root: File,
        input: java.io.InputStream = ByteArrayInputStream(zipBytes),
        afterGate: () -> Unit = {}
    ) = MacTransferDiskInstaller.install(root, input, free, parseManifest = { raw ->
        check(raw.contains("winkit-android-disk-transfer-v1"))
        manifest
    }, afterDenyGate = afterGate)
    var verified = 0
    fun expectFailure(name: String, action: () -> Unit) {
        val result = runCatching(action)
        check(result.isFailure) { "Unsafe transfer was accepted: $name" }
        verified++
    }
    withRoot { root ->
        val receipt = install(root)
        check(receipt.bytes == diskBytes.size.toLong() && receipt.sha256 == digest)
        check(installed(root).readBytes().contentEquals(diskBytes))
        val gateBefore = deny(root).readText()
        check(gateBefore.contains("\"ethernet_enabled\":false"))
        check(gateBefore.contains("\"identity_verified\":false"))
        check(gateBefore.contains(digest))
        check(!config(root).exists() && !ready(root).exists())
        checkNoScratch(root)
        expectFailure("double import cannot overwrite guest") { install(root) }
        check(installed(root).readBytes().contentEquals(diskBytes))
        check(deny(root).readText() == gateBefore)
        checkNoScratch(root)
    }
    withRoot { root ->
        machine(root).mkdir()
        installed(root).writeBytes(ByteArray(512) { 0x35 })
        val before = installed(root).readBytes()
        expectFailure("pre-existing guest disk") { install(root) }
        check(before.contentEquals(installed(root).readBytes()))
        check(!deny(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        machine(root).mkdir()
        deny(root).writeText("existing DENY")
        expectFailure("pre-existing security gate") { install(root) }
        check(deny(root).readText() == "existing DENY")
        check(!installed(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        machine(root).mkdir()
        config(root).writeText("OLD CONF")
        expectFailure("pre-existing boot config") { install(root) }
        check(config(root).readText() == "OLD CONF" && !installed(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        machine(root).mkdir()
        ready(root).writeText("a2-offline\n")
        expectFailure("pre-existing ready marker") { install(root) }
        check(ready(root).readText() == "a2-offline\n" && !installed(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        expectFailure("mid-upload source failure") {
            val broken = object : java.io.FilterInputStream(ByteArrayInputStream(zipBytes)) {
                private var count = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (count >= zipBytes.size / 3) throw IOException("simulated interrupted upload")
                    val n = super.read(b, off, minOf(len, 1024))
                    if (n > 0) count += n
                    return n
                }
            }
            install(root, broken)
        }
        check(!installed(root).exists() && !deny(root).exists() && !ready(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        expectFailure("manifest digest mismatch") {
            MacTransferDiskInstaller.install(
                root, ByteArrayInputStream(zipBytes), free,
                parseManifest = { manifest.copy(diskSha256 = "0".repeat(64)) }
            )
        }
        check(!installed(root).exists() && !deny(root).exists() && !ready(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        expectFailure("simulated failure after durable network deny marker") {
            install(root, afterGate = { throw IOException("simulated interruption after deny marker") })
        }
        val preserved = deny(root).readText()
        check(preserved.contains(digest))
        check(!installed(root).exists() && !ready(root).exists())
        checkNoScratch(root)
        expectFailure("interrupted machine cannot silently retry") { install(root) }
        check(deny(root).readText() == preserved)
    }
    withRoot { root ->
        expectFailure("destination races with publication") {
            install(root, afterGate = {
                installed(root).writeBytes(ByteArray(512) { 0x77 })
            })
        }
        check(deny(root).exists())
        check(installed(root).length() == 512L)
        check(installed(root).readBytes().all { it == 0x77.toByte() })
        check(!ready(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        machine(root).mkdir()
        val external = File(root, "external-file").also { it.writeText("leave me alone") }
        Files.createSymbolicLink(installed(root).toPath(), external.toPath())
        expectFailure("disk symlink") { install(root) }
        check(external.readText() == "leave me alone")
        check(!deny(root).exists())
        checkNoScratch(root)
    }
    withRoot { root ->
        val external = File(root, "external-dir").also { it.mkdir() }
        Files.createSymbolicLink(machine(root).toPath(), external.toPath())
        expectFailure("machine directory symlink") { install(root) }
        check(external.listFiles().isNullOrEmpty())
    }
    println("PASS A29 Mac transfer storage: successful private import, $verified guarded failure scenarios; no existing guest, config or ready marker overwritten")
}
