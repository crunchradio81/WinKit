package com.crownparkcomputing.retrodos.winkit

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/** A5 read-only, fail-closed raw HDD geometry gate. Does NOT prove guest boot. */
object OfflineDiskGeometry {
    fun expectedBytes(profile: String, diskPath: String): Long? {
        if (!OfflineBootPolicy.allows(profile, diskPath)) return null
        var autoexec = false
        for (sourceLine in profile.lineSequence()) {
            val line = sourceLine.trim()
            if (line.startsWith("[")) { autoexec = line.equals("[autoexec]", true); continue }
            if (!autoexec || !line.startsWith("imgmount ", true)) continue
            val geometry = Regex("(?i)(?:^|\\s)-size\\s+([0-9]+,[0-9]+,[0-9]+,[0-9]+)(?:\\s|$)")
                .find(line)?.groupValues?.get(1) ?: return null
            val chs = geometry.split(',').map { it.toLongOrNull() ?: return null }
            if (chs.size != 4 || chs[0] != 512L || chs[1] !in 1L..63L ||
                chs[2] !in 1L..255L || chs[3] !in 1L..65535L) return null
            return try { chs.reduce { a, b -> Math.multiplyExact(a,b) } }
            catch (_: ArithmeticException) { null }
        }
        return null
    }
    fun matches(profile: String, image: File): Boolean {
        val expected = expectedBytes(profile, image.absolutePath) ?: return false
        if (!image.isFile || Files.isSymbolicLink(image.toPath()) || image.length() != expected) return false
        return try {
            RandomAccessFile(image, "r").use { disk ->
                if (disk.length() != expected) return false
                val first = ByteArray(8); disk.readFully(first)
                fun magic(vararg bytes: Int): Boolean = bytes.indices.all { (first[it].toInt() and 255) == bytes[it] }
                if (magic(0x51,0x46,0x49,0xfb) || magic(0x50,0x4b,0x03,0x04) ||
                    magic(0x1f,0x8b) || magic(0x37,0x7a,0xbc,0xaf,0x27,0x1c) ||
                    magic(0x4b,0x44,0x4d,0x56) || magic(0x76,0x68,0x64,0x78,0x66,0x69,0x6c,0x65) ||
                    magic(0x63,0x6f,0x6e,0x65,0x63,0x74,0x69,0x78)) return false
                disk.seek(expected - 512L)
                val tail = ByteArray(8); disk.readFully(tail)
                !tail.contentEquals("conectix".toByteArray(Charsets.US_ASCII))
            }
        } catch (_: Exception) { false }
    }
}
