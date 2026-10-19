# Independent guest disk boot, correctness and identity gates

## Immutable Mac input

The Mac reference is WinKit RC24.3 with YARDO and YARDO-B on locally-verified working clones. Do NOT mount/copy images from a running Mac virtual PC. Preserve all original `.raw` files and backups. Export one stopped `*-WORKING.raw` using the included `reference/RD01` guard tool to a *private* directory on Mac. Its `manifest.json` includes `image_bytes`, SHA-256, `guest_name_already_changed=false`, `network_safe_to_connect=false`.

## On Android (A1 code path)

1. User chooses a previously exported, **independent** `.raw` through Android's document picker; no raw guest images bundled in APK or ZIP.
2. Stream content into a random `.partial` file beneath `filesDir/machines/`. Compute SHA256, check 512-byte sector alignment and optionally manifest length in `GuestStore` (UI in A1 asks for SHA only). Flush filesystem buffers; reject mismatch and delete partial.
3. Rename same-filesystem temp to `YARDOMOB.raw` **without replacing any existing installed image**. Future work: adopt manifest picker, resumable journal/cancellation, restaging free-space estimate, atomic durable directory metadata, explicit instance management.
4. Device-persistent locally administered unicast MAC is created in preferences (`GuestIdentity`). Compare against both Mac guest MACs before enabling LAN; entropy alone doesn't prove no collision. Mac guest's *copied Windows computer name* remains duplicated until changed inside guest.
5. Native `DiskImage` uses `open(..., O_NOFOLLOW)`, regular-file + sector checks, `flock(LOCK_EX|LOCK_NB)` before any sector read/write, bounds-checked `pread`/`pwrite`. JNI `probe()` opens/reads size and closes; this is **not** an emulator device controller. Future DOSBox-X integration must retain open handle throughout its entire boot, unmount and shutdown sequence, and must not allow another writer.

## Offline first boot: planned native core configuration

Using the Mac working guest's actual, user-provided `.conf`:

```bash
python3 scripts/prepare_offline_conf.py \
 --mac-conf /private/path/to/VERIFIED-MAC-GUEST.conf \
 --original-image-path '/private/path/to/YARDO-B-WORKING.raw' \
 --android-image-path '/data/user/0/org.winkit.android.a1/files/machines/YARDOMOB.raw' \
 --out /tmp/YARDOMOB-offline.conf
```

The command creates a new config, never alters input. It preserves CHS, controller, memory, machine/S3, audio, and all other existing config sections instead of inventing values. It replaces exactly one matching Mac HDD pathname with the Android app-private path, forces `ne2000=false`, removes explicit `ETHNET` autoexec commands and sets `working directory option=noprompt` in `[dosbox]`. **Still inspect every copied host-path mount and DOSBox-X option**: a Mac working config may refer to additional host media or unsupported Android functionality. It is not a guarantee of successful Android boot.

Per pinned core host API the actual integration launch is `retrodos_host_run(argc, argv)` with `-conf <private generated conf>`, on a dedicated emulator thread while SDL3 window resides in same process. `retrodos_host_quit()` requests safe stop and frame/input interfaces support host rendering. This function **has not been connected to this A1 scaffold**, the core not built, no boot demonstrated.

## Identity before networking

- Offline boot and obtain Windows for Workgroups Program Manager with `ne2000=false` and no ETHNET sockets.
- In WFW Network Setup rename the copied computer from `YARDO` or `YARDO-B` to `YARDOMOB`; reboot and verify. Preserve Windows network workgroup. Need Windows evidence, not a host preference string.
- Ensure the emulated NIC's actual MAC becomes the Android-persistent generated LAA and differs from both existing Macs, NIC I/O/IRQ matches installed Windows NDIS driver.
- ONLY after guest identity is checked, deliberately activate the network backend. No automatic attempt by A1 to connect to Mac over Tailscale.
- Keep all guest OS disk writes LOCAL to Android. Back up image only after clean shutdown and out of the emulator process.
