#include "winkit_raw_geometry.h"
#include "winkit_image_lease.h"
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
static int checks = 0;
static void check(bool ok) { ++checks; if (!ok) { std::cerr << "FAIL check " << checks << "\n"; std::exit(1); } }
static std::string conf(const std::string& path, const std::string& geom="512,63,16,8") {
    return "[dosbox]\nmemsize=16\n[ne2000]\nne2000=false\n[ipx]\nipx=false\n[autoexec]\n"
           "IMGMOUNT 2 \""+path+"\" -t hdd -fs none -size "+geom+"\nBOOT C:\n";
}
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    std::string path=argv[1];
    const auto good=conf(path);
    uint64_t bytes=0;
    check(winkit_raw::geometry_bytes(good,path,bytes));
    check(bytes==512ull*63*16*8);
    check(!winkit_raw::geometry_bytes(conf(path,"512,63,16,7"),path,bytes) || bytes!=4128768);
    for(const char *g : {"1024,63,16,8", "512,64,16,8", "512,63,256,8", "512,63,16,65536", "0,63,16,8", "512,63,16,0"})
        check(!winkit_raw::geometry_bytes(conf(path,g),path,bytes));
    check(!winkit_raw::geometry_bytes(conf(path)+"ETHNET CONNECT 1.2.3.4\n",path,bytes));
    int fd=::open(path.c_str(),O_RDWR|O_CLOEXEC|O_NOFOLLOW);
    check(fd>=0);
    struct stat st{}; check(::fstat(fd,&st)==0);
    check(winkit_raw::image_matches_profile(good,path,fd,st.st_size));
    check(!winkit_raw::image_matches_profile(conf(path,"512,63,16,7"),path,fd,st.st_size));
    check(!winkit_raw::image_matches_profile(good,path,fd,st.st_size-512));
    auto mutated=[&](off_t off, const char* magic, size_t size) {
        unsigned char saved[8]{};
        check(::pread(fd,saved,size,off)==static_cast<ssize_t>(size));
        check(::pwrite(fd,magic,size,off)==static_cast<ssize_t>(size));
        check(!winkit_raw::image_matches_profile(good,path,fd,st.st_size));
        check(::pwrite(fd,saved,size,off)==static_cast<ssize_t>(size));
        check(winkit_raw::image_matches_profile(good,path,fd,st.st_size));
    };
    mutated(0,"QFI\xfb",4);
    mutated(0,"KDMV",4);
    mutated(0,"vhdxfile",8);
    mutated(0,"PK\003\004",4);
    mutated(0,"conectix",8);
    mutated(st.st_size-512,"conectix",8);
    ::close(fd);
    winkit_lease::ImageLease lease(path);
    check(lease.acquired());
    check(lease.bytes()==static_cast<uint64_t>(st.st_size));
    check(winkit_raw::image_matches_profile(good,path,lease.descriptor(),lease.bytes()));
    std::cout << "A5 C++ raw geometry + container: " << checks << " assertions passed\n";
}
