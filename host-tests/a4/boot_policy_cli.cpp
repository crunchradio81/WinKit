#include "winkit_offline_gate.h"
#include <fstream>
#include <iostream>
#include <iterator>
#include <string>
int main(int argc, char **argv) {
    if (argc != 3) return 2;
    std::ifstream src(argv[1], std::ios::binary);
    if (!src) return 3;
    const std::string config((std::istreambuf_iterator<char>(src)), std::istreambuf_iterator<char>());
    if (!winkit_offline::boot_profile_is_offline(config, argv[2])) {
        std::cerr << "OFFLINE POLICY REJECTED\n";return 1;
    }
    std::cout << "OFFLINE POLICY ACCEPTED (SOURCE ONLY; NO GUEST BOOT)\n";
    return 0;
}
