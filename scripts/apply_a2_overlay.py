#!/usr/bin/env python3
"""Apply WinKit A2 offline Android launcher to exact pinned Retro-DOS checkout.

No SDK needed to apply source. Run AFTER scripts/bootstrap_core.sh.
Never touches Mac project, active guest disks, or user-supplied exports.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
import re

APP_SHA = '65e8c67ed4d8e5aacabc4b96337e8d47ac92d720'
CORE_SHA = '4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11'
APP_ID = 'org.winkit.android.a2'
ROOT = Path(__file__).resolve().parents[1]

INSERT_AFTER = '''        if (games.empty() && retrodos::demo_prepare(demo_dir)) {'''
PATCH_POINT = '''    };
    refresh();

    enum class View'''
old_floppy_guard = """        if (!g.conf_override.empty()) {
            /* A machine's conf is its own, but the floppy drives on the"""
new_floppy_guard = """        if (!g.conf_override.empty() &&
            g.name != "WinKit 3.11 - YARDOMOB (OFFLINE)") {
            /* A machine's conf is its own, but the floppy drives on the"""

WINKIT_CPP = '''
#if defined(__ANDROID__)
        winkit_native_probe = "A13: SDL_GetAndroidInternalStoragePath returned null";
        // WinKit A2: independent, app-private DOS 6.22 / WFW 3.11 machine.
        // The setup Activity imports the disk and creates an offline config first.
        // Never silently launch, mount any Mac disk, or enable an Ethernet socket.
        if (const char *privateRoot = SDL_GetAndroidInternalStoragePath()) {
            const std::string machineRoot = std::string(privateRoot) + "/machines";
            const std::string diskPath = machineRoot + "/YARDOMOB.raw";
            const std::string configPath = machineRoot + "/YARDOMOB.offline.conf";
            const std::string readyPath = machineRoot + "/OFFLINE_READY";
            winkit_native_probe = "A13: OFFLINE_READY missing/invalid: " + readyPath;
            SDL_PathInfo diskInfo{};
            SDL_PathInfo profileInfo{};
            size_t markerBytes = 0, configBytes = 0;
            void *marker = SDL_LoadFile(readyPath.c_str(), &markerBytes);
            // If a selection was interrupted, no ready marker exists.
            const bool ready = marker && markerBytes == 11 &&
                std::memcmp(marker, "a2-offline\\n", 11) == 0;
            if (marker) SDL_free(marker);
            const bool diskOK = SDL_GetPathInfo(diskPath.c_str(), &diskInfo) &&
                diskInfo.type == SDL_PATHTYPE_FILE && diskInfo.size >= 512 &&
                (diskInfo.size % 512) == 0;
            const bool confOK = SDL_GetPathInfo(configPath.c_str(), &profileInfo) &&
                profileInfo.type == SDL_PATHTYPE_FILE &&
                profileInfo.size > 0 && profileInfo.size <= 262144;
            if (ready && !diskOK)
                winkit_native_probe = "A13: disk missing/invalid at SDL path: " + diskPath;
            else if (ready && !confOK)
                winkit_native_probe = "A13: offline.conf missing/invalid: " + configPath;
            else if (ready)
                winkit_native_probe = "A13: offline policy rejected config: " + configPath;
            if (ready && diskOK && confOK) {
                void *data = SDL_LoadFile(configPath.c_str(), &configBytes);
                if (data) {
                    std::string profile(static_cast<const char*>(data), configBytes);
                    SDL_free(data);
                    if (winkit_offline::boot_profile_is_offline(profile, diskPath)) {
                        Game w;
                        w.name = "WinKit 3.11 - YARDOMOB (OFFLINE)";
                        w.dir = machineRoot;
                        w.conf_override = std::move(profile);
                        w.is_machine = true;
                        w.keyboard_on_start = true;
                        w.initial = 'W';
                        games.push_back(std::move(w));
                        winkit_native_probe = "A13: native offline machine recognized";
                    }
                }
            }
        }
        winkit_a16_checkpoint("WinKit A16 stage 00: native discovery finished");
        SDL_Log("%s", winkit_native_probe.c_str());
#endif
'''
A16_CHECKPOINT_CPP = r'''
// A16: persistent, app-private, bounded startup checkpoints; never guest data.
#if defined(__ANDROID__)
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
static std::atomic<SDL_Window*> winkit_a17_window{nullptr};
static void winkit_a16_checkpoint(const char *stage) {
    SDL_Log("%s", stage);
    const char *root = SDL_GetAndroidInternalStoragePath();
    if (!root) return;
    const std::string file = std::string(root) + "/winkit-native-startup.log";
    const int fd = ::open(file.c_str(),
        O_WRONLY | O_APPEND | O_CREAT | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd < 0) return;
    struct stat info{};
    if (::fstat(fd, &info) != 0 || !S_ISREG(info.st_mode) ||
        info.st_nlink != 1 || info.st_size >= 262144) {
        ::close(fd);
        return;
    }
    const std::string line = std::to_string(::getpid()) + " " +
        std::to_string(SDL_GetTicks()) + "ms " + stage + "\n";
    if (::write(fd, line.data(), line.size()) ==
        static_cast<ssize_t>(line.size())) (void)::fsync(fd);
    ::close(fd);
}
extern "C" void winkit_a18_core_checkpoint(const char *stage) {
    winkit_a16_checkpoint(stage);
}
#endif
'''

def replace_exact(data: str, old: str, new: str, what: str) -> str:
    count = data.count(old)
    if count != 1:
        raise ValueError(f'Upstream {what} anchor count was {count}, expected 1. Stop; source changed.')
    return data.replace(old, new, 1)


def patch_frontend(data: str) -> str:
    if not data.startswith('/*\n * retro-dosbox frontend'):
        raise ValueError('Not the expected pinned frontend')
    if 'winkit_offline_gate.h' in data:
        raise ValueError('Frontend already patched; refusing second patch')
    if INSERT_AFTER not in data or data.count(PATCH_POINT) != 1:
        raise ValueError('Unrecognized upstream scan function')
    data = replace_exact(data, '#include "retrodos_host.h"',
                         '#include "retrodos_host.h"\n#include "winkit_offline_gate.h"\n#include "winkit_image_lease.h"\n#include "winkit_raw_geometry.h"\n#include "winkit_launch_snapshot.h"\n'
                         '#if defined(__ANDROID__)\n'
                         'static std::string winkit_native_probe = "A13: native disk scan has not run";\n'
                         '#endif', 'native include')
    data = replace_exact(data, PATCH_POINT, WINKIT_CPP + PATCH_POINT, 'refresh closure')
    data = replace_exact(data,
        'static std::string winkit_native_probe = "A13: native disk scan has not run";\n',
        'static std::string winkit_native_probe = "A13: native disk scan has not run";\n'
        + A16_CHECKPOINT_CPP, 'A16 persistent native checkpoints')
    # Machine-specific lock is on the worker thread for the full emulator lifetime.
    # A non-WinKit title must never be subject to this lease.
    engine_anchor = '    retrodos_host_set_window(nullptr);\n\n    char a0[]'
    engine_with_lease = '''    retrodos_host_set_window(nullptr);
#if defined(__ANDROID__)
    winkit_a16_checkpoint("WinKit A15 stage 10: engine thread entered");
    const char *privateRoot = SDL_GetAndroidInternalStoragePath();
    const std::string machineRoot = privateRoot ? std::string(privateRoot) + "/machines" : "";
    if (!machineRoot.empty() && workdir == machineRoot) {
        SDL_Window *borrowed = winkit_a17_window.load(std::memory_order_acquire);
        if (!borrowed) {
            winkit_a16_checkpoint("WinKit A17 blocked: missing SDL window");
            g_engine_done.store(true);
            return;
        }
        retrodos_host_set_window(borrowed);
        winkit_a16_checkpoint("WinKit A17: borrowed SDL window handed to core");
        winkit_lease::ImageLease lease(machineRoot + "/YARDOMOB.raw");
        if (!lease.acquired()) {
            LOGI("WinKit A3: guest image not safe/exclusively available; boot blocked");
            g_engine_done.store(true);
            return;
        }
        winkit_a16_checkpoint("WinKit A15 stage 11: disk lease acquired");
        // Recheck after obtaining the disk lease, before starting guest code.
        // The frontend profile was loaded earlier and may be stale if files changed.
        // `conf` is a FILE PATH, never pass it as raw profile contents.
        // Compare the generated launch config to the independently staged
        // offline config, then validate CHS and format under the disk lease.
        winkit_launch::ValidatedLaunch run(
            conf, machineRoot + "/YARDOMOB.offline.conf",
            machineRoot + "/YARDOMOB.raw", lease.descriptor(), lease.bytes());
        if (!run.valid()) {
            LOGI("WinKit A6: launch profile mismatch, disk geometry or raw container invalid; boot blocked");
            g_engine_done.store(true);
            return;
        }
        winkit_a16_checkpoint("WinKit A15 stage 12: launch snapshot validated");
        // No separate emulation window: the existing SDL3 render thread owns it.
        char a0[] = "dosbox-x";
        char a1[] = "-conf";
        char a3[] = "-defaultdir";
        std::vector<char> c(run.config_path().begin(), run.config_path().end()); c.push_back('\\0');
        std::vector<char> d(workdir.begin(), workdir.end()); d.push_back('\\0');
        char *argv[] = { a0, a1, c.data(), a3, d.data(), nullptr };
        retrodos_host_set_framebuffer_output(true);
        winkit_a16_checkpoint("WinKit A15 stage 13: entering DOSBox-X core");
        retrodos_host_run(5, argv);
        winkit_a16_checkpoint("WinKit A15 stage 14: DOSBox-X core returned");
        retrodos_host_set_window(nullptr);
        g_engine_done.store(true);
        return; // lease scope ends here, after core shutdown
    }
#endif
    char a0[]'''
    data = replace_exact(data, old_floppy_guard, new_floppy_guard, 'WinKit floppy exclusion')
    data = replace_exact(data, engine_anchor, engine_with_lease, 'engine lease')
    # The WinKit SetupActivity has already installed and approved the separate
    # app-private disk before MainActivity starts. The generic Retro-DOS wizard
    # is for choosing a games folder and should never intercept that boot.
    # Launch only the EXACT native-validated WinKit entry produced by refresh().
    autostart = '''#if defined(__ANDROID__)
    // WinKit A12: enter the independently validated offline machine directly.
    // Do not bypass the A3 image lease, A6 launch-profile gate or renderer.
    // The sole trusted library entry appears only after OFFLINE_READY,
    // raw-disk geometry and disabled-network config are verified.
    if (cfg.pending_launch.empty()) {
        for (const Game &candidate : games) {
            if (candidate.is_machine &&
                candidate.name == "WinKit 3.11 - YARDOMOB (OFFLINE)" &&
                candidate.dir == cfg_dir + "/machines" &&
                !candidate.conf_override.empty()) {
                winkit_a17_window.store(win, std::memory_order_release);
                winkit_a16_checkpoint("WinKit A17: frontend window recorded");
                winkit_a16_checkpoint("WinKit A15 stage 01: found validated YARDOMOB; calling launch");
                launch(candidate);
                winkit_a16_checkpoint("WinKit A15 stage 02: launch returned to frontend");
                break;
            }
        }
    }
    // An unready native guest must not silently fall into the generic wizard.
    // Report failure without modifying disks, profiles or networking.
    if (view == View::Wizard) {
        SDL_Log("WinKit A13 startup blocked: %s", winkit_native_probe.c_str());
        SDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_WARNING,
            "WinKit A13 - offline guest not started",
            winkit_native_probe.c_str(), win);
    }
#endif

'''
    return replace_exact(
        data,
        '    /* ---- Opening straight onto one screen, for the store captures --------',
        autostart + '    /* ---- Opening straight onto one screen, for the store captures --------',
        'WinKit A12 launch after lambda')


# A18: instrument ONLY the two pinned DOSBox-X startup files.
def patch_core_host(data: str) -> str:
    data = replace_exact(data,
        'extern "C" int dosbox_x_main(int argc, char *argv[]);',
        'extern "C" int dosbox_x_main(int argc, char *argv[]);\n'
        '#if defined(__ANDROID__)\n'
        'extern "C" void winkit_a18_core_checkpoint(const char *stage);\n'
        '#define WK_A18_HOST(s) winkit_a18_core_checkpoint(s)\n'
        '#else\n#define WK_A18_HOST(s) ((void)0)\n#endif',
        'A18 host declaration')
    return replace_exact(data,
        '    g_embedded.store(true);\n    g_running.store(true);\n'
        '    const int rc = dosbox_x_main(argc, argv);',
        '    g_embedded.store(true);\n    g_running.store(true);\n'
        '    WK_A18_HOST("WinKit A18 stage 20: before dosbox_x_main");\n'
        '    const int rc = dosbox_x_main(argc, argv);\n'
        '    WK_A18_HOST("WinKit A18 stage 29: dosbox_x_main returned");',
        'A18 host entry')


def patch_core_sdlmain(data: str) -> str:
    data = replace_exact(data,
        '#if defined(RETRODOS_HOST_PROVIDES_MAIN)\n'
        'int dosbox_x_engine_main(int argc, char* argv[]) SDL_MAIN_NOEXCEPT {',
        '#if defined(__ANDROID__) && defined(RETRODOS_HOST_PROVIDES_MAIN)\n'
        'extern "C" void winkit_a18_core_checkpoint(const char *stage);\n'
        '#define WK_A18_CORE(s) winkit_a18_core_checkpoint(s)\n'
        '#else\n#define WK_A18_CORE(s) ((void)0)\n#endif\n'
        '#if defined(RETRODOS_HOST_PROVIDES_MAIN)\n'
        'int dosbox_x_engine_main(int argc, char* argv[]) SDL_MAIN_NOEXCEPT {\n'
        '    WK_A18_CORE("WinKit A18 stage 21: engine main entered");',
        'A18 engine entry')
    data = replace_exact(data,
        '    CommandLine com_line(argc,argv);\n    Config myconf(&com_line);',
        '    CommandLine com_line(argc,argv);\n    Config myconf(&com_line);\n'
        '    WK_A18_CORE("WinKit A18 stage 22: config constructed");',
        'A18 config')
    data = replace_exact(data, '    if (!DOSBOX_parse_argv()) return 1;',
        '    WK_A18_CORE("WinKit A18 stage 23: before argv");\n'
        '    if (!DOSBOX_parse_argv()) return 1;\n'
        '    WK_A18_CORE("WinKit A18 stage 24: argv parsed");','A18 argv')
    data = replace_exact(data,
        '        /* -- SDL init */\n'
        '        if (SDL_Init(SDL_INIT_AUDIO|SDL_INIT_VIDEO|SDL_INIT_TIMER|SDL_INIT_NOPARACHUTE) >= 0)',
        '        /* -- SDL init */\n'
        '        WK_A18_CORE("WinKit A18 stage 25: before SDL init");\n'
        '        if (SDL_Init(SDL_INIT_AUDIO|SDL_INIT_VIDEO|SDL_INIT_TIMER|SDL_INIT_NOPARACHUTE) >= 0)',
        'A18 SDL start')
    data = replace_exact(data,
        '        else\n            E_Exit("Can\'t init SDL %s",SDL_GetError());',
        '        else\n            E_Exit("Can\'t init SDL %s",SDL_GetError());\n'
        '        WK_A18_CORE("WinKit A18 stage 26: SDL initialized");',
        'A18 SDL complete')
    data = replace_exact(data,
        '        /* Start up main machine */',
        '        WK_A18_CORE("WinKit A18 stage 27: starting machine");\n'
        '        /* Start up main machine */','A18 machine')
    data = replace_exact(data,
        '        MAPPER_StartUp();\n        DOSBOX_InitTickLoop();\n        DOSBOX_RealInit();',
        '        MAPPER_StartUp();\n        DOSBOX_InitTickLoop();\n        DOSBOX_RealInit();\n'
        '        WK_A18_CORE("WinKit A18 stage 28: real init returned");',
        'A18 after init')
    data = replace_exact(data,'            DOSBOX_RunMachine();',
        '            WK_A18_CORE("WinKit A18 stage 30: CPU loop entered");\n'
        '            DOSBOX_RunMachine();','A18 CPU loop')
    # A20: the exported entry wrapper is the gap between stages 20 and 21.
    # Block Android builds that would silently compile out the engine probes.
    return replace_exact(data,
        'extern "C" int dosbox_x_main(int argc, char *argv[]) {\n'
        '#if defined(RETRODOS_HOST_PROVIDES_MAIN)',
        '#if defined(__ANDROID__) && !defined(RETRODOS_HOST_PROVIDES_MAIN)\n'
        '#error "WinKit Android must compile the embedded DOSBox-X entry"\n'
        '#endif\n'
        'extern "C" int dosbox_x_main(int argc, char *argv[]) {\n'
        '    WK_A18_CORE("WinKit A20 stage 20A: DOSBox-X wrapper entered");\n'
        '#if defined(RETRODOS_HOST_PROVIDES_MAIN)',
        'A20 wrapper entry')


def patch_manifest(data: str) -> str:
    if 'org.winkit.android.a2' in data or 'WinKitSetupActivity' in data:
        raise ValueError('Manifest already patched')
    if '<manifest xmlns:android=' not in data:
        raise ValueError('Not an Android manifest')
    data = re.sub(r'^\s*<uses-permission android:name="android.permission.(?:INTERNET|ACCESS_NETWORK_STATE)" />\s*\n',
                  '\n', data, flags=re.M)
    data = replace_exact(data, 'android:allowBackup="true"', 'android:allowBackup="false"', 'backup policy')
    data = data.replace('android:label="Retro-DOS"', 'android:label="WinKit 3.11"')
    original_filter = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
'''
    data = replace_exact(data, original_filter, '', 'original launcher')
    setup = '''        <!-- Import and boot gate: the emulator is a separate SDLActivity. -->
        <activity
            android:name=".winkit.WinKitSetupActivity"
            android:exported="true"
            android:label="WinKit 3.11">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

'''
    data = replace_exact(data, '        <activity\n            android:name=".MainActivity"',
                         setup + '        <activity\n            android:name=".MainActivity"', 'SDLActivity')
    # Original MainActivity remains startable by this application's explicit Intent,
    # but external applications may not jump past the import gate.
    data = replace_exact(data, 'android:name=".MainActivity"\n            android:label="WinKit 3.11"\n            android:exported="true"',
                         'android:name=".MainActivity"\n            android:label="WinKit 3.11"\n            android:exported="false"', 'emulator activity export')
    return data


def patch_gradle(data: str) -> str:
    data = replace_exact(data, 'applicationId = "com.dosboxx.app"',
                         f'applicationId = "{APP_ID}"', 'application ID')
    # Android Gradle Plugin normally strips native symbols when packaging,
    # changing the .so bytes even if they came from the just-built JNI folder.
    # A10 debug acceptance compares SHA-256 of each packaged ELF with the
    # freshly compiled file, so preserve those exact ELF bytes. Future
    # optimized/release packaging can use a separate authenticated build-id
    # strategy; do not weaken this strong debug-build provenance gate.
    return replace_exact(data,
                         '            useLegacyPackaging = true',
                         '            useLegacyPackaging = true\n'
                         '            // A10: preserve exact native ELF bytes for APK SHA-256 validation.\n'
                         '            keepDebugSymbols += "**/*.so"',
                         'preserve native ELF bytes in debug APK')


def patch_build(data: str) -> str:
    # The Android frontend extends SDLActivity. A native SDL3 .so without the
    # matching SDL3.jar cannot compile into a working Android application.
    # SDL3 release-3.2.20 provides SDL3-jar via its CMake Android build.
    data = replace_exact(data,
                         '-DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF',
                         '-DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF ' +
                         chr(92) + '\n        -DSDL_ANDROID_JAR=ON',
                         'explicit SDL3 Android Java target')
    data = replace_exact(data,
                         '    cmake --build "$OUT/sdl3-build" -j"$JOBS"',
                         '    cmake --build "$OUT/sdl3-build" -j"$JOBS"\n'
                         '    cmake --build "$OUT/sdl3-build" --target SDL3-jar',
                         'build SDL3 Java activity target')
    # Upstream copies SDL3.jar only if it happens to exist, then the Gradle
    # build fails far later. Fail immediately and verify the expected class.
    end_marker = 'APP_LIBS="$HERE/app/libs"\n'
    gate = '''[[ -s "$OUT/SDL3.jar" ]] || { echo "error: SDL3.jar missing after SDL3 CMake build/install; check SDK platform/Java availability" >&2; exit 1; }
command -v jar >/dev/null || { echo "error: Java jar tool missing" >&2; exit 1; }
jar tf "$OUT/SDL3.jar" | grep -Fxq 'org/libsdl/app/SDLActivity.class' || {
    echo "error: SDL3.jar lacks org.libsdl.app.SDLActivity" >&2; exit 1;
}
'''
    data = replace_exact(data, end_marker, gate + end_marker, 'SDL3 Java verification gate')
    old = 'TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64"'
    new = '''# Select toolchain host directory, not target ABI. Darwin arm64 may need Rosetta.
case "$(uname -s)" in
  Darwin) NDK_HOST_TAG=darwin-x86_64 ;;
  Linux) NDK_HOST_TAG=linux-x86_64 ;;
  *) echo "unsupported build host" >&2; exit 2 ;;
esac
TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/$NDK_HOST_TAG"'''
    data = replace_exact(data,
        'CXXFLAGS="-fPIC -O2 -g0 -DANDROID"',
        'CXXFLAGS="-fPIC -O2 -g0 -DANDROID -DRETRODOS_HOST_PROVIDES_MAIN"',
        'A20 embedded DOSBox-X main macro')
    return replace_exact(data, old, new, 'NDK host tag')


def git_head(folder: Path) -> str:
    return subprocess.check_output(['git', '-C', str(folder), 'rev-parse', 'HEAD'], text=True).strip()


def apply(repo: Path):
    if git_head(repo) != APP_SHA or git_head(repo / 'core') != CORE_SHA:
        raise ValueError('Checkout does not exactly match pinned APP and CORE commits')
    if subprocess.check_output(['git', '-C', str(repo), 'status', '--porcelain'], text=True).strip():
        raise ValueError('Parent checkout contains tracked/untracked changes; use dedicated clean checkout')
    targets = {
        repo / 'frontend/retrodos_frontend.cpp': patch_frontend,
        repo / 'android/app/src/main/AndroidManifest.xml': patch_manifest,
        repo / 'android/app/build.gradle.kts': patch_gradle,
        repo / 'android/build-core.sh': patch_build,
        repo / 'core/src/gui/retrodos_host.cpp': patch_core_host,
        repo / 'core/src/gui/sdlmain.cpp': patch_core_sdlmain,
    }
    # Prepare all transforms before writing anything; fail closed on mismatch.
    changes = {path: transform(path.read_text()) for path, transform in targets.items()}
    dest_kotlin = repo / 'android/app/src/main/java/com/crownparkcomputing/retrodos/winkit'
    dest_native = repo / 'frontend/winkit_offline_gate.h'
    dest_lease = repo / 'frontend/winkit_image_lease.h'
    dest_raw = repo / 'frontend/winkit_raw_geometry.h'
    dest_snapshot = repo / 'frontend/winkit_launch_snapshot.h'
    if dest_kotlin.exists() or dest_native.exists() or dest_lease.exists() or dest_raw.exists() or dest_snapshot.exists():
        raise ValueError('Source overlay already present')
    dest_kotlin.mkdir(parents=True)
    for src in sorted((ROOT / 'overlay/kotlin').glob('*.kt')):
        shutil.copy2(src, dest_kotlin / src.name)
    shutil.copy2(ROOT / 'overlay/native/winkit_offline_gate.h', dest_native)
    shutil.copy2(ROOT / 'overlay/native/winkit_image_lease.h', dest_lease)
    shutil.copy2(ROOT / 'overlay/native/winkit_raw_geometry.h', dest_raw)
    shutil.copy2(ROOT / 'overlay/native/winkit_launch_snapshot.h', dest_snapshot)
    for path, content in changes.items():
        path.write_text(content)
    print('WIN KIT A3 SOURCE OVERLAY APPLIED:', repo)
    print('Native front-end machine entry + offline gating added (not compiled yet).')
    print('Application ID:', APP_ID, '- network permissions removed, backup disabled')
    print('No APK, native core or guest boot claimed until build and device tests pass.')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, default=ROOT / '_external/Retro-Dosbox')
    a = p.parse_args()
    apply(a.repo)

if __name__ == '__main__':
    main()
