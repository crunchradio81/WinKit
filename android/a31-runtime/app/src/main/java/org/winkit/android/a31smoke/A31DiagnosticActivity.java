package org.winkit.android.a31smoke;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Java-first diagnostic screen. Never starts DOSBox-X without a button press. */
public final class A31DiagnosticActivity extends Activity {
    private TextView report;

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
        heading.setText("WinKit A31 — Startup diagnostics");
        column.addView(heading);
        TextView instructions = new TextView(this);
        instructions.setText("Step 1: Check the libraries. Step 2: launch DOSBox-X. " +
            "If the screen is black, return here and share the report. " +
            "No DOS/Windows guest disk is included. This test won't boot WFW 3.11.");
        instructions.setTextSize(15);
        column.addView(instructions);
        button(column, "1 — Check native libraries", this::checkLibraries);
        button(column, "2 — Launch DOSBox-X SDL2", () -> {
            A31DiagnosticLog.record(this, "Launching SDL Activity");
            refreshReport();
            startActivity(new Intent(this, A31SDLActivity.class));
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
