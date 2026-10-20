// WinKit A6: validate the *contents* of an on-disk DOSBox-X config and
// create a private per-run immutable-in-practice snapshot before launching.
// Prevents passing the config filename into a validator expecting text.
// No Android headers / sockets / guest OS artifacts required.
// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once
#include "winkit_raw_geometry.h"
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

namespace winkit_launch {
constexpr size_t MAX_PROFILE = 262144;

// Fails closed for symlinks, hardlinks, directories, empty or oversized files.
inline bool read_profile(const std::string &path, std::string &out) {
    out.clear();
    if (path.empty() || path[0] != '/' || path.find('\0') != std::string::npos) return false;
    const int fd = ::open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    struct stat st{};
    bool ok = (::fstat(fd, &st) == 0 && S_ISREG(st.st_mode) && st.st_nlink == 1 &&
               st.st_size > 0 && st.st_size <= static_cast<off_t>(MAX_PROFILE));
    if (ok) {
        std::string data(static_cast<size_t>(st.st_size), '\0');
        size_t got = 0;
        while (got < data.size()) {
            const ssize_t n = ::read(fd, &data[got], data.size() - got);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) { ok = false; break; }
            got += static_cast<size_t>(n);
        }
        // Reject a file modified during this read, or unexpected extra bytes.
        struct stat after{};
        if (ok && (::fstat(fd, &after) != 0 || after.st_size != st.st_size ||
                   after.st_ino != st.st_ino || after.st_dev != st.st_dev)) ok = false;
        if (ok && data.find('\0') == std::string::npos) out.swap(data);
        else ok = false;
    }
    ::close(fd);
    if (!ok) out.clear();
    return ok;
}

// A25: WinKit's frontend already holds an exclusive flock for this guest HDD.
// DOSBox-X defaults "locking disk image mount" to true, so its own second
// flock (via fopen_lock) fails even in the same process. Disable ONLY that
// redundant lock in the validated, disposable launch snapshot. Original
// profiles and the guest disk are never rewritten.
inline bool no_second_disk_lock(const std::string& validated, std::string& out) {
    out.clear();
    if (validated.empty() || validated.size() > MAX_PROFILE) return false;
    auto trim = [](const std::string& s) {
        const size_t a = s.find_first_not_of(" \t\r");
        if (a == std::string::npos) return std::string();
        const size_t b = s.find_last_not_of(" \t\r");
        return s.substr(a, b - a + 1);
    };
    auto lower = [](std::string s) {
        for (char& c : s) if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
        return s;
    };
    bool in_dosbox = false, seen_dosbox = false;
    size_t pos = 0;
    while (pos < validated.size()) {
        const size_t e = validated.find('\n', pos);
        const size_t next = e == std::string::npos ? validated.size() : e + 1;
        const std::string line = validated.substr(pos, next - pos);
        const std::string normalized = lower(trim(line));
        if (!normalized.empty() && normalized.front() == '[') {
            const size_t end = normalized.find(']');
            if (end != std::string::npos) {
                if (in_dosbox) {
                    if (!out.empty() && out.back() != '\n') out += '\n';
                    out += "locking disk image mount=false\n";
                    in_dosbox = false;
                }
                if (trim(normalized.substr(1, end - 1)) == "dosbox") {
                    if (seen_dosbox) { out.clear(); return false; }
                    seen_dosbox = in_dosbox = true;
                }
            }
        }
        const size_t eq = normalized.find('=');
        if (!(in_dosbox && eq != std::string::npos &&
              trim(normalized.substr(0, eq)) == "locking disk image mount"))
            out += line;
        pos = next;
    }
    if (!seen_dosbox) { out.clear(); return false; }
    if (in_dosbox) {
        if (!out.empty() && out.back() != '\n') out += '\n';
        out += "locking disk image mount=false\n";
    }
    if (out.size() > MAX_PROFILE) { out.clear(); return false; }
    return true;
}

class ValidatedLaunch {
    std::string snapshot_;
public:
    ValidatedLaunch(const std::string &launch_conf_path,
                    const std::string &prepared_conf_path,
                    const std::string &disk_path, int disk_fd,
                    uint64_t disk_bytes) {
        std::string launch_text, prepared_text;
        if (!read_profile(launch_conf_path, launch_text) ||
            !read_profile(prepared_conf_path, prepared_text) ||
            launch_text != prepared_text ||
            !winkit_raw::image_matches_profile(launch_text, disk_path,
                                               disk_fd, disk_bytes)) return;
        std::string runtime_text;
        if (!no_second_disk_lock(launch_text, runtime_text)) return;
        // mkstemp is atomic and cannot overwrite an existing guest/profile.
        const auto slash = disk_path.rfind('/');
        if (slash == std::string::npos) return;
        std::string name = disk_path.substr(0, slash) + "/.winkit-a6-run-XXXXXX";
        std::vector<char> tmpl(name.begin(), name.end()); tmpl.push_back('\0');
        int fd = ::mkstemp(tmpl.data());
        if (fd < 0) return;
        name = tmpl.data();
        bool ok = true;
        size_t offset = 0;
        while (offset < runtime_text.size()) {
            const ssize_t n = ::write(fd, runtime_text.data() + offset,
                                      runtime_text.size() - offset);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) { ok = false; break; }
            offset += static_cast<size_t>(n);
        }
        if (ok && ::fsync(fd) != 0) ok = false;
        if (ok && ::fchmod(fd, S_IRUSR) != 0) ok = false;
        if (::close(fd) != 0) ok = false;
        if (ok) snapshot_ = name;
        else ::unlink(name.c_str());
    }
    ValidatedLaunch(const ValidatedLaunch&) = delete;
    ValidatedLaunch& operator=(const ValidatedLaunch&) = delete;
    ~ValidatedLaunch() { if (!snapshot_.empty()) ::unlink(snapshot_.c_str()); }
    bool valid() const { return !snapshot_.empty(); }
    const std::string &config_path() const { return snapshot_; }
};
} // namespace winkit_launch
