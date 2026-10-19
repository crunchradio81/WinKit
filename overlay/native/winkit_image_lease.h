// WinKit A3: POSIX advisory whole-image lease for app-private Android guest HDD.
// GPL-2.0-or-later; used only when booting this WinKit machine in SDL3.
#pragma once
#if defined(__unix__) || defined(__APPLE__) || defined(__ANDROID__)
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <unistd.h>
#include <cerrno>
#include <string>
#include <cstdint>
namespace winkit_lease {
class ImageLease {
    int fd_ = -1;
    uint64_t bytes_ = 0;
public:
    explicit ImageLease(const std::string& path) {
        constexpr const char *suffix = "/machines/YARDOMOB.raw";
        const auto n = std::string(suffix).size();
        if (path.size() < n || path.compare(path.size()-n, n, suffix) != 0) return;
        fd_ = ::open(path.c_str(), O_RDWR | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
        if (fd_ < 0) return;
        struct stat st{};
        if (::fstat(fd_, &st) != 0 || !S_ISREG(st.st_mode) || st.st_nlink != 1 ||
            st.st_size < 512 || (st.st_size % 512) != 0 ||
            ::flock(fd_, LOCK_EX | LOCK_NB) != 0) {
            ::close(fd_);
            fd_ = -1;
        } else {
            bytes_ = static_cast<uint64_t>(st.st_size);
        }
    }
    ImageLease(const ImageLease&) = delete;
    ImageLease& operator=(const ImageLease&) = delete;
    ~ImageLease() { if (fd_ >= 0) { ::flock(fd_, LOCK_UN); ::close(fd_); } }
    bool acquired() const { return fd_ >= 0; }
    int descriptor() const { return fd_; }
    uint64_t bytes() const { return bytes_; }
};
} // namespace winkit_lease
#endif
