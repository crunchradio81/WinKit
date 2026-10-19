#pragma once
#include <cstdint>
#include <cstddef>
#include <string>

namespace winkit {
/** Holds an exclusive OS lock for exactly one local regular raw disk image.
 * Android emulator integration must retain this handle for the entire running guest.
 * This class does not infer BIOS geometry or boot an OS. */
class DiskImage {
public:
    DiskImage() = default;
    ~DiskImage();
    DiskImage(const DiskImage&) = delete;
    DiskImage& operator=(const DiskImage&) = delete;
    void openExclusive(const std::string& path, bool writable);
    void readSector(uint64_t lba, uint8_t* dst);
    void writeSector(uint64_t lba, const uint8_t* src);
    void flush();
    uint64_t bytes() const { return size_; }
    void close();
private:
    int fd_ = -1;
    uint64_t size_ = 0;
    bool writable_ = false;
    void transfer(uint64_t lba, uint8_t* bytes, bool write);
};
}
