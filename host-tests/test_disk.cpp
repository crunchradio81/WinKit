#include "disk_image.h"
#include <cassert>
#include <cstring>
#include <fcntl.h>
#include <functional>
#include <stdexcept>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
using winkit::DiskImage;
static void expectFail(const std::function<void()>& fn) {
    bool threw = false;
    try { fn(); } catch(const std::exception&) { threw = true; }
    assert(threw);
}
int main() {
    char path[] = "/tmp/winkitA1XXXXXX";
    int fd = mkstemp(path);
    assert(fd >= 0);
    try {
        assert(ftruncate(fd, 512 * 4) == 0);
        close(fd);
        fd = -1;
        char symlinkPath[] = "/tmp/winkitA1linkXXXXXX";
        int linkTemp = mkstemp(symlinkPath);
        assert(linkTemp >= 0); close(linkTemp); unlink(symlinkPath);
        assert(symlink(path,symlinkPath) == 0);
        expectFail([&] { DiskImage tmp; tmp.openExclusive(symlinkPath, true); });
        unlink(symlinkPath);
        DiskImage disk;
        disk.openExclusive(path, true);
        assert(disk.bytes() == 2048);
        expectFail([&] { DiskImage second; second.openExclusive(path, true); });
        uint8_t in[512], out[512];
        memset(in,0xab,sizeof(in)); memset(out,0,sizeof(out));
        disk.writeSector(2,in);
        disk.readSector(2,out);
        assert(memcmp(in,out,sizeof(in)) == 0);
        expectFail([&] { disk.writeSector(4,in); });
        expectFail([&] { disk.readSector(4,out); });
        disk.flush();
        disk.close();
        DiskImage readonly;
        readonly.openExclusive(path, false);
        expectFail([&] { readonly.writeSector(0,in); });
        readonly.readSector(2,out);
        assert(memcmp(in,out,sizeof(in)) == 0);
        readonly.close();
        assert(truncate(path, 511) == 0);
        expectFail([&] { DiskImage bad; bad.openExclusive(path, false); });
    } catch (...) { if (fd >= 0) close(fd); unlink(path); throw; }
    unlink(path);
    return 0;
}
