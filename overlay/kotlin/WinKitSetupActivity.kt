package com.crownparkcomputing.retrodos.winkit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

/** A2 source overlay: standalone source import + offline-only launch selector.
 * No real Ethernet, Tailscale sockets, Windows files or remote-desktop code. */
class WinKitSetupActivity : Activity() {
    private companion object { const val PICK_IMAGE = 0x3120; const val PICK_CONFIG = 0x3121 }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }
        fun label(message: String, size: Float = 16f) = TextView(this).also {
            it.text = message
            it.textSize = size
            it.setPadding(0, 10, 0, 10)
            container.addView(it)
        }
        label("WinKit 3.11 · A13 Offline Diagnostic", 24f)
        label("A13 native guest gate reporting enabled. BIOS boot is not yet verified.", 14f)
        val prefs = getSharedPreferences("winkit-machine-identity", MODE_PRIVATE)
        val proposedMac = prefs.getString("proposed_mac", null) ?: GuestIdentity.newLocalMac().also {
            prefs.edit().putString("proposed_mac", it).apply()
        }
        label("Machine: YARDOMOB | proposed NIC MAC: $proposedMac (NOT installed into emulated NIC)", 14f)
        label("DOS 6.22 / Windows for Workgroups 3.11 on THIS device. No VNC or Mac remote desktop.")
        label("A2 requires a stopped independent image and its real Mac DOSBox-X configuration. Neither is included.", 14f)
        label("Step 1 · Paste SHA-256 from your private export manifest")
        imageSha = EditText(this).also {
            it.hint = "64 hexadecimal characters from manifest.json"
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
            container.addView(it)
        }
        // Test the real Android emulator path without Mac exports or Microsoft media.
        Button(this).also { diagnostic ->
            diagnostic.text = "Install GPL BIOS boot smoke test (NO DOS/Windows)"
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
            container.addView(diagnostic)
        }
        label("Step 2 · Enter the EXACT original absolute Mac image path from [autoexec] IMGMOUNT")
        sourcePath = EditText(this).also {
            it.hint = "/.../YARDO-B-WORKING.raw"
            it.setSingleLine(true)
            container.addView(it, ViewGroup.LayoutParams(-1, -2))
        }
        config = Button(this).also {
            it.text = "Import working DOSBox-X .conf (offline conversion)"
            it.setOnClickListener {
                if (!store.installedImage().isFile) update("Import the verified disk first")
                else if (sourcePath.text.toString().isBlank()) update("Enter exact source disk path")
                else pick(PICK_CONFIG)
            }
            container.addView(it)
        }
        label("Step 3 · Inspect and acknowledge the generated config. Virtual NE2000 and IPX are disabled.", 14f)
        Button(this).also {
            it.text = "Inspect generated offline DOSBox-X configuration"
            it.setOnClickListener {
                val raw = try { offlineConf.readText() } catch (e: Exception) {
                    update("No converted config available: ${e.message}"); return@setOnClickListener
                }
                val display = raw.take(12000) + if (raw.length > 12000) "\n...[truncated display]" else ""
                android.app.AlertDialog.Builder(this)
                    .setTitle("OFFLINE BOOT · Check the IMGMOUNT and BOOT commands")
                    .setMessage(display).setPositiveButton("Close", null).show()
            }
            container.addView(it)
        }
        acknowledged = CheckBox(this).also {
            it.text = "I reviewed the private disk path, IMGMOUNT geometry and BOOT command. This is an OFFLINE first boot."
            it.setOnCheckedChangeListener { _, _ -> refresh() }
            container.addView(it)
        }
        boot = Button(this).also {
            it.text = "Open LOCAL emulation library (offline)"
            it.setOnClickListener {
                if (!isReady()) update("Both verified disk and converted offline config required")
                else startActivity(Intent(this, MainActivity::class.java))
            }
            container.addView(it)
        }
        status = label("A11 ARM64 APK compiled; standalone BIOS guest boot still UNVERIFIED.", 14f)
        label("IMPORTANT: Copied Windows computer name may still be YARDO/YARDO-B. Rename it to YARDOMOB and verify the NIC MAC before ANY network test.", 14f)
        val scroll = ScrollView(this)
        scroll.addView(container)
        setContentView(scroll)
        refresh()
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
