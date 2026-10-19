#!/data/data/com.termux/files/usr/bin/bash
# Read only: no pkg install, no changes to emulator/device settings.
set -u
printf 'WinKit 3.11 Android read-only precheck\n'
printf 'Date: '; date
printf 'Android SDK: '; (getprop ro.build.version.sdk || true)
printf 'Android release: '; (getprop ro.build.version.release || true)
printf 'ABI(s): '; (getprop ro.product.cpu.abilist || true)
printf 'Model: '; (getprop ro.product.model || true)
printf 'Kernel: '; (uname -a || true)
printf '\nFree space:\n'; (df -h "$HOME" 2>/dev/null || true)
printf '\nDetected commands:\n'
for c in python python3 7zz sha256sum dosbox-x termux-x11 git; do
  printf '%-18s: ' "$c"
  (command -v "$c" || echo 'not installed')
done
printf '\nNo emulator installed, guest images mounted, or networking changed.\n'
