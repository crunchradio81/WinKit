# Later A5 Android smoke acceptance (NOT yet run)

This procedure is **Android-local**. No Mac emulator, Tailscale, DOS, Windows,
NetBEUI, network-sharing configuration, or original guest hard disk is needed.
The source package does **not** contain an APK; build gates must complete first.

1. On a dedicated Linux x86_64 development host with Android SDK API 36,
   NDK 28.2.13676358, Java 17, Gradle 8.9 and public upstream access:
   `bash scripts/BUILD_A5_ANDROID.sh`.
2. Only if it reports success and creates `_build_outputs/WinKit311-Android-A5-debug-DEVICE-UNVERIFIED.apk`:
   `adb install -r _build_outputs/WinKit311-Android-A5-debug-DEVICE-UNVERIFIED.apk`.
3. Launch **WinKit 3.11** on the Android device, with a pristine app-data area.
   Tap **Install GPL BIOS boot smoke test (NO DOS/Windows)**. Wait for the
   completion message, inspect the generated offline config, tick the offline
   acknowledgement box, and tap **Open LOCAL emulation library (offline)**.
4. Choose **WinKit 3.11 - YARDOMOB (OFFLINE)** in the SDL3 emulator's library.
   Success B5 = visibly seeing `WINKIT A4 BIOS HDD BOOT OK - NO MICROSOFT FILES`
   drawn by BIOS INT 10h from the emulated guest's HDD boot sector.
5. Record build SHA256, installed Android version, device model and SOC/ABI,
   cold-start launch time, a screenshot, any native crash log and a clean
   emulator quit. If banner is not visible, capture Android logs before
   changing the implementation. A .so or APK existing is NOT proof of boot.
6. Use a separate Android application data reset / clean install before
   importing a *real* stopped, independently exported, user-owned DOS/Windows
   disk: diagnostic fixture installation never overwrites or converts that disk.

Optional diagnostics after launch:

```sh
adb shell getprop ro.product.cpu.abi
adb logcat -d -v time -s retrodos AndroidRuntime SDL3 > a5-device-log.txt
adb shell screencap -p /sdcard/a5-proof.png
adb pull /sdcard/a5-proof.png
```

A4/A5 build-stage SHA and APK manifest checks are host gates only. Actual x86
BIOS execution, framebuffer, input, clean shutdown and Windows guest boot need
subsequent observations before any claim of success.

Network remains disabled deliberately until later identity and ETHNET audits.
The private disk is always independent; none of these instructions involve
accessing the Mac guest, Mac RC24.3 or its backup drives.
