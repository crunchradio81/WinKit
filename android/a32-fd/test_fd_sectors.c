#define _XOPEN_SOURCE 700
#include "winkit_fd_sectors.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
int main(void) {
    char path[]="/tmp/winkit-a32-XXXXXX";
    int fd=mkstemp(path); assert(fd>=0);
    assert(ftruncate(fd, 1048576)==0);
    unsigned char mbr[512]={0}, part[512]={0};
    mbr[510]=0x55; mbr[511]=0xaa; mbr[454]=63;
    part[510]=0x55; part[511]=0xaa;
    assert(pwrite(fd,mbr,512,0)==512);
    assert(pwrite(fd,part,512,63*512)==512);
    winkit_fd_disk d={-1,0};
    assert(winkit_fd_disk_open(&d,fd,1048576)==EACCES);
    close(fd);
    fd=open(path,O_RDONLY);
    assert(fd>=0);
    assert(winkit_fd_disk_open(&d,fd,1048576)==0);
    close(fd);
    unsigned char result[512]={0};
    assert(winkit_fd_disk_read(&d,0,1,result)==0);
    assert(memcmp(result,mbr,512)==0);
    assert(winkit_fd_disk_read(&d,63,1,result)==0);
    assert(memcmp(result,part,512)==0);
    assert(winkit_fd_disk_read(&d,2048,1,result)==ERANGE);
    assert(winkit_fd_disk_read(&d,2047,2,result)==ERANGE);
    winkit_fd_disk_close(&d);
    assert(winkit_fd_disk_read(&d,0,1,result)==EINVAL);
    unlink(path);
    puts("PASS A32: read-only FD ownership, MBR, partition, bounds, lifetime");
    return 0;
}
