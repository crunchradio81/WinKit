# Publish A10 directly from Android (no Mac computer needed)

The connected public repository is https://github.com/crunchradio81/WinKit.
Its `main` branch has only a status README and safe `.gitignore`; the complete
A10 source **has not been uploaded** by the assistant. Do not start GitHub
Actions until the source branch is pushed.

## Termux upload route

Download the A10 ZIP into Android Downloads, open Termux, and install Git and
GitHub CLI (if available in your Termux repositories):

```sh
pkg update
pkg install git gh unzip
```

Authenticate **interactively with your own GitHub account** (never paste an
access token into chat or an uploaded archive):

```sh
gh auth login
gh auth setup-git
```

Make sure Termux can read the downloaded ZIP. If storage access is needed,
run `termux-setup-storage` and grant permission. Navigate to the download
location (`~/storage/downloads` is commonly available after setup), then:

```sh
unzip WinKit311-Android-A10-GitHub-Ready-NoMac-2026-10-19.zip
cd WinKit311-Android-A10
bash scripts/PUBLISH_A10_GITHUB.sh
```

The script verifies checksums, clones only `crunchradio81/WinKit`, copies
source files, checks the staged Git index, and pushes a **new** branch named
`android-a10-source` without merging or overwriting `main`. It refuses an
existing remote branch and refuses unsafe image/binary file extensions.

If Git asks for identity configuration, set your own Git author display name
and email through `git config --global user.name` and `user.email`, then retry.
The repository remains unchanged if push authentication fails.

Review the source at:
https://github.com/crunchradio81/WinKit/compare/main...android-a10-source

After review and merge (or by explicitly choosing that source branch in the
Actions interface), open **Actions** → **WinKit A10 Android ARM64 Build** →
**Run workflow**. A compiled APK artifact, if the workflow succeeds, is
**DEVICE-UNVERIFIED** until separately installed and boot-tested.

## Safety reminders

Do not copy/export live guest disks, Microsoft installation images, keys,
backups, private Tailscale configuration, or Mac RC24.3 into the source tree.
No remote desktop/VNC or real NetBEUI link is part of the A10 build.
