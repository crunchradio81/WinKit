// WinKit A5: independent raw HDD geometry + container guard.
// Host-testable, no Android API, network or Microsoft files.
// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include "winkit_offline_gate.h"
#include <array>
#include <cstdint>
#include <cstring>
#include <fcntl.h>
#include <limits>
#include <sys/stat.h>
#include <unistd.h>
#include <string>
#include <vector>

namespace winkit_raw {
inline bool geometry_bytes(const std::string& profile, const std::string& disk, uint64_t& bytes) {
    bytes = 0;
    if (!winkit_offline::boot_profile_is_offline(profile, disk)) return false;
    bool autoexec = false;
    size_t off = 0;
    while (off < profile.size()) {
        auto end = profile.find('\n', off);
        if (end == std::string::npos) end = profile.size();
        auto line = winkit_offline::trim(profile.substr(off, end - off));
        off = end + 1;
        if (line.empty() || line[0] == '#' || line[0] == ';') continue;
        if (line[0] == '[') { autoexec = winkit_offline::lower(line) == "[autoexec]"; continue; }
        if (!autoexec) continue;
        std::vector<std::string> words;
        if (!winkit_offline::tokens(line, words) || words.empty() ||
            winkit_offline::lower(words[0]) != "imgmount") continue;
        for (size_t i = 3; i + 1 < words.size(); i += 2) {
            if (winkit_offline::lower(words[i]) != "-size") continue;
            std::array<uint64_t,4> chs{};
            size_t pos = 0;
            for (size_t index = 0; index < chs.size(); ++index) {
                auto comma = words[i+1].find(',', pos);
                if (index < 3 && comma == std::string::npos) return false;
                if (index == 3 && comma != std::string::npos) return false;
                auto item = words[i+1].substr(pos, comma == std::string::npos ? std::string::npos : comma-pos);
                if (!winkit_offline::is_integer(item)) return false;
                try { chs[index] = std::stoull(item); } catch (...) { return false; }
                pos = comma == std::string::npos ? words[i+1].size() : comma+1;
            }
            // Raw PC HDDs: bytes/sector, sectors/track, heads, cylinders.
            if (chs[0] != 512 || chs[1] < 1 || chs[1] > 63 ||
                chs[2] < 1 || chs[2] > 255 || chs[3] < 1 || chs[3] > 65535) return false;
            uint64_t total = chs[0];
            for (size_t j = 1; j < 4; ++j) {
                if (total > std::numeric_limits<uint64_t>::max()/chs[j]) return false;
                total *= chs[j];
            }
            bytes = total;
            return true;
        }
    }
    return false;
}
inline bool supported_raw_container_fd(int fd, uint64_t size) {
    if (fd < 0 || size < 512 || size > static_cast<uint64_t>(std::numeric_limits<off_t>::max())) return false;
    unsigned char head[8]{};
    if (::pread(fd, head, sizeof(head), 0) != static_cast<ssize_t>(sizeof(head))) return false;
    const unsigned char qcow[] = { 'Q','F','I',0xfb };
    const unsigned char zip[] = {'P','K',3,4};
    const unsigned char gzip[] = {0x1f,0x8b};
    const unsigned char sevenzip[] = {'7','z',0xbc,0xaf,0x27,0x1c};
    if (!std::memcmp(head, qcow, 4) || !std::memcmp(head, zip, 4) ||
        !std::memcmp(head, gzip, 2) || !std::memcmp(head, sevenzip, 6) ||
        !std::memcmp(head, "KDMV", 4) || !std::memcmp(head, "vhdxfile", 8) ||
        !std::memcmp(head, "conectix", 8)) return false;
    // A fixed VHD has a virtual-hard-disk footer, not raw sectors.
    unsigned char tail[8]{};
    if (::pread(fd, tail, sizeof(tail), static_cast<off_t>(size - 512)) !=
        static_cast<ssize_t>(sizeof(tail))) return false;
    if (!std::memcmp(tail, "conectix", 8)) return false;
    return true;
}
inline bool image_matches_profile(const std::string& conf, const std::string& disk,
                                  int fd, uint64_t size) {
    uint64_t expected = 0;
    return geometry_bytes(conf, disk, expected) && expected == size &&
           supported_raw_container_fd(fd, size);
}
} // namespace winkit_raw
