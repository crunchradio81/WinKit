#include "winkit_offline_gate.h"
#include <cassert>
#include <iostream>
#include <string>
int main() {
    const std::string disk = "/data/user/0/org.winkit.android.a2/files/machines/YARDOMOB.raw";
    const std::string good = "[ne2000]\nne2000=false\n[ipx]\nipx=false\n[autoexec]\nIMGMOUNT 2 \"" + disk + "\" -t hdd -fs none -size 512,63,16,1024\nBOOT C:\n";
    assert(winkit_offline::boot_profile_is_offline(good, disk));
    assert(!winkit_offline::boot_profile_is_offline(good, "/wrong/YARDOMOB.raw"));
    assert(!winkit_offline::boot_profile_is_offline(good.substr(0, good.size()-8), disk));
    assert(!winkit_offline::boot_profile_is_offline(good + "ETHNET CONNECT 1.2.3.4\n", disk));
    assert(!winkit_offline::boot_profile_is_offline(good + "IPXNET STARTSERVER\n", disk));
    assert(!winkit_offline::boot_profile_is_offline(good.substr(good.find("[ipx]")), disk));
    std::cout << "A2 native boot-profile gate: 6 assertions passed\n";
}
