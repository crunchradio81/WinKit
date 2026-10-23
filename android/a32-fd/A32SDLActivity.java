package org.winkit.android.a31smoke;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

/** A32 isolated read-only disk attachment attempt. Java NEVER mounts or copies image. */
public final class A32SDLActivity extends A31SDLActivity {
    private static native String nativeStageDiskFd(int borrowedFd, long bytes);

    @Override protected String testName() {
        return "A32 SAF BIOS/IDE hard disk (read only)";
    }

    // SDL2 calls this on SDLThread immediately before native SDL_main.
    // It loads libmain and stages a duplicated SAF descriptor before BIOS init.
    @Override protected String[] getArguments() {
        Uri selected = A31GuestDisk.selectedUri(this);
        if (selected == null) {
            A31DiagnosticLog.record(this, "A32 disk STAGE SKIPPED: select YARDOMOB.raw first");
            return new String[0];
        }
        try (ParcelFileDescriptor pfd =
                 getContentResolver().openFileDescriptor(selected, "r")) {
            if (pfd == null) throw new java.io.IOException("SAF did not return a descriptor");
            long size = pfd.getStatSize();
            if (size <= 0 || (size & 511L) != 0)
                throw new java.io.IOException("Invalid or unavailable sector-aligned size: " + size);
            System.loadLibrary("main");
            String result = nativeStageDiskFd(pfd.getFd(), size);
            A31DiagnosticLog.record(this, result);
        } catch (java.io.IOException | SecurityException | UnsatisfiedLinkError error) {
            A31DiagnosticLog.record(this, "A32 native disk staging FAIL: "
                + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        // First build does not auto-BOOT. It probes BIOS attachment while launching
        // DOSBox-X's shell; explicit DOS guest boot is a separate acceptance stage.
        return new String[0];
    }
}
