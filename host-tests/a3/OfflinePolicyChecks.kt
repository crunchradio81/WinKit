package com.crownparkcomputing.retrodos.winkit
private var count = 0
private fun ok(label: String, got: Boolean, expected: Boolean = true) {
    require(got == expected) { "A3 policy $label: got $got, expected $expected" }
    count++
}
fun main() {
    val src = "/mac/stopped/YARDO-B.raw"
    val dst = "/data/user/0/org.winkit.android.a2/files/machines/YARDOMOB.raw"
    val conf = """[dosbox]
memsize=16
[ne2000]
ne2000=true
backend=ethnet
[ipx]
ipx=true
[autoexec]
@echo off
IMGMOUNT 2 "$src" -t hdd -fs none -size 512,63,16,1024
ETHNET CONNECT 127.0.0.1 213
BOOT C:
"""
    val converted = OfflineBootConfig.convert(conf, src, dst)
    ok("baseline converted", OfflineBootPolicy.allows(converted, dst))
    ok("valid option reordering", OfflineBootPolicy.allows(converted.replace("-t hdd -fs none -size 512,63,16,1024", "-size 512,63,16,1024 -t hdd -fs none"), dst))
    fun bad(label: String, data: String) = ok(label, OfflineBootPolicy.allows(data, dst), false)
    bad("unexpected other disk", converted.replace(dst, "/other/private/YARDOMOB.raw"))
    bad("disk path substring", converted.replace(dst, dst + ".old"))
    bad("duplicate network keys", converted.replace("ne2000=false", "ne2000=false\nne2000=true"))
    bad("duplicate IPX keys", converted.replace("ipx=false", "ipx=false\nipx=true"))
    bad("duplicate autoexec sections", converted + "[autoexec]\n")
    bad("extra network command", converted + "[autoexec]\nETHNET CONNECT 10.0.0.1\n")
    bad("post-boot command", converted.replace("BOOT C:", "BOOT C:\nMOUNT D /bad"))
    bad("pre-boot network", converted.replace("BOOT C:", "IPXNET CONNECT test\nBOOT C:"))
    bad("command chain", converted.replace("BOOT C:", "BOOT C: & ETHNET CONNECT 1.2.3.4"))
    bad("extra disk", converted.replace("BOOT C:", "IMGMOUNT D /tmp/add.iso -t iso\nBOOT C:"))
    bad("unquoted disk path", converted.replace("IMGMOUNT 2 \"$dst\"", "IMGMOUNT 2 $dst.tmp"))
    bad("broken CHS", converted.replace("512,63,16,1024", "512,63,16,0"))
    bad("unknown mount switch", converted.replace("-fs none", "-fs none -ide 1m"))
    bad("network in echo chain", converted.replace("@echo off", "@echo off & NET START"))
    bad("network in rem chain", converted.replace("@echo off", "rem ok | NET START"))
    bad("boot before disk", converted.replace("IMGMOUNT 2 \"$dst\" -t hdd -fs none -size 512,63,16,1024\nBOOT C:", "BOOT C:\nIMGMOUNT 2 \"$dst\" -t hdd -fs none -size 512,63,16,1024"))
    bad("no boot", converted.replace("BOOT C:", ""))
    bad("bad autoexec command", converted.replace("BOOT C:", "CALL /data/boot.bat\nBOOT C:"))
    ok("wrong expected image", OfflineBootPolicy.allows(converted, "/outside/machines/YARDOMOB.raw"), false)
    println("A3 Kotlin launch-time boot policy: $count assertions passed")
}
