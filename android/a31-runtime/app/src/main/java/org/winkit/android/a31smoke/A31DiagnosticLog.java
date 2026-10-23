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
            "Guest boot: NOT VERIFIED; image selection is inspection-only\n" +
            "Selected guest image:\n" + A31GuestDisk.savedSummary(context) + "\n\n" +
            "Native read-only disk test:\n" + A31GuestDisk.savedNativeResult(context) + "\n\n" +
            "Startup checkpoints:\n" + prefs.getString("events", "(no checkpoints)") +
            "\nDOSBox-X stdout/stderr tail (if bridge reached):\n" +
            nativeLogTail(context) +
            "\nA native fatal signal may require Android adb logcat.\n";
    }

    private static String nativeLogTail(Context context) {
        java.io.File file = new java.io.File(context.getFilesDir(), "a31-dosbox-stdout.txt");
        if (!file.isFile()) return "(not captured; DOSBox-X native bridge may not have started)\n";
        try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(file, "r")) {
            long length = in.length();
            int last = (int)Math.min(length, 9000L);
            in.seek(length - last);
            byte[] b = new byte[last];
            in.readFully(b);
            return new String(b, java.nio.charset.StandardCharsets.UTF_8) + "\n";
        } catch (java.io.IOException ex) {
            return "(native log read error: " + ex.getClass().getSimpleName() + ")\n";
        }
    }

    public static synchronized void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove("events").commit();
    }
}
