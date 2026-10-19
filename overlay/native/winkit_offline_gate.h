// WinKit A3 source overlay. Host-testable strict OFFLINE boot gate.
// Intended for the Retro-DOS DOSBox-X SDL3 frontend. GPL-2.0-or-later.
#pragma once
#include <algorithm>
#include <cctype>
#include <climits>
#include <sstream>
#include <string>
#include <vector>
#include <set>

namespace winkit_offline {
inline std::string trim(const std::string &s) {
    size_t a = s.find_first_not_of(" \t\r");
    if (a == std::string::npos) return "";
    return s.substr(a, s.find_last_not_of(" \t\r") + 1 - a);
}
inline std::string lower(std::string s) {
    for (char &c : s) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return s;
}
inline bool tokens(const std::string &line, std::vector<std::string> &out) {
    // No escaping, metacharacters, or shell chaining are supported on boot lines.
    out.clear(); std::string cur; bool quoted = false, active = false;
    for (char c : line) {
        if (c == '\"') { quoted = !quoted; active = true; continue; }
        if (c == '&' || c == '|' || c == '>' || c == '<' || c == '`' || c == '\\') return false;
        if (!quoted && (c == ' ' || c == '\t')) {
            if (active) { out.push_back(cur); cur.clear(); active = false; }
        } else { cur += c; active = true; }
    }
    if (quoted) return false;
    if (active) out.push_back(cur);
    return true;
}
inline bool is_integer(const std::string &s) {
    if (s.empty() || s.size() > 12) return false;
    return std::all_of(s.begin(), s.end(), [](unsigned char c) {return std::isdigit(c) != 0;});
}
inline bool geometry(const std::string &v) {
    std::stringstream ss(v); std::string part; int count = 0;
    while (std::getline(ss, part, ',')) {
        if (!is_integer(part)) return false;
        try { if (std::stoull(part) == 0 || std::stoull(part) > INT_MAX) return false; }
        catch (...) { return false; }
        ++count;
    }
    return count == 4 && !v.empty() && v.back() != ',';
}
inline bool image_mount_is_safe(const std::string &line, const std::string &disk) {
    std::vector<std::string> parts;
    if (!tokens(line, parts) || parts.size() != 9) return false;
    for (auto &part : parts) if (part.empty()) return false;
    // DOSBox-X IMGMOUNT 2 /private/... -t hdd -fs none -size b,s,h,c
    if (lower(parts[0]) != "imgmount" || (lower(parts[1]) != "2" && lower(parts[1]) != "c") ||
        parts[2] != disk) return false;
    bool type = false, fs = false, size = false;
    for (size_t i = 3; i < parts.size();) {
        if (i + 1 >= parts.size()) return false;
        const auto flag = lower(parts[i]), value = lower(parts[i + 1]);
        if (flag == "-t" && value == "hdd" && !type) type = true;
        else if (flag == "-fs" && value == "none" && !fs) fs = true;
        else if (flag == "-size" && geometry(parts[i + 1]) && !size) size = true;
        else return false;
        i += 2;
    }
    return type && fs && size;
}
inline bool boot_profile_is_offline(const std::string &conf, const std::string &expected_disk) {
    if (conf.empty() || conf.size() > 262144 || expected_disk.empty() ||
        (expected_disk.size() < 22 || expected_disk.compare(expected_disk.size()-22, 22, "/machines/YARDOMOB.raw") != 0) ||
        expected_disk.find('\n') != std::string::npos || expected_disk.find('"') != std::string::npos ||
        expected_disk.find('\r') != std::string::npos || expected_disk.find('\\') != std::string::npos ||
        expected_disk[0] != '/' || conf.find('\0') != std::string::npos ||
        conf.find('\r') != std::string::npos) return false;
    std::string section; std::set<std::string> sections;
    int net = 0, ipx = 0, mounts = 0, boots = 0;
    size_t start = 0;
    while (start < conf.size()) {
        size_t end = conf.find('\n', start);
        if (end == std::string::npos) end = conf.size();
        const auto line = trim(conf.substr(start, end - start));
        start = end + 1;
        if (line.empty() || line[0] == '#' || line[0] == ';') continue;
        if (line[0] == '[') {
            if (line.back() != ']' || line.size() < 3) return false;
            section = lower(trim(line.substr(1, line.size() - 2)));
            if (section.empty() || !sections.insert(section).second) return false;
            continue;
        }
        const auto l = lower(line);
        if (section.empty()) return false;
        // A9: first-boot offline mode must not expose alternative host transports.
        // A disabled/dummy legacy serial port is harmless; all live serial,
        // printer and Ethernet/tunnel sections require an explicit future mode.
        if (section == "ethernet" || section == "ethnet" ||
            section == "network" || section == "ipxnet" ||
            section == "modem" || section == "slirp") return false;
        if (section == "serial" || section == "parallel") {
            const auto eq = l.find('=');
            if (eq == std::string::npos) return false;
            const auto key = trim(l.substr(0, eq));
            const auto value = trim(l.substr(eq + 1));
            if (key.empty() || (value != "disabled" && value != "dummy" && value != "none"))
                return false;
        }
        // Reject second startup scripts or host transport selectors, even when
        // placed outside [autoexec] where a DOSBox-X variant might parse them.
        const auto eq = l.find('=');
        if (eq != std::string::npos) {
            const auto key = trim(l.substr(0, eq));
            if (key == "configfile" || key == "config file" ||
                key == "autoexec" || key == "sdl_net" || key == "ethernet" ||
                key == "tunnel" || key == "hostip" || key == "host port") return false;
        }
        if (section == "ne2000") {
            if (l == "ne2000=false") ++net;
            else if (l.rfind("ne2000", 0) == 0) return false;
        }
        if (section == "ipx") {
            if (l == "ipx=false") ++ipx;
            else if (l.rfind("ipx", 0) == 0) return false;
        }
        if (section == "autoexec") {
            if (boots) return false;  // No command after BOOT, including another disk or networking.
            if (l == "@echo off" || l == "echo off" || l == "cls" || l == "pause" ||
                l == "rem" || l.rfind("rem ", 0) == 0 || l == "@rem" ||
                l.rfind("@rem ", 0) == 0 || l.rfind("echo ", 0) == 0 ||
                l.rfind("@echo ", 0) == 0) {
                if (line.find_first_of("&|<>`\\") != std::string::npos) return false;
                continue;
            }
            if (l.rfind("imgmount ", 0) == 0) {
                if (mounts || !image_mount_is_safe(line, expected_disk)) return false;
                ++mounts;
            } else if (l == "boot c:" || l == "boot -l c") {
                if (mounts != 1 || boots) return false;
                ++boots;
            } else return false;
        }
    }
    return sections.count("autoexec") && net == 1 && ipx == 1 && mounts == 1 && boots == 1;
}
} // namespace winkit_offline
