// A6 actual compiled execution of the launch-time disk+profile snapshot gate.
// GPL-2.0-or-later. Run ONLY against a disposable synthetic fixture disk.
#include "winkit_launch_snapshot.h"
#include "winkit_image_lease.h"
#include <cstdlib>
#include <dirent.h>
#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

static int checks = 0;
#define CHECK(x) do { ++checks; if (!(x)) throw std::runtime_error(#x); } while (false)
static std::string load(const std::string& p) {
    std::ifstream in(p, std::ios::binary);
    return std::string(std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>());
}
static void save(const std::string& p, const std::string& s) {
    std::ofstream out(p, std::ios::binary | std::ios::trunc);
    if (!out.write(s.data(), static_cast<std::streamsize>(s.size()))) throw std::runtime_error("write");
}
int main(int argc, char** argv) {
    try {
        if (argc != 3) return 2;
        const std::string disk = argv[1], prepared = argv[2];
        const std::string machine = disk.substr(0, disk.find_last_of('/'));
        const std::string generated = machine + "/dosbox-x.conf";
        const auto orig = load(prepared);
        CHECK(!orig.empty());
        save(generated, orig);
        winkit_lease::ImageLease lease(disk);
        CHECK(lease.acquired());
        CHECK(lease.bytes() == 4128768);
        std::string contents;
        CHECK(winkit_launch::read_profile(generated, contents));
        CHECK(contents == orig);
        CHECK(!winkit_launch::read_profile(machine + "/not-a-config", contents));
        {
            winkit_launch::ValidatedLaunch run(generated, prepared, disk, lease.descriptor(), lease.bytes());
            CHECK(run.valid());
            CHECK(run.config_path() != generated);
            CHECK(run.config_path() != prepared);
            const std::string runtime_conf = load(run.config_path());
            CHECK(runtime_conf != orig);
            CHECK(runtime_conf.find("locking disk image mount=false") != std::string::npos);
            CHECK(runtime_conf.find("locking disk image mount=true") == std::string::npos);
            CHECK(load(prepared) == orig);
            CHECK(load(generated) == orig);
            struct stat st{};
            CHECK(::stat(run.config_path().c_str(), &st) == 0);
            CHECK((st.st_mode & 0777) == 0400);
            std::string persistent = run.config_path();
            {
                winkit_launch::ValidatedLaunch another(generated, prepared, disk,
                                                       lease.descriptor(), lease.bytes());
                CHECK(another.valid());
                CHECK(another.config_path() != persistent);
            }
            CHECK(::access(persistent.c_str(), F_OK) == 0);
        }
        // Inhibiting the redundant emulator lock must not release the frontend lease.
        CHECK(lease.acquired());
        {
            std::string rewritten;
            CHECK(winkit_launch::no_second_disk_lock(
                "[dosbox]\nlocking disk image mount=true\n[sdl]\noutput=gamelink\n", rewritten));
            CHECK(rewritten.find("locking disk image mount=true") == std::string::npos);
            CHECK(rewritten.find("locking disk image mount=false") != std::string::npos);
            CHECK(!winkit_launch::no_second_disk_lock("[sdl]\noutput=gamelink\n", rewritten));
        }
        // Both per-run snapshots must have been removed after their worker scopes.
        CHECK([](const std::string& dir) {
            DIR* d = ::opendir(dir.c_str());
            if (!d) return false;
            bool clear = true;
            while (dirent* e = ::readdir(d)) {
                if (std::string(e->d_name).find(".winkit-a6-run-") == 0) clear = false;
            }
            ::closedir(d);
            return clear;
        }(machine));
        save(generated, orig + "\n");
        CHECK(!winkit_launch::ValidatedLaunch(generated, prepared, disk, lease.descriptor(), lease.bytes()).valid());
        save(generated, orig + "\n[serial]\nserial1=modem\n");
        CHECK(!winkit_launch::ValidatedLaunch(generated, prepared, disk, lease.descriptor(), lease.bytes()).valid());
        save(generated, orig);
        const auto extra = orig.find("BOOT C:");
        CHECK(extra != std::string::npos);
        std::string floppy = orig;
        floppy.insert(extra, "IMGMOUNT A /tmp/foreign.img -t floppy\n");
        save(generated, floppy);
        CHECK(!winkit_launch::ValidatedLaunch(generated, prepared, disk, lease.descriptor(), lease.bytes()).valid());
        save(generated, orig);
        const auto symlink = machine + "/profile-symlink.conf";
        CHECK(::symlink(generated.c_str(), symlink.c_str()) == 0);
        CHECK(!winkit_launch::read_profile(symlink, contents));
        CHECK(!winkit_launch::ValidatedLaunch(symlink, prepared, disk, lease.descriptor(), lease.bytes()).valid());
        CHECK(::unlink(symlink.c_str()) == 0);
        const auto hardlink = machine + "/profile-hardlink.conf";
        CHECK(::link(generated.c_str(), hardlink.c_str()) == 0);
        CHECK(!winkit_launch::read_profile(hardlink, contents));
        CHECK(::unlink(hardlink.c_str()) == 0);
        save(generated, std::string(winkit_launch::MAX_PROFILE + 1, 'x'));
        CHECK(!winkit_launch::read_profile(generated, contents));
        save(generated, orig + std::string("\0evil",5));
        CHECK(!winkit_launch::read_profile(generated, contents));
        CHECK(!winkit_launch::ValidatedLaunch(generated, prepared, disk, lease.descriptor(), lease.bytes()).valid());
        save(generated, orig);
        CHECK(!winkit_launch::ValidatedLaunch(generated, prepared, disk, -1, lease.bytes()).valid());
        CHECK(!winkit_launch::ValidatedLaunch(generated, prepared, disk, lease.descriptor(), lease.bytes()+512).valid());
        CHECK(!winkit_launch::ValidatedLaunch(generated, machine + "/missing.conf", disk, lease.descriptor(), lease.bytes()).valid());
        CHECK(!winkit_launch::ValidatedLaunch(generated, prepared, machine + "/wrong.raw", lease.descriptor(), lease.bytes()).valid());
        // A path string is NOT config text: regression for the A5 blocker.
        uint64_t bad = 0;
        CHECK(!winkit_raw::geometry_bytes(generated, disk, bad));
        CHECK(winkit_raw::geometry_bytes(orig, disk, bad));
        CHECK(bad == lease.bytes());
        CHECK(::unlink(generated.c_str()) == 0);
        std::cout << "A6 native launch/snapshot assertions PASS: " << checks << "\n";
        return 0;
    } catch (const std::exception& ex) {
        std::cerr << "A6 FAIL after " << checks << ": " << ex.what() << "\n";
        return 1;
    }
}
