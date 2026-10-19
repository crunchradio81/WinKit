#include "winkit_offline_gate.h"
#include <cassert>
#include <iostream>
int main() {
    std::string disk = "/data/user/0/org.winkit.android.a2/files/machines/YARDOMOB.raw";
    std::string mount = "IMGMOUNT 2 \"" + disk + "\" -t hdd -fs none -size 512,63,16,1024";
    std::string good = "[dosbox]\nmemsize=16\n[ne2000]\nne2000=false\nbackend=ethnet\n[ipx]\nipx=false\n[autoexec]\n@echo off\n" + mount + "\nBOOT C:\n";
    int passed = 0;
    auto valid = [&](const std::string &s) {assert(winkit_offline::boot_profile_is_offline(s, disk)); passed++;};
    auto bad = [&](const std::string &s) {assert(!winkit_offline::boot_profile_is_offline(s, disk)); passed++;};
    valid(good);
    valid(good.substr(0, good.find("-t hdd -fs none -size")) + "-size 512,63,16,1024 -t hdd -fs none\nBOOT C:\n");
    bad(good + "MOUNT D /tmp/disk");
    bad(good + "[autoexec]\nBOOT C:\n");
    bad(good + "\nETHNET CONNECT 1.2.3.4");
    bad(good.replace(good.find("ne2000=false"), 12, "ne2000=false\nne2000=true"));
    std::string fixed = "[dosbox]\nmemsize=16\n[ne2000]\nne2000=false\nbackend=ethnet\n[ipx]\nipx=false\n[autoexec]\n@echo off\n" + mount + "\nBOOT C:\n";
    bad(fixed.substr(0, fixed.find("BOOT C:")) + "NET START\nBOOT C:\n");
    bad(fixed.substr(0, fixed.find("BOOT C:")) + "BOOT C: & ETHNET CONNECT 1.2.3.4\n");
    bad(fixed.substr(0, fixed.find(mount)) + "IMGMOUNT 2 \"" + disk + ".old\" -t hdd -fs none -size 512,63,16,1024\nBOOT C:\n");
    bad(fixed.substr(0, fixed.find("BOOT C:")) + "IMGMOUNT D /tmp/cd.iso -t iso\nBOOT C:\n");
    bad(fixed.substr(0, fixed.find("BOOT C:")) + "BOOT C:\nCALL AUTOEXEC.BAT\n");
    bad(fixed.substr(0, fixed.find(mount)) + "@echo off & NET START\n" + mount + "\nBOOT C:\n");
    bad(fixed.substr(0, fixed.find(mount)) + "IMGMOUNT 2 \"" + disk + "\" -t hdd -fs none -size 512,63,16,0\nBOOT C:\n");
    std::cout << "A3 native boot profile: " << passed << " assertions passed\n";
}
