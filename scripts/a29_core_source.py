#!/usr/bin/env python3
"""A29: strictly validate a public GitHub source-recovery repository slug.

This tool does not fetch or trust alternate emulator code. bootstrap_core.sh must
still check out the precise Retro-DOS submodule gitlink SHA and verify it.
"""
import re
import sys

DEFAULT_REPOSITORY = "CrownParkComputing/dosbox-x-sdl3"
PINNED_CORE_SHA = "4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11"
PART = r"[A-Za-z0-9](?:[A-Za-z0-9_.-]{0,98}[A-Za-z0-9])?"
REPOSITORY = re.compile(rf"{PART}/{PART}\Z")


def source_url(repository: str) -> str:
    """Only literal owner/repository slugs; never tokens, schemes, redirects or shell syntax."""
    if not REPOSITORY.fullmatch(repository):
        raise ValueError("Expected public GitHub owner/repository (no URL, secrets or flags)")
    if any(part in {".", ".."} or part.endswith(".git") for part in repository.split("/")):
        raise ValueError("Invalid GitHub repository slug")
    return f"https://github.com/{repository}.git"


if __name__ == "__main__":
    try:
        if len(sys.argv) > 2:
            raise ValueError("One repository slug expected")
        print(source_url(sys.argv[1] if len(sys.argv) == 2 else DEFAULT_REPOSITORY))
    except ValueError as exc:
        print(f"A29 core source rejected: {exc}", file=sys.stderr)
        sys.exit(2)
