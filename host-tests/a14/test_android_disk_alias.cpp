// A14: offline native Android path-alias regression; no Microsoft media.
// Tests the same strict boot policy used during SDL frontend machine discovery.
#include "winkit_offline_gate.h"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <fcntl.h>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

static void mkdir_ok(const std::string &p) { assert(::mkdir(p.c_str(), 0700) == 0); }
static void touch(const std::string &p) {
    int fd = ::open(p.c_str(), O_CREAT | O_EXCL | O_WRONLY, 0600);
    assert(fd >= 0);
    assert(::write(fd, "BIOS", 4) == 4);
    assert(::close(fd) == 0);
}
static std::string profile(const std::string &p) {
    return "[dosbox]\nmemsize=16\n[ne2000]\nne2000=false\n[ipx]\nipx=false\n"
           "[autoexec]\n@echo off\nIMGMOUNT 2 \"" + p +
           "\" -t hdd -fs none -size 512,63,16,8\nBOOT C:\n";
}
int main() {
    char temp[] = "/tmp/winkit-a14-XXXXXX";
    const char *created = ::mkdtemp(temp);
    assert(created);
    const std::string root(created);
    mkdir_ok(root + "/user");
    mkdir_ok(root + "/user/0");
    mkdir_ok(root + "/user/0/org.winkit.android.a2");
    mkdir_ok(root + "/user/0/org.winkit.android.a2/files");
    mkdir_ok(root + "/user/0/org.winkit.android.a2/files/machines");
    const std::string actual = root + "/user/0/org.winkit.android.a2/files/machines/YARDOMOB.raw";
    touch(actual);
    assert(::symlink((root + "/user/0").c_str(), (root + "/data").c_str()) == 0);
    const std::string native = root + "/data/org.winkit.android.a2/files/machines/YARDOMOB.raw";
    assert(winkit_offline::boot_profile_is_offline(profile(actual), native));
    assert(winkit_offline::boot_profile_is_offline(profile(native), actual));
    assert(winkit_offline::boot_profile_is_offline(profile(actual), actual));
    mkdir_ok(root + "/different");
    mkdir_ok(root + "/different/machines");
    const std::string unrelated = root + "/different/machines/YARDOMOB.raw";
    touch(unrelated);
    assert(!winkit_offline::boot_profile_is_offline(profile(unrelated), native));
    assert(!winkit_offline::boot_profile_is_offline(
        profile(root + "/user/0/../0/org.winkit.android.a2/files/machines/YARDOMOB.raw"), native));
    std::string net = profile(actual);
    net.replace(net.find("ne2000=false"), 12, "ne2000=true ");
    assert(!winkit_offline::boot_profile_is_offline(net, native));
    std::string boot = profile(actual);
    boot.replace(boot.find("BOOT C:"), 7, "BOOT D:");
    assert(!winkit_offline::boot_profile_is_offline(boot, native));
    assert(::unlink(unrelated.c_str()) == 0);
    assert(::symlink(actual.c_str(), unrelated.c_str()) == 0);
    assert(!winkit_offline::boot_profile_is_offline(profile(unrelated), native));
    std::puts("PASS A14: Android private-path aliases validated; unsafe disks/commands denied");
}
