/* A32: DOSBox-X imageDisk adapter for a borrowed Android SAF read-only FD.
 * Compile as part of the DOSBox-X native library, not as a standalone JNI DLL.
 * Factory yields an imageDisk* suitable for the emulator's disk attachment path.
 * WARNING: factory creation alone does not attach a disk to BIOS/IDE.
 */
#ifndef WINKIT_A32_IMAGE_DISK_H
#define WINKIT_A32_IMAGE_DISK_H
#include "bios_disk.h"
#include "winkit_fd_sectors.h"
#include <cstdint>

class WinKitA32ReadOnlyImage final : public imageDisk {
public:
    // Returns nullptr if the descriptor is not truly read-only, or size invalid.
    static WinKitA32ReadOnlyImage *Create(int borrowed_fd, uint64_t bytes);
    ~WinKitA32ReadOnlyImage() override;

    Int13Status Read_AbsoluteSector(uint32_t lba, void *dst) override;
    Int13Status Write_AbsoluteSector(uint32_t lba, const void *src) override;
    Int13Status Read_Sector(uint32_t head, uint32_t cylinder,
                            uint32_t sector, void *dst,
                            unsigned int req_sector_size=0) override;
    Int13Status Write_Sector(uint32_t head, uint32_t cylinder,
                             uint32_t sector, const void *src,
                             unsigned int req_sector_size=0) override;
private:
    WinKitA32ReadOnlyImage();
    winkit_fd_disk handle{-1,0};
};

#endif
