#include <cstdio>
/* Host-only minimal stand-in for the verified pinned DOSBox-X imageDisk API.
 * Real Android compilation MUST use vendor/dosbox-x/include/bios_disk.h.
 * This tests behavior, not ABI compatibility. */
#pragma once
#include <cstdint>
#include <string>
enum class Int13Status : uint8_t {
    NoError=0, WriteProtected=3, SectorNotFound=4, ControllerFailure=0x20
};
class imageDisk {
public:
    enum IMAGE_TYPE { ID_BASE=0 };
    virtual ~imageDisk() = default;
    virtual Int13Status Read_Sector(uint32_t,uint32_t,uint32_t,void*,unsigned int=0) {
        return Int13Status::ControllerFailure;
    }
    virtual Int13Status Write_Sector(uint32_t,uint32_t,uint32_t,const void*,unsigned int=0) {
        return Int13Status::ControllerFailure;
    }
    virtual Int13Status Read_AbsoluteSector(uint32_t,void*) {
        return Int13Status::ControllerFailure;
    }
    virtual Int13Status Write_AbsoluteSector(uint32_t,const void*) {
        return Int13Status::ControllerFailure;
    }
    std::string diskname;
    uint64_t image_base=0, image_length=0, diskSizeK=0, LBA=0;
    uint32_t sector_size=512, heads=0, cylinders=0, sectors=0;
    bool hardDrive=false, active=false;
    FILE* diskimg=nullptr;
protected:
    explicit imageDisk(IMAGE_TYPE) {}
};
