# A32 native imageDisk integration checkpoint

This branch adds a read-only Android SAF descriptor sector reader and a DOSBox-X `imageDisk` subclass. This is **source-stage integration**, not an APK and not a verified boot.

## Verified in source
- `vendor/dosbox-x/include/bios_disk.h` declares `imageDisk(IMAGE_TYPE)` protected, and virtual `Read_AbsoluteSector`, `Write_AbsoluteSector`, `Read_Sector`, `Write_Sector`.
- `WinKitA32ReadOnlyImage` uses the inherited protected base constructor and overrides these methods.
- Open denies writable descriptors; ownership is via `dup()`; sector reads via `pread()`; all guest writes return `Int13Status::WriteProtected`.
- Existing `YARDOMOB.raw` is not copied or changed by this code.

## Remaining to attempt real guest startup
1. Integrate `winkit_a32_image_disk.cpp` into native DOSBox-X compile and include `android/a32-fd` in the C++ header search path. Build and resolve native compiler/linker errors first.
2. From the SDL activity, open persisted SAF URI in `r` mode and transfer a *borrowed* integer descriptor to native code. The resulting imageDisk object owns its own `dup` and must remain alive until DOSBox-X releases it. Never use `/proc/self/fd`, `fdopen`, or `fopen` for Android guest disk input.
3. Attach returned `imageDisk*` using the existing DOSBox-X BIOS/IDE attachment mechanism and honor its Addref/Release lifetime contract. Do **not** use arbitrary global pointer assignment or bypass disk setup.
4. Confirm disk attachment and first BIOS INT 13h read, then attempt boot. Record SDLThread, `SDL_main`, disk attachment, read success, and exit/crash reason.
5. Guest OS normally writes while booting. Read-only backend intentionally rejects writes; a complete DOS boot may not be possible until a carefully bounded copy-on-write overlay is available.
6. Only after a complete link and emulator startup should a new A32 APK be published as **NOT BOOT VERIFIED**; mark boot verified only from device evidence.

## Known outstanding risks
- A32 subclass currently sets conventional translated 16/63 CHS with cylinder cap 1024. Validate guest BPB/geometry against upstream's attach procedure before boot.
- Some DOSBox-X code paths may require a non-null `diskimg` FILE pointer or specific `class_id`, and must be audited for the subclass.
- The existing A31 Android runtime bundles a prebuilt `libmain.so`: the subclass must be compiled and linked into a new emulator build; merely repackaging `libbridge.so` is insufficient.
- Running a read-only native probe demonstrates descriptor access only, not DOSBox-X bootability.
