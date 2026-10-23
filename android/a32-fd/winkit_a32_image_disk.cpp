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


#if defined(__ANDROID__)
#include <jni.h>
#include <cstdio>
#include <cstring>

/* One file is staged by A32SDLActivity.getArguments() on SDLThread, BEFORE
 * native SDL_main begins. The original Java ParcelFileDescriptor stays owned
 * by Java; this copy is read-only, duplicated, and not a pseudo-path. */
static winkit_fd_disk a32_staged{-1, 0};
static bool a32_attached = false;

/* These functions belong to the pinned DOSBox-X core (dos_programs.cpp).
 * Never replace the refcount protocol by directly assigning imageDiskList. */
extern bool AttachToBiosByIndex(imageDisk *image, unsigned char bios_index);
extern bool AttachToBiosAndIdeByIndex(imageDisk *image, unsigned char bios_index,
                                      unsigned char ide_index, bool ide_slave);
extern void IDE_Auto(signed char &index, bool &slave);
extern void updateDPT(void);
extern imageDisk *imageDiskList[];

extern "C" JNIEXPORT jstring JNICALL
Java_org_winkit_android_a31smoke_A32SDLActivity_nativeStageDiskFd(
    JNIEnv *env, jclass, jint borrowed_fd, jlong length) {
    // The SDL thread prepares a new session. Never silently reuse a stale fd.
    winkit_fd_disk_close(&a32_staged);
    a32_attached = false;
    if (length <= 0) {
        return env->NewStringUTF("A32 SAF descriptor rejected: nonpositive size");
    }
    int rc = winkit_fd_disk_open(&a32_staged, borrowed_fd, (uint64_t)length);
    if (rc) {
        char status[160];
        snprintf(status, sizeof(status), "A32 SAF descriptor rejected errno=%d", rc);
        return env->NewStringUTF(status);
    }
    unsigned char sector[512];
    rc = winkit_fd_disk_read(&a32_staged, 0, 1, sector);
    if (rc || sector[510] != 0x55 || sector[511] != 0xaa) {
        winkit_fd_disk_close(&a32_staged);
        return env->NewStringUTF("A32 SAF descriptor rejected: MBR read/signature failed");
    }
    char status[180];
    snprintf(status, sizeof(status),
        "A32 SAF read-only descriptor staged: %llu sectors; BIOS attachment pending",
        (unsigned long long)a32_staged.sectors);
    return env->NewStringUTF(status);
}

/* Called on the emulator thread after BIOS_SetupDisks prepares its callbacks
 * and disk parameter tables. Only one BIOS hard disk is staged at slot 2
 * (BIOS drive 0x80). On failure, DOSBox-X continues to its normal shell. */
extern "C" void winkit_a32_attach_pending_disk(void) {
    if (a32_attached || a32_staged.fd < 0) return;
    if (imageDiskList[2] != nullptr) {
        fprintf(stderr, "[A32] BIOS HDD slot 2 occupied; leaving existing disk unchanged\n");
        return;
    }
    const uint64_t length = a32_staged.sectors * UINT64_C(512);
    WinKitA32ReadOnlyImage *disk =
        WinKitA32ReadOnlyImage::Create(a32_staged.fd, length);
    if (!disk) {
        fprintf(stderr, "[A32] Create failed; not attaching SAF disk\n");
        winkit_fd_disk_close(&a32_staged);
        return;
    }

    signed char ide = -1;
    bool slave = false;
    IDE_Auto(ide, slave);
    const bool ok = ide >= 0
        ? AttachToBiosAndIdeByIndex(disk, 2, (unsigned char)ide, slave)
        : AttachToBiosByIndex(disk, 2);
    if (!ok) {
        fprintf(stderr, "[A32] BIOS hard disk attachment rejected\n");
        delete disk;
        winkit_fd_disk_close(&a32_staged);
        return;
    }
    if (ide < 0) updateDPT();
    a32_attached = true;
    fprintf(stderr, "[A32] BIOS 0x80 attached read only, %llu sectors, IDE=%d, CHS=%u/%u/%u\n",
        (unsigned long long)a32_staged.sectors, (int)ide,
        disk->cylinders, disk->heads, disk->sectors);
    // DOSBox-X owns the disk object through AttachToBios* -> Addref().
    // Release the staging lease; the object owns a separate dup().
    winkit_fd_disk_close(&a32_staged);
}
#endif
