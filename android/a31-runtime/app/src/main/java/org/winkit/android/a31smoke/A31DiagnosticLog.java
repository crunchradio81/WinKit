package org.winkit.android.a31smoke;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** App-private, bounded, no-permission Java lifecycle checkpoints. */
public final class A31DiagnosticLog {
    private static final String PREF = "a31-startup-checkpoints";
    private A31DiagnosticLog() {}

    public static synchronized void record(Context context, String stage) {
        SharedPreferences prefs = context.getApplicationContext()
            .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String events = prefs.getString("events", "");
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String entry = time + " " + stage.replace('\n', ' ') + "\n";
        String updated = events + entry;
        if (updated.length() > 7000) updated = updated.substring(updated.length() - 7000);
        // commit() persists a checkpoint even if native code crashes immediately afterward.
        prefs.edit().putString("events", updated).commit();
    }

    public static String report(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
            .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return "WinKit A31 SDL2 Android startup diagnostic\n" +
            "Package: " + context.getPackageName() + "\n" +
            "Android API: " + Build.VERSION.SDK_INT + "\n" +
            "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
            "Supported ABIs: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS) + "\n" +
            "Guest boot: NOT VERIFIED; no guest image included\n\n" +
            "Startup checkpoints:\n" + prefs.getString("events", "(no checkpoints)") +
            "\nA native fatal signal may require Android adb logcat.\n";
    }

    public static synchronized void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove("events").commit();
    }
}
