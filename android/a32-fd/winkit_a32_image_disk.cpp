#include "winkit_a32_image_disk.h"
#include <cerrno>
#include <limits>

WinKitA32ReadOnlyImage::WinKitA32ReadOnlyImage() : imageDisk(ID_BASE) {}

WinKitA32ReadOnlyImage *WinKitA32ReadOnlyImage::Create(int borrowed_fd,
                                                       uint64_t bytes) {
    WinKitA32ReadOnlyImage *disk = new WinKitA32ReadOnlyImage();
    int rc = winkit_fd_disk_open(&disk->handle, borrowed_fd, bytes);
    if (rc != 0) {
        delete disk;
        return nullptr;
    }
    disk->diskname = "YARDOMOB.raw [SAF read only]";
    disk->image_base = 0;
    disk->image_length = bytes;
    disk->diskSizeK = bytes / 1024;
    disk->LBA = bytes / 512;
    disk->sector_size = 512;
    disk->hardDrive = true;
    disk->active = true;
    disk->diskimg = nullptr;
    // Read the real partition BPB before selecting legacy BIOS CHS geometry.
    // An arbitrary 16-head translation would truncate access near 1 GiB
    // when legacy DOS INT 13h requests sectors on this ~2 GiB image.
    uint32_t chosenHeads = 255;
    uint32_t chosenSectors = 63;
    unsigned char mbr[512];
    if (winkit_fd_disk_read(&disk->handle, 0, 1, mbr) == 0
            && mbr[510] == 0x55 && mbr[511] == 0xaa) {
        for (unsigned i = 0; i < 4; ++i) {
            const unsigned p = 446 + i * 16;
            const uint64_t first = (uint64_t)mbr[p+8]
                | ((uint64_t)mbr[p+9] << 8) | ((uint64_t)mbr[p+10] << 16)
                | ((uint64_t)mbr[p+11] << 24);
            const uint64_t length = (uint64_t)mbr[p+12]
                | ((uint64_t)mbr[p+13] << 8) | ((uint64_t)mbr[p+14] << 16)
                | ((uint64_t)mbr[p+15] << 24);
            if (mbr[p+4] == 0 || !first || !length
                || first >= disk->handle.sectors
                || length > disk->handle.sectors-first) continue;
            unsigned char boot[512];
            if (winkit_fd_disk_read(&disk->handle, first, 1, boot) != 0
                || boot[510] != 0x55 || boot[511] != 0xaa) continue;
            const uint32_t spt = (uint32_t)boot[0x18]
                | ((uint32_t)boot[0x19] << 8);
            const uint32_t heads = (uint32_t)boot[0x1a]
                | ((uint32_t)boot[0x1b] << 8);
            if (heads >= 1 && heads <= 255 && spt >= 1 && spt <= 63) {
                const uint64_t tracks = (disk->handle.sectors
                    + (uint64_t)heads*spt - 1) / ((uint64_t)heads*spt);
                if (tracks <= 1024) {
                    chosenHeads = heads;
                    chosenSectors = spt;
                }
                // Otherwise keep 255/63 translation to expose all 2GB.
            }
            break;
        }
    }
    disk->heads = chosenHeads;
    disk->sectors = chosenSectors;
    const uint64_t divisor = (uint64_t)chosenHeads * chosenSectors;
    const uint64_t tracks = (disk->handle.sectors + divisor - 1) / divisor;
    if (!tracks || tracks > 1024) {
        delete disk;
        return nullptr; // cannot represent image within legacy CHS limits
    }
    disk->cylinders = static_cast<uint32_t>(tracks);
    return disk;
}

WinKitA32ReadOnlyImage::~WinKitA32ReadOnlyImage() {
    winkit_fd_disk_close(&handle);
}

Int13Status WinKitA32ReadOnlyImage::Read_AbsoluteSector(uint32_t lba,
                                                         void *dst) {
    const int rc = winkit_fd_disk_read(&handle, lba, 1, dst);
    if (rc == 0) return Int13Status::NoError;
    if (rc == ERANGE || rc == EINVAL) return Int13Status::SectorNotFound;
    return Int13Status::ControllerFailure;
}

Int13Status WinKitA32ReadOnlyImage::Write_AbsoluteSector(
    uint32_t, const void *) {
    return Int13Status::WriteProtected;
}

Int13Status WinKitA32ReadOnlyImage::Read_Sector(
    uint32_t head, uint32_t cylinder, uint32_t sector, void *dst,
    unsigned int requested) {
    if (requested != 0 && requested != 512) return Int13Status::SectorNotFound;
    if (head >= heads || cylinder >= cylinders ||
        sector == 0 || sector > sectors) return Int13Status::SectorNotFound;
    const uint64_t lba = (static_cast<uint64_t>(cylinder) * heads + head)
        * sectors + (sector - 1);
    if (lba > std::numeric_limits<uint32_t>::max())
        return Int13Status::SectorNotFound;
    return Read_AbsoluteSector(static_cast<uint32_t>(lba), dst);
}

Int13Status WinKitA32ReadOnlyImage::Write_Sector(
    uint32_t, uint32_t, uint32_t, const void *, unsigned int) {
    return Int13Status::WriteProtected;
}
