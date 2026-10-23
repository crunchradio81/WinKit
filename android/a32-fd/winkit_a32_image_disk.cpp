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
    // DOSBox-X uses CHS for legacy INT 13h. 16 heads / 63 sectors
    // gives a conservative geometry for this 2GB DOS 6.22 image.
    // The emulated BIOS may need to reconcile this with the guest's BPB.
    disk->heads = 16;
    disk->sectors = 63;
    disk->cylinders = static_cast<uint32_t>(
        (bytes / 512) / (16u * 63u));
    if (disk->cylinders == 0 || disk->cylinders > 1024) {
        // Large LBA images exceed legacy 1024-cylinder BIOS geometry.
        // Cap CHS geometry; absolute LBA reads remain full-range.
        disk->cylinders = 1024;
    }
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
