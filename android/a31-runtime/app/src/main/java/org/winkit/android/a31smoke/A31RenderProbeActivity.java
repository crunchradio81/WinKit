package org.winkit.android.a31smoke;

/** Native SDL2 color bars without DOSBox-X or guest media. */
public final class A31RenderProbeActivity extends A31SDLActivity {
    @Override protected String[] getLibraries() { return new String[]{"SDL2","probe"}; }
    @Override protected String testName() { return "native SDL2 color"; }
    private static native int probeStage();
    private static native int probeFrames();
    @Override protected String extraNativeStatus() {
        try {
            return "native stage=" + probeStage() + ", frames=" + probeFrames();
        } catch (UnsatisfiedLinkError ex) {
            return "native probe not loaded: " + ex.getMessage();
        }
    }
}
