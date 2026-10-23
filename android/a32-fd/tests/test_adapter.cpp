/* Host-only behavioral test of the actual A32 adapter source.
 * Fixture is a sparse temporary RAW file, never user YARDOMOB.raw. */
#define _XOPEN_SOURCE 700
#include "winkit_a32_image_disk.h"
#include <cassert>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
int main() {
    constexpr uint64_t bytes=2147475456ULL;
    constexpr uint32_t sectors=(uint32_t)(bytes/512);
    char filename[]="/tmp/a32-raw-XXXXXX";
    int f=mkstemp(filename);
    assert(f>=0);
    assert(ftruncate(f,(off_t)bytes)==0);
    unsigned char mbr[512]={0}, boot[512]={0}, last[512]={0};
    mbr[446+4]=0x06;
    mbr[446+8]=63;
    const uint32_t partLen=4185153U;
    for(int i=0;i<4;i++)mbr[446+12+i]=(unsigned char)(partLen>>(i*8));
    mbr[510]=0x55;mbr[511]=0xaa;
    boot[0x18]=63;boot[0x19]=0;
    boot[0x1a]=255;boot[0x1b]=0;
    boot[510]=0x55;boot[511]=0xaa;
    last[0]=0x5a;last[511]=0xa5;
    assert(pwrite(f,mbr,512,0)==512);
    assert(pwrite(f,boot,512,63*512)==512);
    assert(pwrite(f,last,512,(off_t)(sectors-1)*512)==512);
    assert(WinKitA32ReadOnlyImage::Create(f,bytes)==nullptr);
    close(f);
    f=open(filename,O_RDONLY);assert(f>=0);
    imageDisk *disk=WinKitA32ReadOnlyImage::Create(f,bytes);
    assert(disk && disk->active && disk->hardDrive);
    assert(disk->heads==255 && disk->sectors==63);
    assert(disk->cylinders>=262 && disk->image_length==bytes);
    close(f); // adapter retains its independent duplicate
    unsigned char result[512]={0};
    assert(disk->Read_AbsoluteSector(0,result)==Int13Status::NoError);
    assert(!memcmp(mbr,result,512));
    assert(disk->Read_AbsoluteSector(63,result)==Int13Status::NoError);
    assert(!memcmp(boot,result,512));
    assert(disk->Read_AbsoluteSector(sectors-1,result)==Int13Status::NoError);
    assert(!memcmp(last,result,512));
    assert(disk->Read_AbsoluteSector(sectors,result)==Int13Status::SectorNotFound);
    const uint32_t track=255U*63U, cylinder=(sectors-1)/track;
    const uint32_t head=((sectors-1)%track)/63U;
    const uint32_t sector=((sectors-1)%63U)+1U;
    assert(disk->Read_Sector(head,cylinder,sector,result)==Int13Status::NoError);
    assert(!memcmp(last,result,512));
    assert(disk->Read_Sector(255,0,1,result)==Int13Status::SectorNotFound);
    assert(disk->Write_AbsoluteSector(0,result)==Int13Status::WriteProtected);
    assert(disk->Write_Sector(head,cylinder,sector,result)==Int13Status::WriteProtected);
    delete disk;
    f=open(filename,O_RDONLY);assert(f>=0);
    assert(pread(f,result,512,0)==512);
    assert(!memcmp(result,mbr,512));
    assert(pread(f,result,512,(off_t)(sectors-1)*512)==512);
    assert(!memcmp(result,last,512));
    close(f);
    unlink(filename);
    puts("PASS A32: 2GB sparse RAW, BPB-derived CHS, high-LBA access, full write protection, FD lifetime");
}
