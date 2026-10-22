#!/usr/bin/env python3
"""Inspect a pinned, editable public DOSBox-X Git checkout without mutating it.

This is *not* a compatibility or guest-boot test. Fail clearly when the
expected upstream source tree is missing. A30 is isolated from A26 and A29.
"""
import argparse
import json
import subprocess
from pathlib import Path

UPSTREAM = "joncampbell123/dosbox-x"
PINNED_SHA = "029bd65c5dcc7cdc7f2e14e35278620d9707d969"
NEEDED = ("src", "include", "configure.ac", "COPYING", "src/gui")
HOST_HEADER = "include/retrodos_host.h"


def git(folder, *args):
    return subprocess.check_output(
        ["git", "-C", str(folder), *args], stderr=subprocess.PIPE
    ).decode("utf-8", "replace").strip()


def audit(folder: Path):
    revision = git(folder, "rev-parse", "HEAD")
    if revision != PINNED_SHA:
        raise RuntimeError(f"Wrong upstream SHA: {revision}; expected {PINNED_SHA}")
    names = git(folder, "ls-tree", "-r", "--name-only", "HEAD").splitlines()
    existing = set(names)
    for required in NEEDED:
        if required not in existing and not any(x.startswith(required + "/") for x in names):
            raise RuntimeError(f"Missing upstream source component: {required}")
    # The upstream's source and license are real, but it is NOT a drop-in
    # replacement for Crown Park's customized Android host ABI.
    configure = git(folder, "show", "HEAD:configure.ac")
    if "enable_sdl3" not in configure:
        raise RuntimeError("Pinned upstream source lacks SDL3 configure option")
    license_text = git(folder, "show", "HEAD:COPYING")
    if "GNU GENERAL PUBLIC LICENSE" not in license_text:
        raise RuntimeError("Unexpected upstream license document")
    host_header = HOST_HEADER in existing
    if host_header:
        raise RuntimeError("Upstream unexpectedly contains Retro-DOS host API; re-audit changes")
    # ls-tree -rl exposes Git blob sizes without requiring the full working tree.
    raw = git(folder, "ls-tree", "-rl", "HEAD").splitlines()
    blob_count = 0
    known_bytes = 0
    oversized = []
    symlinks = []
    gitlinks = []
    for line in raw:
        metadata, sep, path = line.partition("\t")
        if not sep:
            continue
        parts = metadata.split()
        if len(parts) != 4:
            continue
        mode, kind, _sha, size = parts
        if mode == "120000":
            symlinks.append(path)
        if mode == "160000":
            gitlinks.append(path)
        if kind == "blob" and size != "-":
            blob_count += 1
            byte_count = int(size)
            known_bytes += byte_count
            if byte_count > 100 * 1024 * 1024:
                oversized.append(path)
    report = {
        "status": "source-tree-verified-not-port-compiled",
        "source": UPSTREAM,
        "revision": revision,
        "license": "GNU GPL (see upstream COPYING and individual files)",
        "tracked_paths": len(names),
        "blob_count": blob_count,
        "blob_bytes_estimate": known_bytes,
        "largest_object_exceeds_github_100mib": oversized,
        "symlinks": symlinks,
        "nested_gitlinks": gitlinks,
        "configure_mentions_sdl3": True,
        "retrodos_host_header_present": host_header,
        "original_pinned_custom_core": "4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11",
        "source_compatibility": "NOT-COMPATIBLE-AS-IS",
        "next_tasks": [
            "Review source footprint and third-party redistribution obligations",
            "Import reviewed source into isolated A30 WinKit branch",
            "Implement Retro-DOS C host bridge on top of pinned upstream SDL3",
            "Port Android NDK compile integration and WinKit Kbd/lifecycle checkpoints",
            "Demonstrate synthetic guest BIOS before YARDOMOB Mac disk"
        ],
    }
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkout", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = audit(args.checkout)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n")
    print("PASS A30 exact public upstream source:", report["revision"])
    print("Git-tracked paths:", report["tracked_paths"], "| blob bytes:", report["blob_bytes_estimate"])
    print("SDL3 option present; customized retrodos_host API MISSING; native port required.")
    print("No emulator build, APK, or Windows guest boot has been performed.")


if __name__ == "__main__":
    main()
