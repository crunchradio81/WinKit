/* WinKit A32: read-only, owned descriptor-backed RAW sectors.
 * No /proc/self/fd reopen; no fdopen; no file path required.
 * The caller passes an existing SAF read-only fd and retains ownership of it.
 * The imageDisk subclass is separate; native core linking is still pending.
 */
#ifndef WINKIT_A32_FD_SECTORS_H
#define WINKIT_A32_FD_SECTORS_H
#include <errno.h>
#include <stdint.h>
#include <stddef.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <limits.h>

typedef struct {
    int fd;
    uint64_t sectors;
} winkit_fd_disk;

static inline int winkit_fd_disk_open(winkit_fd_disk *disk, int borrowed_fd, uint64_t bytes) {
    if (!disk) return EINVAL;
    disk->fd = -1;
    disk->sectors = 0;
    if (borrowed_fd < 0 || bytes < 1048576 || bytes % 512) return EINVAL;
    if (bytes > (uint64_t)INT64_MAX) return EOVERFLOW;
    int flags = fcntl(borrowed_fd, F_GETFL);
    if (flags < 0) return errno;
    if ((flags & O_ACCMODE) != O_RDONLY) return EACCES;
    struct stat st;
    if (fstat(borrowed_fd, &st) != 0) return errno;
    if (st.st_size < 0 || (uint64_t)st.st_size != bytes) return EINVAL;
    int owned = dup(borrowed_fd);
    if (owned < 0) return errno;
    disk->fd = owned;
    disk->sectors = bytes / 512;
    return 0;
}
static inline int winkit_fd_disk_read(winkit_fd_disk *disk, uint64_t lba,
                               uint32_t count, void *output) {
    if (!disk || disk->fd < 0 || !output || count == 0) return EINVAL;
    if (lba >= disk->sectors || (uint64_t)count > disk->sectors - lba)
        return ERANGE;
    uint64_t offset = lba * UINT64_C(512);
    const uint64_t count_bytes = (uint64_t)count * UINT64_C(512);
    if (count_bytes > (uint64_t)SIZE_MAX) return EOVERFLOW;
    size_t total = (size_t)count_bytes;
    unsigned char *p = (unsigned char *)output;
    size_t done = 0;
    while (done < total) {
        ssize_t n = pread(disk->fd, p + done, total - done, (off_t)(offset + done));
        if (n < 0 && errno == EINTR) continue;
        if (n < 0) return errno;
        if (n == 0) return EIO;
        done += (size_t)n;
    }
    return 0;
}
static inline void winkit_fd_disk_close(winkit_fd_disk *disk) {
    if (!disk) return;
    if (disk->fd >= 0) close(disk->fd);
    disk->fd = -1;
    disk->sectors = 0;
}
#endif
