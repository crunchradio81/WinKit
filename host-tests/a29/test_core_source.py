#!/usr/bin/env python3
"""Offline tests for the A29 immutable-core source recovery contract."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "a29_core_source.py"
SPEC = importlib.util.spec_from_file_location("a29_core_source", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class TestPinnedCoreRecovery(unittest.TestCase):
    def test_default_url_and_commit(self):
        self.assertEqual(
            MODULE.source_url(MODULE.DEFAULT_REPOSITORY),
            "https://github.com/CrownParkComputing/dosbox-x-sdl3.git",
        )
        self.assertEqual(MODULE.PINNED_CORE_SHA, "4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11")

    def test_authorized_public_mirror_format(self):
        self.assertEqual(
            MODULE.source_url("crunchradio81/winkit-dosbox-x-sdl3-source"),
            "https://github.com/crunchradio81/winkit-dosbox-x-sdl3-source.git",
        )

    def test_reject_unsafe_url_or_shell_syntax(self):
        for slug in (
            "", "owner", "owner/repo/other", "../repo", "owner/..",
            "https://github.com/owner/repo", "owner/repo.git", "owner/repo@token",
            "owner/repo?ref=latest", "owner/repo;echo injected",
            "owner/repo\\ninvalid", "-owner/repo", "owner/-repo",
            "owner/with space", "owner/.git", "owner/.hidden",
        ):
            with self.subTest(repository=slug):
                with self.assertRaises(ValueError):
                    MODULE.source_url(slug)

    def test_bootstrap_preserves_gitlink_and_exact_sha(self):
        bootstrap = (ROOT / "scripts" / "bootstrap_core.sh").read_text()
        self.assertIn('CORE_URL="$(python3 "$ROOT/scripts/a29_core_source.py"', bootstrap)
        self.assertIn('git -C "$DEST" config submodule.core.url "$CORE_URL"', bootstrap)
        self.assertIn('git -C "$DEST" submodule update --init --recursive', bootstrap)
        self.assertIn('[ "$ACTUAL_CORE" = "$CORE_SHA" ]', bootstrap)
        self.assertIn("4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11", bootstrap)
        self.assertNotIn("git checkout --force", bootstrap)


if __name__ == "__main__":
    unittest.main(verbosity=2)
