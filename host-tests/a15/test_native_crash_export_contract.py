#!/usr/bin/env python3
"""A15: source-level crash-export and native-breadcrumb contracts."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
activity=(root/"overlay/kotlin/WinKitSetupActivity.kt").read_text()
native=(root/"scripts/apply_a2_overlay.py").read_text()
for token in (
    "getHistoricalProcessExitReasons(packageName, 0, 32)",
    "ApplicationExitInfo.REASON_CRASH_NATIVE",
    "exit.traceInputStream?.use",
    'ZipEntry("native-tombstone.pb")',
    'ZipEntry("summary.txt")',
    "Intent.ACTION_CREATE_DOCUMENT",
    "if (requestCode == EXPORT_NATIVE_CRASH)",
    "Build.VERSION.SDK_INT < Build.VERSION_CODES.S",
):
    assert token in activity, token
for token in ("MANAGE_EXTERNAL_STORAGE","READ_LOGS","READ_EXTERNAL_STORAGE"):
    assert token not in activity, token
for n in (1,2,10,11,12,13,14):
    assert f"WinKit A15 stage {n:02d}:" in native, n
assert "winkit_launch::ValidatedLaunch run(" in native
assert "winkit_lease::ImageLease lease(" in native
assert "retrodos_host_run(5, argv)" in native
print("PASS A15 private native crash ZIP and native startup checkpoints")
