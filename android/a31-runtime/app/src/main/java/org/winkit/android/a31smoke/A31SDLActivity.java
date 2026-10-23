package org.winkit.android.a31smoke;

import org.libsdl.app.SDLActivity;

/**
 * Android SDL2 lifecycle smoke-test host.
 * Only tests whether the native emulator can be packaged and launched.
 * Guest image import and DOS/WFW 3.11 boot are not verified.
 */
public final class A31SDLActivity extends SDLActivity {
    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL2", "main"};
    }

    @Override
    protected String[] getArguments() {
        // Never mount user-supplied guest media implicitly.
        return new String[0];
    }
}
