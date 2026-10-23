package org.winkit.android.a31smoke;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;

/** Read-only Android SAF inspection of a user-owned .raw disk; never boots or writes. */
public final class A31GuestDisk {
    private static final String STORE = "a31-disk-selection";
    private static final String URI = "disk-uri";
    private static final String SUMMARY = "disk-summary";
    private A31GuestDisk() {}

    public static final class Inspection {
        public final String displayName;
        public final String summary;
        Inspection(String name, String details) {
            displayName = name;
            summary = details;
        }
    }

    public static Inspection inspect(Context context, Uri uri) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme()))
            throw new IOException("Choose the raw image through Android Files.");
        ContentResolver resolver = context.getContentResolver();
        String name = "(unknown)";
        try (Cursor cursor = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                name = cursor.getString(0);
        } catch (RuntimeException ignored) {
            // File content validation is authoritative, not provider metadata.
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".raw"))
            throw new IOException("Expected a .raw image; selected: " + name);
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null)
                throw new IOException("The file provider refused read-only access.");
            try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
                FileChannel channel = input.getChannel();
                long length = channel.size();
                if (length < 1048576L || length % 512 != 0)
                    throw new IOException("Image size is not a sector-aligned hard disk: " + length);
                byte[] mbr = readSector(channel, 0);
                if (!hasSignature(mbr))
                    throw new IOException("Sector zero lacks 55 AA signature; not selecting image.");
                long imageSectors = length / 512;
                StringBuilder summary = new StringBuilder();
                summary.append(name).append(": ").append(length)
                       .append(" bytes (").append(imageSectors).append(" sectors)\n");
                summary.append("Sector zero: boot signature 55 AA present\n");
                int count = 0;
                long firstPartition = 0;
                int firstType = 0;
                for (int i = 0; i < 4; i++) {
                    int o = 446 + 16 * i;
                    int type = mbr[o + 4] & 255;
                    long lba = unsigned32(mbr, o + 8);
                    long sectors = unsigned32(mbr, o + 12);
                    if (type == 0) continue;
                    count++;
                    boolean inRange = lba > 0 && sectors > 0
                        && lba < imageSectors && sectors <= imageSectors - lba;
                    summary.append("Partition ").append(i + 1)
                        .append(": type 0x").append(String.format(Locale.US,"%02X",type))
                        .append(", LBA ").append(lba).append(", sectors ").append(sectors)
                        .append(inRange ? " (in range)" : " (range questionable)")
                        .append("\n");
                    if (firstPartition == 0 && inRange) {
                        firstPartition = lba;
                        firstType = type;
                    }
                }
                if (count == 0) {
                    summary.append("No MBR partitions: may be a floppy/superfloppy.\n");
                } else if (firstPartition != 0) {
                    byte[] boot = readSector(channel, firstPartition * 512L);
                    summary.append("First in-range partition (0x")
                        .append(String.format(Locale.US,"%02X",firstType))
                        .append(") boot signature: ")
                        .append(hasSignature(boot) ? "55 AA present" : "missing")
                        .append("\n");
                }
                summary.append("Inspection only — no mount, copy, modifications or boot.");
                return new Inspection(name, summary.toString());
            }
        } catch (SecurityException ex) {
            throw new IOException("Android document provider denied access.",ex);
        }
    }

    private static byte[] readSector(FileChannel channel, long offset) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        channel.position(offset);
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer);
            if (n <= 0) throw new IOException("Unexpected end of disk image at " + offset);
        }
        return buffer.array();
    }

    private static boolean hasSignature(byte[] sector) {
        return (sector[510] & 255) == 0x55 && (sector[511] & 255) == 0xaa;
    }

    private static long unsigned32(byte[] b, int n) {
        return (b[n] & 255L) | ((b[n+1] & 255L) << 8)
            | ((b[n+2] & 255L) << 16) | ((b[n+3] & 255L) << 24);
    }

    /** Borrow a file descriptor only while performing a native READ ONLY test. */
    private static native String nativeProbeReadableFd(int fd, long size);

    public static String nativeReadOnlyTest(Context context) throws IOException {
        Uri uri = selectedUri(context);
        if (uri == null) throw new IOException("Select YARDOMOB.raw first.");
        try (ParcelFileDescriptor pfd = context.getContentResolver()
                .openFileDescriptor(uri, "r")) {
            if (pfd == null) throw new IOException("Provider did not return a descriptor.");
            long bytes = pfd.getStatSize();
            if (bytes < 0) {
                try (FileInputStream input = new FileInputStream(pfd.getFileDescriptor())) {
                    bytes = input.getChannel().size();
                }
            }
            if (bytes < 1048576L || bytes % 512 != 0)
                throw new IOException("Native descriptor is not a sector-aligned disk image.");
            try {
                System.loadLibrary("SDL2");
                System.loadLibrary("bridge");
                return nativeProbeReadableFd(pfd.getFd(), bytes);
            } catch (UnsatisfiedLinkError error) {
                throw new IOException("Native bridge not loadable: " + error.getMessage(),error);
            }
        } catch (SecurityException error) {
            throw new IOException("No persisted Android read-only image permission.",error);
        }
    }

    public static void saveNativeResult(Context context,String summary) {
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .edit().putString("native-result",summary).commit();
    }

    public static String savedNativeResult(Context context) {
        return context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getString("native-result","(not tested yet)");
    }

    public static void remember(Context context, Uri uri, Inspection info) {
        context.getContentResolver().takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit()
            .putString(URI,uri.toString()).putString(SUMMARY,info.summary)
            .remove("native-result").commit())
            throw new IllegalStateException("Could not persist selected document.");
    }

    public static Uri selectedUri(Context context) {
        String value = context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getString(URI,null);
        return value == null ? null : Uri.parse(value);
    }

    public static String savedSummary(Context context) {
        return context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
            .getString(SUMMARY,"(no image selected)");
    }

    public static void forget(Context context) {
        Uri uri = selectedUri(context);
        if (uri != null) {
            try {
                context.getContentResolver().releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Permission may already have expired.
            }
        }
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().clear().commit();
    }
}
