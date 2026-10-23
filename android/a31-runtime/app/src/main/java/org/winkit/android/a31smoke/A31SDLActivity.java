package org.winkit.android.a31smoke;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.libsdl.app.SDLActivity;

/** Lifecycle trace only; DO NOT equate onResume with successful DOS boot. */
public final class A31SDLActivity extends SDLActivity {
    private TextView overlay;

    @Override
    protected String[] getLibraries() {
        return new String[]{"SDL2", "main"};
    }

    @Override
    protected String[] getArguments() {
        return new String[0];
    }

    @Override
    protected void onCreate(Bundle state) {
        A31DiagnosticLog.record(this, "SDL Activity onCreate BEFORE native SDL initialization");
        super.onCreate(state);
        A31DiagnosticLog.record(this, "SDL Activity onCreate AFTER native SDL initialization");
        // Small Java overlay: if it appears above black SDL output, the Java
        // activity is alive, but it does NOT prove the emulator drew a frame.
        overlay = new TextView(this);
        overlay.setText("A31: SDL Activity created\nDOS/WFW boot unverified");
        overlay.setTextColor(Color.WHITE);
        overlay.setBackgroundColor(Color.argb(175, 0, 0, 0));
        overlay.setTextSize(12);
        int p = (int)(8 * getResources().getDisplayMetrics().density);
        overlay.setPadding(p,p,p,p);
        overlay.setClickable(false);
        overlay.setFocusable(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.START);
        getWindow().addContentView(overlay, params);
        A31DiagnosticLog.record(this, "SDL Activity Java overlay added");
    }

    @Override
    protected void onResume() {
        super.onResume();
        A31DiagnosticLog.record(this, "SDL Activity onResume");
        if (overlay != null) overlay.setText("A31: SDL Java resumed\nNo guest disk configured");
    }

    @Override
    protected void onPause() {
        A31DiagnosticLog.record(this, "SDL Activity onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        A31DiagnosticLog.record(this, "SDL Activity onDestroy BEFORE native shutdown");
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        A31DiagnosticLog.record(this, focused ?
            "SDL Activity window FOCUSED" : "SDL Activity window UNFOCUSED");
    }
}
