package org.winkit.android.a31smoke;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Java-first diagnostic screen. Never starts DOSBox-X without a button press. */
public final class A31DiagnosticActivity extends Activity {
    private TextView report;
    private static final int PICK_RAW_IMAGE = 4131;
    private boolean checkingImage = false;

    private Button button(LinearLayout column, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        column.addView(b, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        A31DiagnosticLog.record(this, "Java diagnostic launcher created");
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(18 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column);
        TextView heading = new TextView(this);
        heading.setTextSize(22);
        heading.setText("WinKit A31 — Android guest setup");
        column.addView(heading);
        TextView instructions = new TextView(this);
        instructions.setText("DOSBox-X now displays its built-in shell. Select the existing " +
            "YARDOMOB.raw through Android Files for READ-ONLY inspection. " +
            "Selection does not mount, modify, copy, or boot the image.");
        instructions.setTextSize(15);
        column.addView(instructions);
        button(column, "1 — Check native libraries", this::checkLibraries);
        button(column, "2 — TEST NATIVE SDL2 COLORS (no DOSBox-X)", () -> {
            A31DiagnosticLog.record(this, "Launching independent native SDL2 color renderer");
            startActivity(new Intent(this, A31RenderProbeActivity.class));
        });
        button(column, "3 — Launch DOSBox-X SDL2 (built-in shell)", () -> {
            A31DiagnosticLog.record(this, "Launching SDL Activity — waiting for SDLThread samples at 1s, 3.5s, 8.5s");
            refreshReport();
            startActivity(new Intent(this, A31SDLActivity.class));
        });
        button(column, "4 — SELECT EXISTING YARDOMOB.raw (READ ONLY)", this::chooseRawImage);
        button(column, "Recheck selected disk (read only)", () -> {
            Uri selected = A31GuestDisk.selectedUri(this);
            if (selected == null) {
                A31DiagnosticLog.record(this, "No guest image selected for recheck");
                refreshReport();
            } else {
                inspectOnly(selected);
            }
        });
        button(column, "Forget selection (original disk unchanged)", () -> {
            A31GuestDisk.forget(this);
            A31DiagnosticLog.record(this, "Disk selection forgotten; image untouched");
            refreshReport();
        });
        button(column, "Copy startup report", () -> {
            ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("A31 startup", A31DiagnosticLog.report(this)));
        });
        button(column, "Share startup report", () -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, A31DiagnosticLog.report(this));
            startActivity(Intent.createChooser(send, "Send A31 diagnostic report"));
        });
        button(column, "Clear checkpoint history", () -> {
            A31DiagnosticLog.clear(this);
            A31DiagnosticLog.record(this, "Checkpoint history cleared");
            refreshReport();
        });
        report = new TextView(this);
        report.setTextSize(12);
        report.setTextIsSelectable(true);
        column.addView(report);
        setContentView(scroll);
        refreshReport();
    }

    @Override
    protected void onResume() {
        super.onResume();
        A31DiagnosticLog.record(this, "Java diagnostic launcher resumed");
        refreshReport();
    }

    private void refreshReport() {
        if (report != null) report.setText(A31DiagnosticLog.report(this));
    }

    private void chooseRawImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_RAW_IMAGE);
        } catch (ActivityNotFoundException error) {
            A31DiagnosticLog.record(this, "Android document picker unavailable");
            refreshReport();
        }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int code, int resultCode, Intent data) {
        super.onActivityResult(code, resultCode, data);
        if (code != PICK_RAW_IMAGE) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            A31DiagnosticLog.record(this, "Guest image picker canceled");
            refreshReport();
            return;
        }
        inspectOnly(data.getData());
    }

    private void inspectOnly(Uri uri) {
        if (checkingImage) return;
        checkingImage = true;
        A31DiagnosticLog.record(this, "Read-only RAW inspection started");
        refreshReport();
        new Thread(() -> {
            try {
                A31GuestDisk.Inspection info = A31GuestDisk.inspect(this, uri);
                A31GuestDisk.remember(this, uri, info);
                A31DiagnosticLog.record(this, "Read-only guest image verified: " +
                    info.displayName + " (not mounted)");
            } catch (Exception error) {
                A31DiagnosticLog.record(this, "Guest image inspection failed: " +
                    error.getClass().getSimpleName() + ": " + error.getMessage());
            } finally {
                runOnUiThread(() -> {
                    checkingImage = false;
                    refreshReport();
                });
            }
        }, "A31-ReadOnlyGuestInspector").start();
    }

    private void checkLibraries() {
        for (String name : new String[]{"c++_shared", "SDL2", "main"}) {
            try {
                System.loadLibrary(name);
                A31DiagnosticLog.record(this, "System.loadLibrary(" + name + "): PASS");
            } catch (UnsatisfiedLinkError | SecurityException error) {
                A31DiagnosticLog.record(this, "System.loadLibrary(" + name + "): FAIL " +
                    error.getClass().getSimpleName() + ": " + error.getMessage());
                break;
            }
        }
        refreshReport();
    }
}
