package com.crownparkcomputing.retrodos.winkit

import java.io.InputStream

/**
 * A4 read-only first-sector evidence. Does NOT assert the Windows guest boots,
 * owns a unique Windows network identity, or that an MBR is safe to execute.
 * The synthetic BIOS fixture is deliberately NOT an installable DOS disk.
 */
object BootSectorEvidence {
    const val BANNER = "WINKIT A4 BIOS HDD BOOT OK"
    data class Partition(val slot: Int, val bootFlag: Int, val type: Int,
                         val lbaStart: Long, val sectors: Long)
    data class Report(val kind: String, val signature: Boolean,
                      val partitions: List<Partition>, val errors: List<String>,
                      val isTestFixture: Boolean) {
        val isNetworkSafe: Boolean get() = false
        val isGuestOsVerified: Boolean get() = false
    }

    /** Caller passes exact, previously verified image length. Reads at most 512 bytes. */
    fun inspect(stream: InputStream, imageSize: Long): Report {
        require(imageSize >= 512 && imageSize % 512L == 0L) { "Invalid disk byte length" }
        val mbr = ByteArray(512)
        var have = 0
        while (have < mbr.size) {
            val n = stream.read(mbr, have, mbr.size - have)
            if (n < 0) error("Disk truncated before first sector ended")
            if (n == 0) { // InputStream implementations may rarely return zero; bounded fallback.
                val one = stream.read()
                if (one < 0) error("Disk truncated")
                mbr[have++] = one.toByte()
            } else have += n
        }
        val signed = (mbr[510].toInt() and 0xff) == 0x55 && (mbr[511].toInt() and 0xff) == 0xaa
        fun byte(i: Int) = mbr[i].toInt() and 0xff
        fun le32(i: Int): Long = (0..3).fold(0L) { v, n -> v or (byte(i+n).toLong() shl (n*8)) }
        val entries = mutableListOf<Partition>()
        val errors = mutableListOf<String>()
        val ranges = mutableListOf<Pair<LongRange, Int>>()
        if (signed) for (slot in 0 until 4) {
            val start = 446 + slot*16
            if ((start until start+16).all { byte(it) == 0 }) continue
            val flag = byte(start)
            val type = byte(start+4)
            val lba = le32(start+8)
            val count = le32(start+12)
            entries.add(Partition(slot+1, flag, type, lba, count))
            if (flag != 0 && flag != 0x80) errors.add("partition ${slot+1}: unsupported boot flag")
            if (type == 0 || lba < 1 || count == 0L || lba+count > imageSize/512L)
                errors.add("partition ${slot+1}: invalid type or LBA bounds")
            else ranges.add((lba until lba+count) to (slot+1))
        }
        val ordered = ranges.sortedBy { it.first.first }
        for (i in 1 until ordered.size) {
            if (ordered[i-1].first.last >= ordered[i].first.first)
                errors.add("partition ${ordered[i-1].second} overlaps partition ${ordered[i].second}")
        }
        val marker = BANNER.toByteArray(Charsets.US_ASCII)
        val fixture = signed && (0..mbr.size-marker.size).any { k ->
            marker.indices.all { mbr[k+it] == marker[it] }
        }
        val kind = when {
            fixture -> "A4_BIOS_ONLY_FIXTURE"
            !signed -> "NO_55AA_SIGNATURE"
            entries.isNotEmpty() -> "MBR_PARTITIONED"
            else -> "SIGNATURE_WITHOUT_PARTITIONS"
        }
        return Report(kind, signed, entries, errors, fixture)
    }
}
