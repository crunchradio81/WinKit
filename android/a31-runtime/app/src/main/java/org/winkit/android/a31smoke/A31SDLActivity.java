package org.winkit.android.a31smoke;

import android.graphics.Color;
import android.content.Intent;
import android.os.Build;
import android.widget.Button;
import android.view.ViewGroup;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.libsdl.app.SDLActivity;

/**
 * Trace SDL2 Java activity and whether SDLThread actually starts.
 * Java UI readiness is not evidence of a DOSBox-X frame or a guest boot.
 */
public class A31SDLActivity extends SDLActivity {
    private TextView overlay;
    private final Handler observer = new Handler(Looper.getMainLooper());
    private final java.util.ArrayList<Runnable> pending = new java.util.ArrayList<>();
    private boolean stopping = false;
    private OnBackInvokedCallback backCallback;

    // libbridge.so traces calls into the existing unmodified libmain.so.
    @Override protected String[] getLibraries() { return new String[]{"SDL2", "bridge"}; }
    private static native int entryStage();
    private static native int entryResult();
    private static native String entryError();
    @Override protected String[] getArguments() { return new String[0]; }
    protected String testName() { return "DOSBox-X"; }
    protected String extraNativeStatus() {
        try {
            int stage = entryStage();
            String message = "DOSBox entry stage=" + stage;
            if (stage == 4) message += ", exit=" + entryResult();
            if (stage < 0) message += ", error=" + entryError();
            if (stage == 3) message += " (inside DOSBox-X SDL_main)";
            return message;
        } catch (UnsatisfiedLinkError ex) {
            return "Native entry bridge not loaded yet";
        }
    }
    private void returnToDiagnostics() {
        A31DiagnosticLog.record(this, testName() + " RETURN control pressed");
        Intent i = new Intent(this, A31DiagnosticActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        // Do not finish() here; pinned SDLActivity.onDestroy joins the native thread.
    }
    @Override public void onBackPressed() { returnToDiagnostics(); }

    private String nativeThreadState(boolean withStack) {
        Thread thread = mSDLThread;
        String result = "SDLThread=" + (thread == null ? "NOT CREATED" :
              thread.getState().toString() + ", alive=" + thread.isAlive()) +
            "; SDL state=" + mCurrentNativeState + "->" + mNextNativeState +
            "; focus=" + mHasFocus + "; resume=" + mIsResumedCalled;
        if (withStack && thread != null) {
            StackTraceElement[] stack = thread.getStackTrace();
            StringBuilder sb = new StringBuilder(result);
            for (int i = 0; i < Math.min(5, stack.length); i++) {
                sb.append(" [").append(i).append("] ").append(stack[i].toString());
            }
            return sb.toString();
        }
        return result;
    }

    private void checkpoint(String label, boolean stack) {
        if (stopping) return;
        String details = nativeThreadState(stack);
        A31DiagnosticLog.record(this, testName() + " " + label + ": " + details + " " + extraNativeStatus());
        if (overlay != null) {
            Thread t = mSDLThread;
            overlay.setText("A31 " + testName() + " diagnostics (no guest disk)\n" +
                "SDLThread: " + (t == null ? "NOT CREATED" :
                     (t.isAlive() ? "ALIVE" : "STOPPED") + " / " + t.getState()) +
                "\nNative state: " + mCurrentNativeState + " / " + mNextNativeState +
                "\n" + extraNativeStatus() + "\nTap RETURN TO DIAGNOSTICS");
        }
    }

    private void observeAfter(long millis) {
        final Runnable sample = () -> {
            if (!stopping) checkpoint("SDLThread after " + millis + " ms", true);
        };
        pending.add(sample);
        observer.postDelayed(sample, millis);
    }

    @Override protected void onCreate(Bundle state) {
        A31DiagnosticLog.record(this, "SDL Activity onCreate BEFORE SDL Java/native setup");
        super.onCreate(state);
        A31DiagnosticLog.record(this, "SDL Activity onCreate AFTER SDL Java/native setup");
        overlay = new TextView(this);
        overlay.setTextColor(Color.WHITE);
        overlay.setBackgroundColor(Color.argb(205, 10, 10, 10));
        overlay.setTextSize(13);
        int pad = (int)(10 * getResources().getDisplayMetrics().density);
        overlay.setPadding(pad, pad, pad, pad);
        overlay.setClickable(false);
        overlay.setFocusable(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.START);
        params.topMargin = (int)(42 * getResources().getDisplayMetrics().density);
        getWindow().addContentView(overlay, params);
        Button escape = new Button(this);
        escape.setText("RETURN TO DIAGNOSTICS");
        escape.setAllCaps(false);
        escape.setOnClickListener(v -> returnToDiagnostics());
        FrameLayout.LayoutParams escapeParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        escapeParams.topMargin = (int)(195 * getResources().getDisplayMetrics().density);
        getWindow().addContentView(escape, escapeParams);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::returnToDiagnostics;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        checkpoint("SDL Activity created", false);
        observeAfter(1000);
        observeAfter(3500);
        observeAfter(8500);
    }

    @Override protected void onResume() {
        super.onResume();
        A31DiagnosticLog.record(this, "SDL Activity onResume");
        checkpoint("SDL Activity resumed", false);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        A31DiagnosticLog.record(this, focused ? "SDL Activity window FOCUSED" :
            "SDL Activity window UNFOCUSED");
        checkpoint("SDL Activity focusChanged", false);
    }

    @Override protected void onPause() {
        checkpoint("SDL Activity before onPause", true);
        super.onPause();
        A31DiagnosticLog.record(this, "SDL Activity onPause");
    }

    @Override protected void onDestroy() {
        checkpoint("SDL Activity before onDestroy", true);
        stopping = true;
        for (Runnable callback : pending) observer.removeCallbacks(callback);
        pending.clear();
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
        super.onDestroy();
        A31DiagnosticLog.record(this, "SDL Activity onDestroy AFTER SDL cleanup");
    }
}
