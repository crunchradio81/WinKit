package com.crownparkcomputing.retrodos.winkit

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Base64
import java.util.UUID

/** A5 self-contained GPL BIOS smoke disk. This is NOT MS-DOS or Windows. */
object OfflineSmokeFixture {
    private const val GEOMETRY = "512,63,16,8"
    const val IMAGE_BYTES = 512L * 63L * 16L * 8L
    // Assembled from fixtures/winkit_bios_boot.S with GNU as --32 and ld -m elf_i386.
    // Source-controlled data, not executable Android code or proprietary ROM/media.
    private const val SECTOR_B64 = "+jHAjtiO0LwAfPu+H3yshMB0CbQOuwcAzRDr8vTr/VdJTktJVCBBNCBCSU9TIEhERCBCT09UIE9LIC0gTk8gTUlDUk9TT0ZUIEZJTEVTAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAVao="

    /** Only installs into a previously unused app-private machine directory. */
    fun install(privateRoot: File): File {
        require(privateRoot.isDirectory && !Files.isSymbolicLink(privateRoot.toPath()))
        val machines = File(privateRoot, "machines")
        if (!machines.exists()) require(machines.mkdir()) { "Cannot create machines directory" }
        require(machines.isDirectory && !Files.isSymbolicLink(machines.toPath()))
        val image = File(machines,"YARDOMOB.raw")
        val config = File(machines,"YARDOMOB.offline.conf")
        val marker = File(machines,"OFFLINE_READY")
        require(listOf(image,config,marker).none { it.exists() || Files.isSymbolicLink(it.toPath()) }) {
            "A machine already exists; synthetic test must never overwrite it"
        }
        val sector = Base64.getDecoder().decode(SECTOR_B64)
        require(sector.size == 512 && sector[510] == 0x55.toByte() && sector[511] == 0xaa.toByte() &&
                String(sector, Charsets.US_ASCII).contains("WINKIT A4 BIOS HDD BOOT OK")) {
            "Invalid embedded GPL test sector"
        }
        val profile = "[dosbox]\nmemsize=16\nworking directory option=noprompt\n"+
                "[ne2000]\nne2000=false\n[ipx]\nipx=false\n"+
                "[autoexec]\n@echo off\n"+
                "IMGMOUNT 2 \"${image.absolutePath}\" -t hdd -fs none -size $GEOMETRY\nBOOT C:\n"
        require(OfflineBootPolicy.allows(profile,image.absolutePath))
        val stage = File(machines,".winkit-a5-${UUID.randomUUID()}.partial")
        require(stage.createNewFile())
        try {
            RandomAccessFile(stage,"rw").use { f ->
                f.setLength(IMAGE_BYTES)
                f.seek(0)
                f.write(sector)
                f.fd.sync()
            }
            NoReplacePublisher.publish(stage, image) // exclusive, synced publication without hard links
            require(OfflineDiskGeometry.matches(profile, image))
            // No marker exists until both image and offline config are safely present.
            val stagedConf=File(machines,".winkit-a5-config-${UUID.randomUUID()}.partial")
            require(stagedConf.createNewFile())
            try {
                FileOutputStream(stagedConf).use { f ->
                    f.write(profile.toByteArray(Charsets.UTF_8)); f.fd.sync()
                }
                NoReplacePublisher.publish(stagedConf, config)
            } finally { if (stagedConf.exists()) stagedConf.delete() }
            val stagedMarker=File(machines,".winkit-a5-marker-${UUID.randomUUID()}.partial")
            require(stagedMarker.createNewFile())
            try {
                FileOutputStream(stagedMarker).use { f ->
                    f.write("a2-offline\n".toByteArray(Charsets.US_ASCII)); f.fd.sync()
                }
                NoReplacePublisher.publish(stagedMarker, marker)
            } finally { if (stagedMarker.exists()) stagedMarker.delete() }
            return image
        } finally { if (stage.exists()) stage.delete() }
    }

}
