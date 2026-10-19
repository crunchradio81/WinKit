#include "disk_image.h"
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <stdexcept>
#include <string>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

namespace winkit {
static std::runtime_error err(const char* text) {
    return std::runtime_error(std::string(text) + ": " + std::strerror(errno));
}
DiskImage::~DiskImage() { close(); }
void DiskImage::openExclusive(const std::string& path, bool writable) {
    if (fd_ != -1) throw std::logic_error("Disk image already open");
    if (path.empty() || path.find('\0') != std::string::npos) throw std::invalid_argument("Bad disk path");
    int fd = ::open(path.c_str(), (writable ? O_RDWR : O_RDONLY) | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) throw err("Cannot open image");
    try {
        struct stat st{};
        if (fstat(fd, &st) != 0) throw err("Cannot stat image");
        if (!S_ISREG(st.st_mode) || st.st_size < 512 || (st.st_size % 512))
            throw std::runtime_error("Need sector-aligned regular raw HDD image");
        if (flock(fd, LOCK_EX | LOCK_NB) != 0) throw err("Image in use: exclusive lock denied");
        fd_ = fd;
        size_ = static_cast<uint64_t>(st.st_size);
        writable_ = writable;
    } catch (...) { ::close(fd); throw; }
}
void DiskImage::transfer(uint64_t lba, uint8_t* data, bool write) {
    if (fd_ < 0) throw std::logic_error("Image not open");
    if (write && !writable_) throw std::logic_error("Image is read-only");
    if (data == nullptr) throw std::invalid_argument("Null sector data");
    if (lba >= size_ / 512) throw std::out_of_range("Sector is outside disk");
    const off_t offset = static_cast<off_t>(lba * 512ULL); // st_size bounds off_t
    size_t done = 0;
    while (done < 512) {
        ssize_t n = write ? ::pwrite(fd_, data + done, 512 - done, offset + done)
                          : ::pread(fd_, data + done, 512 - done, offset + done);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) throw err("Disk short I/O");
        done += static_cast<size_t>(n);
    }
}
void DiskImage::readSector(uint64_t lba, uint8_t* dst) { transfer(lba, dst, false); }
void DiskImage::writeSector(uint64_t lba, const uint8_t* src) {
    transfer(lba, const_cast<uint8_t*>(src), true);
}
void DiskImage::flush() {
    if (fd_ >= 0 && writable_ && fsync(fd_) != 0) throw err("Disk fsync failure");
}
void DiskImage::close() {
    if (fd_ >= 0) {
        // Caller should flush before closing. Destructor deliberately cannot throw.
        if (writable_) (void)fsync(fd_);
        (void)::flock(fd_, LOCK_UN);
        (void)::close(fd_);
        fd_ = -1;
        size_ = 0;
        writable_ = false;
    }
}
}
