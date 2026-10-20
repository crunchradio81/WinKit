package com.crownparkcomputing.retrodos.winkit

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.crownparkcomputing.retrodos.MainActivity
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A2 source overlay: standalone source import + offline-only launch selector.
 * No real Ethernet, Tailscale sockets, Windows files or remote-desktop code. */
class WinKitSetupActivity : Activity() {
    private companion object { const val PICK_IMAGE = 0x3120; const val PICK_CONFIG = 0x3121; const val EXPORT_NATIVE_CRASH = 0x3122; const val UI_RELEASE = "A19" }
    private val mainThread = Handler(Looper.getMainLooper())
    private val workers = Executors.newSingleThreadExecutor()
    private val store by lazy { GuestStore(filesDir) }
    private lateinit var status: TextView
    private lateinit var imageSha: EditText
    private lateinit var sourcePath: EditText
    private lateinit var import: Button
    private lateinit var config: Button
    private lateinit var boot: Button
    private lateinit var acknowledged: CheckBox
    private val machineDir get() = File(filesDir, "machines")
    private val offlineConf get() = File(machineDir, "YARDOMOB.offline.conf")
    private val ready get() = File(machineDir, "OFFLINE_READY")
    private val nativeTrace get() = File(filesDir, "winkit-native-startup.log")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val colors = obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        val bodyColor = colors.getColor(0, android.graphics.Color.BLACK)
        colors.recycle()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(20))
        }
        fun label(message: String, size: Float = 16f) = TextView(this).also {
            it.text = message
            it.textSize = size
            it.setTextColor(bodyColor)
            it.setPadding(0, dp(8), 0, dp(8))
            it.setLineSpacing(dp(2).toFloat(), 1f)
            if (size >= 20f) it.setTypeface(null, android.graphics.Typeface.BOLD)
            container.addView(it, LinearLayout.LayoutParams(-1, -2))
        }
        fun addAction(button: Button) {
            button.isAllCaps = false
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            button.setSingleLine(false)
            button.gravity = Gravity.CENTER
            button.setPadding(dp(12), dp(10), dp(12), dp(10))
            button.setMinimumHeight(dp(52))
            val layout = LinearLayout.LayoutParams(-1, -2)
            layout.bottomMargin = dp(8)
            container.addView(button, layout)
        }
        label("WinKit 3.11 Android · $UI_RELEASE", 23f)
        label("A18 native core checkpoints enabled. Guest boot is not yet verified.", 14f)
        val prefs = getSharedPreferences("winkit-machine-identity", MODE_PRIVATE)
        val proposedMac = prefs.getString("proposed_mac", null) ?: GuestIdentity.newLocalMac().also {
            prefs.edit().putString("proposed_mac", it).apply()
        }
        label("Machine: YARDOMOB | proposed NIC MAC: $proposedMac (NOT installed into emulated NIC)", 14f)
        label("DOS 6.22 / Windows for Workgroups 3.11 on THIS device. No VNC or Mac remote desktop.")
        label("Import a private disk and its DOSBox-X configuration, or try the standalone GPL BIOS smoke-test disk.", 14f)
        label("Step 1 · Paste SHA-256 from your private export manifest")
        imageSha = EditText(this).also {
            it.hint = "SHA-256 from manifest (64 hex digits)"
            it.setSingleLine(true)
            container.addView(it, ViewGroup.LayoutParams(-1, -2))
        }
        import = Button(this).also {
            it.text = "Import private YARDOMOB.raw"
            it.setOnClickListener {
                if (!Regex("[a-fA-F0-9]{64}").matches(imageSha.text.toString().trim())) {
                    update("Image SHA-256 must have 64 hex characters")
                } else pick(PICK_IMAGE)
            }
            addAction(it)
        }
        // Test the real Android emulator path without Mac exports or Microsoft media.
        Button(this).also { diagnostic ->
            diagnostic.text = "Install GPL BIOS smoke test (no DOS/Windows)"
            diagnostic.setOnClickListener {
                if (store.installedImage().exists() || offlineConf.exists() || ready.exists()) {
                    update("A machine already exists. Synthetic test will not overwrite any guest disk.")
                    return@setOnClickListener
                }
                import.isEnabled = false
                config.isEnabled = false
                boot.isEnabled = false
                diagnostic.isEnabled = false
                update("Creating independent local BIOS boot test image...")
                workers.execute {
                    val message = try {
                        val fixture = OfflineSmokeFixture.install(filesDir)
                        "GPL BIOS smoke disk prepared: ${fixture.length()} bytes. Review offline config and " +
                            "acknowledge, then launch local emulator. Expected banner: WINKIT A4 BIOS HDD BOOT OK. " +
                            "Display is NOT verified until device test."
                    } catch (e: Exception) { "Diagnostic install rejected: ${e.message}" }
                    mainThread.post { diagnostic.isEnabled = !store.installedImage().exists(); refresh(); update(message) }
                }
            }
            addAction(diagnostic)
        }
        label("Step 2 · Enter the EXACT original absolute Mac image path from [autoexec] IMGMOUNT")
        sourcePath = EditText(this).also {
            it.hint = "/.../YARDO-B-WORKING.raw"
            it.setSingleLine(true)
            container.addView(it, ViewGroup.LayoutParams(-1, -2))
        }
        config = Button(this).also {
            it.text = "Import DOSBox-X configuration (offline)"
            it.setOnClickListener {
                if (!store.installedImage().isFile) update("Import the verified disk first")
                else if (sourcePath.text.toString().isBlank()) update("Enter exact source disk path")
                else pick(PICK_CONFIG)
            }
            addAction(it)
        }
        label("Step 3 · Inspect and acknowledge the generated config. Virtual NE2000 and IPX are disabled.", 14f)
        Button(this).also {
            it.text = "Review offline DOSBox-X configuration"
            it.setOnClickListener {
                val raw = try { offlineConf.readText() } catch (e: Exception) {
                    update("No converted config available: ${e.message}"); return@setOnClickListener
                }
                val display = raw.take(12000) + if (raw.length > 12000) "\n...[truncated display]" else ""
                android.app.AlertDialog.Builder(this)
                    .setTitle("OFFLINE BOOT · Check the IMGMOUNT and BOOT commands")
                    .setMessage(display).setPositiveButton("Close", null).show()
            }
            addAction(it)
        }
        acknowledged = CheckBox(this).also {
            it.text = "I reviewed the private disk path, IMGMOUNT geometry and BOOT command. This is an OFFLINE first boot."
            it.setOnCheckedChangeListener { _, _ -> refresh() }
            container.addView(it)
        }
        boot = Button(this).also {
            it.text = "Open local emulator (offline)"
            it.setOnClickListener {
                if (!isReady()) update("Both verified disk and converted offline config required")
                else startActivity(Intent(this, MainActivity::class.java))
            }
            addAction(it)
        }
        Button(this).also { export ->
            export.text = "Export startup / crash report"
            export.setOnClickListener {
                // A native fault may be absent from retained Android crash history.
                // The app-private stage log is independently exportable.
                if (!hasNativeTrace() && latestNativeExit() == null) {
                    update("No WinKit diagnostics yet. Launch the offline guest, return here and export.")
                } else {
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/zip"
                        putExtra(Intent.EXTRA_TITLE, "WinKit-A19-startup-diagnostics.zip")
                    }, EXPORT_NATIVE_CRASH)
                }
            }
            addAction(export)
        }
        status = label("A19: Native emulator boot remains unverified.", 14f)
        label("IMPORTANT: Copied Windows computer name may still be YARDO/YARDO-B. Rename it to YARDOMOB and verify the NIC MAC before ANY network test.", 14f)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = true
            addView(container)
            setOnApplyWindowInsetsListener { view, insets ->
                // Android edge-to-edge: keep controls clear of system bars.
                val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.systemBars())
                } else null
                view.setPadding(
                    bars?.left ?: insets.systemWindowInsetLeft,
                    bars?.top ?: insets.systemWindowInsetTop,
                    bars?.right ?: insets.systemWindowInsetRight,
                    bars?.bottom ?: insets.systemWindowInsetBottom
                )
                insets
            }
        }
        setContentView(scroll)
        refresh()
        if (savedInstanceState == null) scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun pick(req: Int) = startActivityForResult(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, req
    )
    @Deprecated("Compatible with Android 9 / platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == EXPORT_NATIVE_CRASH) {
            workers.execute {
                val message = try {
                    val exit = latestNativeExit()
                    val available = hasNativeTrace()
                    require(available || exit != null) { "No WinKit diagnostics available" }
                    var savedLog = false
                    var savedTombstone = false
                    val destination = contentResolver.openOutputStream(uri, "w")
                        ?: error("Selected document provider returned no writable stream")
                    destination.use { out ->
                        ZipOutputStream(out).use { zip ->
                            val summary = "WinKit A19 startup diagnostics (A18 core probes)\n" +
                                "Native crash history: ${if (exit != null) "available" else "not retained"}\n" +
                                "Process: ${exit?.processName ?: "unknown"}\n" +
                                "Exit timestamp: ${exit?.timestamp ?: 0L}\n" +
                                "Reason: ${exit?.reason ?: "unknown"}\n" +
                                "Startup checkpoints are not a symbolized native backtrace.\n" +
                                "Guest disk, offline config and network data are excluded.\n"
                            zip.putNextEntry(ZipEntry("summary.txt"))
                            zip.write(summary.toByteArray(StandardCharsets.UTF_8))
                            zip.closeEntry()
                            if (available) {
                                zip.putNextEntry(ZipEntry("native-startup.log"))
                                nativeTrace.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                                savedLog = true
                            }
                            exit?.traceInputStream?.use { trace ->
                                zip.putNextEntry(ZipEntry("native-tombstone.pb"))
                                trace.copyTo(zip)
                                zip.closeEntry()
                                savedTombstone = true
                            }
                        }
                    } // A completed ZIP must not be reported as a failed write.
                    "WinKit diagnostics saved: startup log=$savedLog, tombstone=$savedTombstone"
                } catch (e: Exception) { "Diagnostic export failed (${e.javaClass.simpleName}): ${e.message}" }
                update(message)
            }
            return
        }
        if (requestCode !in arrayOf(PICK_IMAGE, PICK_CONFIG)) return
        val sha = imageSha.text.toString().trim()
        val old = sourcePath.text.toString().trim()
        import.isEnabled = false
        config.isEnabled = false
        boot.isEnabled = false
        update("Reading SAF source into private storage...")
        workers.execute {
            val message = try {
                if (requestCode == PICK_IMAGE) {
                    contentResolver.openInputStream(uri)?.use { src ->
                        val receipt = store.importVerified(src, sha)
                        val evidence = java.io.FileInputStream(receipt.image).use { disk ->
                            BootSectorEvidence.inspect(disk, receipt.bytes)
                        }
                        "Private disk imported: ${receipt.bytes} bytes (SHA-256 verified). " +
                        "First-sector classification: ${evidence.kind}; " +
                        "partition warnings: ${evidence.errors.size}. " +
                        "Neither guest boot nor Windows identity is verified; Ethernet stays disabled."
                    } ?: error("Cannot open image picker result")
                } else {
                    val bytes = contentResolver.openInputStream(uri)?.use { src ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(4096)
                        while (true) {
                            val n = src.read(buf)
                            if (n < 0) break
                            if (n > 0) out.write(buf, 0, n)
                            require(out.size() <= 262144) { "Config exceeds 256 KiB" }
                        }
                        out.toByteArray()
                    } ?: error("Cannot open config picker result")
                    val disk = store.installedImage()
                    require(disk.isFile && disk.length() >= 512 && disk.length() % 512L == 0L)
                    val converted = OfflineBootConfig.convert(
                        String(bytes, StandardCharsets.UTF_8), old, disk.absolutePath
                    )
                    require(OfflineDiskGeometry.matches(converted, disk)) {
                        "Imported disk must be a supported raw image matching exact IMGMOUNT CHS bytes"
                    }
                    require(!offlineConf.exists() && !ready.exists()) {
                        "Offline config already prepared. Never overwrite a launched machine's config."
                    }
                    val tmp = File(machineDir, ".offline-conf.partial")
                    require(tmp.createNewFile()) { "Staging file exists; investigate interrupted import" }
                    try {
                        java.io.FileOutputStream(tmp).use { stream ->
                            stream.write(converted.toByteArray(StandardCharsets.UTF_8))
                            stream.fd.sync()
                        }
                        NoReplacePublisher.publish(tmp, offlineConf)
                        // The native frontend discovers WinKit only after this marker exists.
                        // Never create it earlier, and never create it if conversion failed.
                        val marker = File(machineDir, "OFFLINE_READY")
                        val stagedMarker = File(machineDir, ".winkit-ready-${java.util.UUID.randomUUID()}.partial")
                        require(stagedMarker.createNewFile())
                        try {
                            java.io.FileOutputStream(stagedMarker).use { output ->
                                output.write("a2-offline\n".toByteArray(StandardCharsets.US_ASCII))
                                output.fd.sync()
                            }
                            NoReplacePublisher.publish(stagedMarker, marker)
                        } finally { if (stagedMarker.exists()) stagedMarker.delete() }
                    } finally { if (tmp.exists()) tmp.delete() }
                    "Offline configuration created. Review it before launching. No networking is available."
                }
            } catch (e: Exception) { "Rejected safely: ${e.message}" }
            mainThread.post { refresh(); update(message) }
        }
    }

    private fun hasNativeTrace(): Boolean = nativeTrace.isFile &&
        !java.nio.file.Files.isSymbolicLink(nativeTrace.toPath()) &&
        nativeTrace.length() in 1L..262144L

    private fun latestNativeExit(): ApplicationExitInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            manager.getHistoricalProcessExitReasons(packageName, 0, 32)
                .filter { it.processName == packageName &&
                          it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }
                .maxByOrNull { it.timestamp }
        } catch (_: Exception) { null }
    }

    private fun isReady(): Boolean =
        store.installedImage().isFile && store.installedImage().length() >= 512 &&
        store.installedImage().length() % 512L == 0L &&
        offlineConf.isFile && offlineConf.length() in 1L..262144L &&
        ready.isFile && ready.length() == 11L &&
        try { ready.readText() == "a2-offline\n" &&
              OfflineDiskGeometry.matches(offlineConf.readText(), store.installedImage()) }
        catch (_: Exception) { false }

    private fun refresh() {
        if (::boot.isInitialized) boot.isEnabled = isReady() && ::acknowledged.isInitialized && acknowledged.isChecked
        if (::import.isInitialized) import.isEnabled = !store.installedImage().exists()
        if (::config.isInitialized) config.isEnabled = !offlineConf.exists() && store.installedImage().exists()
    }
    private fun update(message: String) { mainThread.post { if (::status.isInitialized) status.text = message } }
    override fun onDestroy() { super.onDestroy(); workers.shutdown() }
}
