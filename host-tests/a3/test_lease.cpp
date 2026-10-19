#include "winkit_image_lease.h"
#include <cassert>
#include <cstdio>
#include <string>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
int main() {
    char t[] = "/tmp/winkit-a3-lease-XXXXXX";
    char *folder = mkdtemp(t); assert(folder);
    std::string base(folder);
    assert(::mkdir((base + "/machines").c_str(), 0700) == 0);
    std::string path = base + "/machines/YARDOMOB.raw";
    int fd = ::open(path.c_str(), O_CREAT | O_EXCL | O_RDWR | O_CLOEXEC, 0600);
    assert(fd >= 0); assert(::ftruncate(fd, 1024) == 0); ::close(fd);
    {
        winkit_lease::ImageLease first(path);
        assert(first.acquired());
        winkit_lease::ImageLease second(path);
        assert(!second.acquired());
        winkit_lease::ImageLease wrong(base + "/machines/OTHER.raw");
        assert(!wrong.acquired());
    }
    {
        winkit_lease::ImageLease third(path);
        assert(third.acquired());
    }
    std::string alias = base + "/machines/OTHER.raw";
    assert(::rename(path.c_str(), alias.c_str()) == 0);
    assert(::symlink(alias.c_str(), path.c_str()) == 0);
    winkit_lease::ImageLease symlink(path);
    assert(!symlink.acquired());
    ::unlink(path.c_str()); ::unlink(alias.c_str());
    ::rmdir((base + "/machines").c_str()); ::rmdir(base.c_str());
    std::puts("A3 image lease: exclusive, RAII release, filename, symlink checks PASS");
}
