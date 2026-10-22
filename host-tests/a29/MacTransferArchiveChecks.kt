package com.crownparkcomputing.retrodos.winkit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

/** Pure JVM A29 Mac transfer test: no Android emulator/media/device needed. */
fun main() {
    val disk = ByteArray(512 * 16) { (it % 197).toByte() }
    val sha = MessageDigest.getInstance("SHA-256").digest(disk)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val fixtureManifest = MacTransferArchiveVerifier.Manifest(
        schema="winkit-android-disk-transfer-v1", guestName="YARDOMOB",
        privateDiskRequired=true, ethernetEnabled=false, identitySetupRequired=true,
        diskEntry="disk/YARDOMOB.raw", diskFormat="raw-sector-image", sectorBytes=512,
        diskBytes=disk.size.toLong(), diskSha256=sha)
    val json = """{"schema":"winkit-android-disk-transfer-v1","target":{"guest_name":"YARDOMOB","ethernet_enabled":false},"disk":{"bytes":${disk.size},"sha256":"$sha"}}"""
    fun pack(raw: ByteArray=disk, manifest: String=json, names: List<String> = listOf("manifest.json","README.txt","disk/YARDOMOB.raw")): ByteArray {
        val data=ByteArrayOutputStream()
        ZipOutputStream(data).use { zip ->
            for (name in names) {
                val payload = when(name){"manifest.json"->manifest.toByteArray();"README.txt"->"Private Mac guest".toByteArray();else->raw}
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    crc = CRC32().apply { update(payload) }.value
                }
                zip.putNextEntry(entry)
                zip.write(payload)
                zip.closeEntry()
            }
        }
        return data.toByteArray()
    }
    fun verify(bytes:ByteArray, meta:MacTransferArchiveVerifier.Manifest=fixtureManifest, free:Long=100L*1024*1024):MacTransferArchiveVerifier.Receipt {
        val temp=File.createTempFile("winkit-a29-", ".partial")
        try {
            return ByteArrayInputStream(bytes).use { input ->
                MacTransferArchiveVerifier.stage(input,temp,free) { text ->
                    check(text.startsWith("{"))
                    meta
                }
            }
        } finally { temp.delete() }
    }
    fun rejects(title:String, action:()->Unit){
        var thrown=false
        try{action()} catch (_:Exception){thrown=true}
        check(thrown) { "Accepted invalid archive: $title" }
    }
    check(verify(pack()).bytes==disk.size.toLong())
    rejects("altered disk") { verify(pack(raw=disk.clone().apply{this[13]=(this[13]+1).toByte()})) }
    rejects("extra entry") { verify(pack(names=listOf("manifest.json","README.txt","disk/YARDOMOB.raw","extra"))) }
    rejects("unexpected disk-shaped entry") { verify(pack(names=listOf("manifest.json","README.txt","disk/YARDOMOB.raw","disk/YARDOMOB.rAw"))) }
    rejects("wrong order") { verify(pack(names=listOf("README.txt","manifest.json","disk/YARDOMOB.raw"))) }
    rejects("missing README") { verify(pack(names=listOf("manifest.json","disk/YARDOMOB.raw")) ) }
    rejects("unsafe LAN target") { verify(pack(),fixtureManifest.copy(ethernetEnabled=true)) }
    rejects("wrong machine") { verify(pack(),fixtureManifest.copy(guestName="YARDO")) }
    rejects("identity setup skipped") { verify(pack(),fixtureManifest.copy(identitySetupRequired=false)) }
    rejects("invalid format") { verify(pack(),fixtureManifest.copy(diskFormat="qcow2")) }
    rejects("geometry mismatch") { verify(pack(),fixtureManifest.copy(sectorBytes=1024)) }
    rejects("wrong declared size") { verify(pack(),fixtureManifest.copy(diskBytes=disk.size.toLong()+512)) }
    rejects("wrong digest") { verify(pack(),fixtureManifest.copy(diskSha256="0".repeat(64))) }
    rejects("insufficient free space") { verify(pack(),free=64L*1024*1024 + 2L*disk.size) }
    rejects("oversized manifest") { verify(pack(manifest="X".repeat(65537))) }
    val corrupted=pack().clone().apply {
        // Corrupt uncompressed ZIP data without updating stored CRC; find the disk's known first bytes.
        val start=indexOfSubArray(this,disk.copyOfRange(0,32))
        check(start>=0)
        this[start]=(this[start].toInt() xor 0x33).toByte()
    }
    rejects("bad ZIP CRC") { verify(corrupted) }
    println("PASS A29 pure Kotlin Mac transfer: 1 valid, 15 rejection scenarios; offline, no device/emulator required")
}
private fun indexOfSubArray(haystack:ByteArray, needle:ByteArray):Int {
    for (i in 0..haystack.size-needle.size) if(needle.indices.all{haystack[i+it]==needle[it]})return i
    return -1
}
