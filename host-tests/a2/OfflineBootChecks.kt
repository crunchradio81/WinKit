package com.crownparkcomputing.retrodos.winkit

private var passed = 0
private fun checkCase(name: String, condition: Boolean) {
    require(condition) { "FAILED $name" }
    passed++
}
private fun rejected(name: String, input: String, src: String = "/mac/stopped/YARDO-B-WORKING.raw") {
    val dest = "/data/user/0/org.winkit.android.a2/files/machines/YARDOMOB.raw"
    val gotError = try { OfflineBootConfig.convert(input, src, dest); false }
    catch (_: OfflineBootConfig.UnsafeConfig) { true }
    checkCase(name, gotError)
}
fun main() {
    val src = "/mac/stopped/YARDO-B-WORKING.raw"
    val dst = "/data/user/0/org.winkit.android.a2/files/machines/YARDOMOB.raw"
    val base = """
[dosbox]
working directory option=default
[machine]
memsize=16
[sblaster]
sbtype=sb16
[ne2000]
ne2000=true
nicbase=0x300
backend=ethnet
[ipx]
ipx=true
[autoexec]
@echo off
IMGMOUNT 2 "$src" -t hdd -fs none -size 512,63,16,1024
ETHNET CONNECT 100.90.1.1 213
BOOT C:
""".trimIndent() + "\n"
    val output = OfflineBootConfig.convert(base, src, dst)
    checkCase("rewrites exact private image path", output.contains("IMGMOUNT 2 \"$dst\" -t hdd -fs none -size 512,63,16,1024"))
    checkCase("preserves real guest geometry", output.contains("-size 512,63,16,1024"))
    checkCase("preserves audio", output.contains("sbtype=sb16"))
    checkCase("explicitly disables NE2000", output.contains("ne2000=false") && !output.contains("ne2000=true"))
    checkCase("explicitly disables IPX", output.contains("ipx=false") && !output.contains("ipx=true"))
    checkCase("removes host ETHNET command", !output.contains("ETHNET CONNECT"))
    checkCase("preserves BOOT C", output.contains("BOOT C:"))
    checkCase("removes hostname path", !output.contains(src))
    checkCase("single network override", Regex("ne2000=false").findAll(output).count() == 1)
    checkCase("single ipx override", Regex("ipx=false").findAll(output).count() == 1)
    checkCase("explicit private marker", output.startsWith("; WinKit A2: OFFLINE."))
    rejected("reject second mount", base.replace("BOOT C:", "IMGMOUNT D /mac/cd.iso -t iso\nBOOT C:"))
    rejected("reject second HDD", base.replace("BOOT C:", "IMGMOUNT 2 \"$src\" -t hdd -fs none\nBOOT C:"))
    rejected("reject extra boot", base.replace("BOOT C:", "BOOT A:\nBOOT C:"))
    rejected("reject direct external script", base.replace("BOOT C:", "CALL SETUP.BAT\nBOOT C:"))
    rejected("reject missing source image", base.replace(src, "/mac/stopped/other.raw"))
    rejected("reject duplicate source references", base.replace("BOOT C:", "REM $src\nBOOT C:"))
    rejected("reject missing autoexec", base.substringBefore("[autoexec]"))
    rejected("reject duplicate autoexec", base + "\n[autoexec]\nBOOT C:\n")
    rejected("reject unquoted extra command", base.replace("BOOT C:", "MOUNT C /mac/folder\nBOOT C:"))
    rejected("reject missing boot", base.replace("BOOT C:\n", ""))
    rejected("reject network via IPXNET", base.replace("BOOT C:", "IPXNET CONNECT 10.0.0.1\nBOOT C:").replace("IPXNET CONNECT 10.0.0.1\n", "NET.EXE START\n"))
    val noIp = base.replace("[ipx]\nipx=true\n", "")
    val noNet = noIp.replace("[ne2000]\nne2000=true\nnicbase=0x300\nbackend=ethnet\n", "")
    val fallback = OfflineBootConfig.convert(noNet, src, dst)
    checkCase("add explicit IPX disabled section", fallback.contains("[ipx]\nipx=false"))
    checkCase("add explicit NE2000 disabled section", fallback.contains("[ne2000]\nne2000=false"))
    println("A2 Kotlin offline boot assertions: $passed passed")
}
