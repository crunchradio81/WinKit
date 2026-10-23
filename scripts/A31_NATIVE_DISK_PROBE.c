/* Included by A31_DOSBOX_ENTRY_BRIDGE.c. Read-only SAF FD interoperability test.
 * No image bytes are copied except two 512-byte sectors; no guest mount. */
#include <jni.h>
#include <sys/stat.h>
#include <stdint.h>

JNIEXPORT jstring JNICALL
Java_org_winkit_android_a31smoke_A31GuestDisk_nativeProbeReadableFd(
    JNIEnv *env, jclass clazz, jint fd, jlong expectedSize) {
    (void)clazz;
    char result[960];
    if (fd < 0 || expectedSize < 1048576 || expectedSize % 512 != 0)
        return (*env)->NewStringUTF(env, "Native probe FAIL: invalid sector-aligned FD.");
    struct stat st;
    if (fstat(fd,&st) != 0) {
        snprintf(result,sizeof(result),"Native fstat FAIL errno=%d",errno);
        return (*env)->NewStringUTF(env,result);
    }
    if (st.st_size != expectedSize) {
        snprintf(result,sizeof(result),"Native size mismatch expected=%lld actual=%lld",
             (long long)expectedSize,(long long)st.st_size);
        return (*env)->NewStringUTF(env,result);
    }
    unsigned char sector[512];
    ssize_t n = pread(fd,sector,512,0);
    if (n != 512 || sector[510] != 0x55 || sector[511] != 0xaa) {
        snprintf(result,sizeof(result),"Native MBR pread FAIL length=%lld errno=%d",
             (long long)n,errno);
        return (*env)->NewStringUTF(env,result);
    }
    uint64_t lba = (uint64_t)sector[454] | ((uint64_t)sector[455]<<8)
        | ((uint64_t)sector[456]<<16) | ((uint64_t)sector[457]<<24);
    int partition_valid = 0;
    if (lba > 0 && lba < (uint64_t)(expectedSize / 512)) {
        unsigned char part[512];
        n=pread(fd,part,512,(off_t)lba*512);
        partition_valid=n==512 && part[510]==0x55 && part[511]==0xaa;
    }
    char path[64];
    snprintf(path,sizeof(path),"/proc/self/fd/%d",fd);
    /* DOSBox-X uses stdio for disk image access. Probe only 'rb' reopening. */
    FILE *reopen=fopen(path,"rb");
    if (!reopen) {
        snprintf(result,sizeof(result),
          "Native read-only pread PASS; partition LBA=%llu boot=%s. "
          "stdio /proc/self/fd reopen FAIL errno=%d; FD-backed I/O required.",
          (unsigned long long)lba,partition_valid?"PASS":"unverified",errno);
        return (*env)->NewStringUTF(env,result);
    }
    unsigned char compare[512];
    size_t got=fread(compare,1,512,reopen);
    int ok=got==512 && memcmp(compare,sector,512)==0;
    fclose(reopen);
    snprintf(result,sizeof(result),
      "Native read-only pread PASS; partition LBA=%llu boot=%s. "
      "stdio /proc/self/fd reopen %s. No image modified or mounted.",
      (unsigned long long)lba,partition_valid?"PASS":"unverified",ok?"PASS":"FAIL");
    return (*env)->NewStringUTF(env,result);
}
