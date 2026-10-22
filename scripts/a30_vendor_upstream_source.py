#!/usr/bin/env python3
"""Vendoring of pinned public DOSBox-X source only; no media or APK."""
import argparse, hashlib, json, pathlib, subprocess

PIN = "029bd65c5dcc7cdc7f2e14e35278620d9707d969"
SOURCE = "joncampbell123/dosbox-x"
DEST = pathlib.Path("vendor/dosbox-x")
ROOT_ALLOWED = {"COPYING", "AUTHORS", "CREDITS.md", "THANKS", "README.md",
                "BUILD.md", "INSTALL.md", "README.source-code-description",
                "Makefile.am", "configure.ac", "acinclude.m4", "autogen.sh"}
EXT_ALLOWED = {".c", ".cc", ".cpp", ".cxx", ".h", ".hh", ".hpp", ".hxx",
               ".s", ".asm", ".inc", ".inl", ".m", ".mm", ".in", ".am", ".ac",
               ".m4", ".def", ".rc", ".sh", ".py", ".pl", ".txt", ".md",
               ".rst", ".cfg", ".conf", ".ini", ".json", ".yml", ".yaml",
               ".cmake", ".mk", ".mak", ".bat"}
NAME_ALLOWED = {"Makefile", "Makefile.am", "Makefile.in", "makefile",
                "configure", "configure.ac", "COPYING", "LICENSE", "AUTHORS", "README"}
EXCLUDED_EXT = {".bin", ".exe", ".dll", ".so", ".a", ".o", ".obj", ".7z",
                ".zip", ".tar", ".gz", ".iso", ".img", ".raw", ".vhd", ".rom",
                ".pak", ".dat", ".png", ".jpg", ".jpeg", ".bmp", ".gif",
                ".ico", ".ttf", ".otf", ".wav", ".mp3", ".ogg", ".mp4",
                ".pdf", ".chm", ".html", ".htm", ".svg"}

def git(folder, *args):
    return subprocess.check_output(["git", "-C", str(folder), *args])

def allowed(name):
    p = pathlib.PurePosixPath(name)
    if p.is_absolute() or ".." in p.parts or ".git" in p.parts:
        return False
    if name in ROOT_ALLOWED:
        return True
    if not name.startswith(("src/", "include/", "tests/")):
        return False
    return p.suffix.lower() not in EXCLUDED_EXT and (
        p.suffix in EXT_ALLOWED or p.name in NAME_ALLOWED)

def run(upstream, winkit):
    if git(upstream, "rev-parse", "HEAD").decode().strip() != PIN:
        raise ValueError("Upstream commit differs from pinned Git revision")
    if not (winkit / "MANIFEST.sha256").is_file():
        raise ValueError("WinKit manifest missing")
    vendor = winkit / DEST
    if vendor.exists() or vendor.is_symlink():
        raise ValueError("Source directory already exists; no overwrite")
    records = git(upstream, "ls-tree", "-r", "-z", "HEAD").split(b"\0")
    chosen = []
    for record in records:
        if not record:
            continue
        meta, tab, rawname = record.partition(b"\t")
        if not tab:
            raise ValueError("Invalid Git record")
        mode, kind, sha = meta.decode("ascii").split()
        name = rawname.decode("utf-8", "surrogateescape")
        if not allowed(name):
            continue
        if kind != "blob" or mode not in ("100644", "100755"):
            raise ValueError("Unexpected symlink or gitlink: " + name)
        chosen.append((name, sha, mode))
    if len(chosen) < 900 or not {"COPYING", "configure.ac", "src/dosbox.cpp"}.issubset(
        {entry[0] for entry in chosen}
    ):
        raise ValueError("Incomplete audited upstream source")
    import shutil
    vendor.mkdir(parents=True)
    hashes = []
    byte_count = 0
    try:
        for name, sha, mode in chosen:
            body = git(upstream, "cat-file", "blob", sha)
            if len(body) > 15 * 1024 * 1024:
                raise ValueError("Oversized source requires separate review: " + name)
            file = vendor / name
            file.parent.mkdir(parents=True, exist_ok=True)
            with file.open("xb") as output:
                output.write(body)
            if mode == "100755":
                file.chmod(0o755)
            byte_count += len(body)
            hashes.append((str(DEST / name), hashlib.sha256(body).hexdigest()))
        if byte_count > 120 * 1024 * 1024:
            raise ValueError("Unexpected source footprint")
        provenance = {
            "status": "source-owned-not-built",
            "repository": SOURCE, "revision": PIN,
            "original_git_tree": git(upstream, "rev-parse", "HEAD^{tree}").decode().strip(),
            "included_files": len(chosen), "included_bytes": byte_count,
            "license_notice": "COPYING and original inline notices",
            "selection": "src/include/tests text source and selected build metadata; exclude binary media and ROMs",
            "retrodos_host_bridge_present": False,
            "android_native_build_verified": False,
            "guest_boot_verified": False,
        }
        note = (
            "# A30 pinned upstream DOSBox-X source\n\n"
            "Source: https://github.com/joncampbell123/dosbox-x\n"
            "Pinned commit: " + PIN + "\n"
            "See COPYING and original notices for redistribution terms.\n"
            "This is not CrownParkComputing's customized SDL3 host fork.\n"
            "ROMs, prebuilt toolchains and third-party media were excluded.\n"
            "The custom Android host bridge and keyboard handling still require a port.\n"
        )
        artifacts = {"WINKIT_SOURCE_IMPORT.json": json.dumps(provenance, sort_keys=True, indent=2) + "\n",
                     "WINKIT_UPSTREAM_SOURCE.md": note}
        for name, payload in artifacts.items():
            (vendor / name).write_text(payload)
            hashes.append((str(DEST / name), hashlib.sha256(payload.encode()).hexdigest()))
        manifest = winkit / "MANIFEST.sha256"
        old = manifest.read_text().splitlines()
        existing = {line.split("  ", 1)[1] for line in old if "  " in line}
        if existing.intersection(name for name, _ in hashes):
            raise ValueError("Manifest collision; refusing")
        rows = old + [digest + "  " + name for name, digest in hashes]
        manifest.write_text("\n".join(sorted(rows, key=lambda line: line.split("  ", 1)[1])) + "\n")
        print(json.dumps(provenance, indent=2, sort_keys=True))
        print("Source imported, not Android-compiled or guest-booted.")
    except Exception:
        shutil.rmtree(vendor)
        raise

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", required=True, type=pathlib.Path)
    parser.add_argument("--winkit", required=True, type=pathlib.Path)
    args = parser.parse_args()
    run(args.upstream, args.winkit)
