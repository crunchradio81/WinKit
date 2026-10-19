import com.crownparkcomputing.retrodos.winkit.BootSectorEvidence
import java.io.ByteArrayInputStream

fun main(args: Array<String>) {
    var count = 0
    fun check(ok: Boolean) { if (!ok) error("A4 sector evidence assertion ${++count} failed") else count++ }
    val fixture = java.io.File(args[0]).inputStream().use { BootSectorEvidence.inspect(it, java.io.File(args[0]).length()) }
    check(fixture.signature)
    check(fixture.kind == "A4_BIOS_ONLY_FIXTURE")
    check(fixture.isTestFixture)
    check(fixture.partitions.isEmpty())
    check(fixture.errors.isEmpty())
    check(!fixture.isNetworkSafe && !fixture.isGuestOsVerified)
    val bytes = ByteArray(512)
    bytes[510] = 0x55; bytes[511] = 0xaa.toByte()
    fun probe() = BootSectorEvidence.inspect(ByteArrayInputStream(bytes), 512L * 100L)
    check(probe().kind == "SIGNATURE_WITHOUT_PARTITIONS")
    fun entry(slot: Int, flag: Int, type: Int, start: Int, length: Int) {
        val off=446+slot*16
        bytes[off] = flag.toByte(); bytes[off+4] = type.toByte()
        for (j in 0..3) {
            bytes[off+8+j] = (start ushr (j*8)).toByte()
            bytes[off+12+j] = (length ushr (j*8)).toByte()
        }
    }
    entry(0, 128, 6, 1, 70)
    check(probe().kind == "MBR_PARTITIONED")
    check(probe().partitions.size == 1)
    check(probe().errors.isEmpty())
    entry(1, 0, 1, 65, 30)
    check(probe().errors.any { "overlap" in it })
    entry(1, 0, 1, 71, 30)
    check(probe().errors.any { "LBA" in it })
    entry(1, 0, 1, 71, 29)
    check(probe().errors.isEmpty())
    entry(1, 127, 1, 71, 29)
    check(probe().errors.any { "boot flag" in it })
    bytes[510] = 0
    check(probe().kind == "NO_55AA_SIGNATURE")
    check(probe().partitions.isEmpty())
    try { BootSectorEvidence.inspect(ByteArrayInputStream(ByteArray(8)), 513); error("bad size allowed") }
    catch (e: IllegalArgumentException) { count++ }
    try { BootSectorEvidence.inspect(ByteArrayInputStream(ByteArray(8)), 512); error("truncated allowed") }
    catch (e: IllegalStateException) { count++ }
    println("A4 Kotlin first-sector evidence: $count assertions passed")
}
