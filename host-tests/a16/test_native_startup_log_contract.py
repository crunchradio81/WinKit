#!/usr/bin/env python3
"""A16 source regression: persistent checkpoints and optional tombstone."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
frontend=(root/"scripts/apply_a2_overlay.py").read_text()
activity=(root/"overlay/kotlin/WinKitSetupActivity.kt").read_text()
assert "A16_CHECKPOINT_CPP = r'''" in frontend
for token in ("O_APPEND | O_CREAT | O_CLOEXEC | O_NOFOLLOW",
              "S_ISREG(info.st_mode)", "info.st_nlink != 1",
              "info.st_size >= 262144", "static_cast<ssize_t>(line.size())",
              "::fsync(fd)", "winkit-native-startup.log"):
    assert token in frontend, token
for n in (0,1,2,10,11,12,13,14):
    label=f"WinKit A16 stage {n:02d}:" if n == 0 else f"WinKit A15 stage {n:02d}:"
    assert label in frontend, label
assert frontend.count("winkit_a16_checkpoint(") >= 9
for token in ("hasNativeTrace()", 'ZipEntry("native-startup.log")',
              'ZipEntry("summary.txt")', "exit?.traceInputStream?.use",
              "if (!hasNativeTrace() && latestNativeExit() == null)",
              "in 1L..262144L", "Intent.ACTION_CREATE_DOCUMENT"):
    assert token in activity, token
assert 'ZipEntry("YARDOMOB.raw")' not in activity
assert 'ZipEntry("YARDOMOB.offline.conf")' not in activity
print("PASS A16 optional tombstone + persistent private startup evidence")
