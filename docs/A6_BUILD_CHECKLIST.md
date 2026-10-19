# A6 build / device acceptance gates

1. Checkout/clone pinned Retro-DOS parent `65e8c67ed4d8e5aacabc4b96337e8d47ac92d720`
   and nested DOSBox-X submodule `4167c3aca0953e8b2af8ee3b63c94e6bdb2cfe11` into
   `_external` (the build script does this only if the destination is absent).
2. Apply A6 overlay only to a **clean, exact pinned** checkout; do not reuse an A5
   patched `_external` tree. The script refuses that state.
3. Compile SDL3/ImGui DOSBox-X with NDK r28 for `arm64-v8a`, API 28.
4. Check AArch64 ELF, native retrodos host ABI, embedded A6 signature,
   packaged SDL3 and correct app ID/permissions. **No step is marked achieved
   without completed tool exit 0 and the output file.**
5. Install debug APK on Android hardware (when possible). Use GPL BIOS fixture
   first. Assert the full boot banner **actually appears** on the rendered screen.
6. After successful local synthetic boot, test a private exported HDD while
   preserving all Mac originals; then configure unique guest name and MAC.
7. Only after local boot is reliable proceed to physical LAN interoperability,
   NetBEUI and Tailscale.
