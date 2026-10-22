package org.winkit.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** A1 import-and-probe launcher. NO emulated machine is started in this milestone. */
class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val workers = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var expectedHash: EditText
    private lateinit var importButton: Button
    private lateinit var transferButton: Button
    private val store by lazy { GuestStore(filesDir) }
    private val prefs by lazy { getSharedPreferences("machine-identity", MODE_PRIVATE) }
    private val selectDisk = 311
    private val selectTransfer = 312

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mac = prefs.getString("mac", null) ?: GuestIdentity.newLocalMac().also {
            prefs.edit().putString("mac", it).apply()
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 45, 36, 45)
        }
        fun line(msg: String, size: Float = 16f): TextView = TextView(this).apply {
            text = msg
            textSize = size
            setPadding(0, 7, 0, 9)
            col.addView(this)
        }
        line("WinKit 3.11 — Android A1", 23f)
        line("Local computer: ${GuestIdentity.COMPUTER_NAME} (Windows rename NOT yet verified)")
        line("NE2000 proposed MAC: $mac (not yet attached to an emulator)")
        line("Networking: DISABLED. No VPN socket, no virtual LAN, no remote desktop.")
        line("Import a PRIVATE Windows disk copied from WinKit for Mac. Network remains disabled.")
        transferButton = Button(this).apply {
            text = "Import from WinKit Mac ZIP"
            setOnClickListener {
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(pick, selectTransfer)
            }
            col.addView(this)
        }
        line("Or import a legacy raw image with its manifest SHA-256:", 14f)
        expectedHash = EditText(this).apply {
            hint = "manifest.json sha256 (64 hex characters)"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            col.addView(this, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        importButton = Button(this).apply {
            text = "Import independently exported HDD"
            setOnClickListener {
                val sha = expectedHash.text.toString().trim()
                if (!Regex("[0-9a-fA-F]{64}").matches(sha)) {
                    showStatus("Paste the 64-character SHA-256 from export manifest first.")
                } else {
                    val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(pick, selectDisk)
                }
            }
            col.addView(this)
        }
        status = line("No emulator boot available in A1. Native disk probe is functional only after Android NDK build.")
        Button(this).apply {
            text = "Inspect imported disk (no boot)"
            setOnClickListener {
                workers.execute {
                    val outcome = try {
                        "Native exclusive disk probe: ${NativeDisk.probe(store.installedImage().absolutePath)} bytes. Emulator still NOT connected."
                    } catch (t: Throwable) { "Probe unavailable: ${t.message}" }
                    showStatus(outcome)
                }
            }
            col.addView(this)
        }
        line("Next gate: integrate verified DOSBox-X ARM64 core + validated Mac boot config. Then OFFLINE DOS/WFW boot. ETHNET remains disabled.", 14f)
        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
    }
    @Deprecated("Minimum SDK compatible Android picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in setOf(selectDisk, selectTransfer) || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val sha = expectedHash.text.toString().trim()
        importButton.isEnabled = false
        transferButton.isEnabled = false
        showStatus("Verifying private transfer and SHA-256; network remains disabled…")
        workers.execute {
            val msg = try {
                if (requestCode == selectTransfer) {
                    val receipt = WinKitAndroidDiskImporter.importArchive(this, uri)
                    "VERIFIED MAC TRANSFER ${receipt.bytes} bytes, sha256 ${receipt.sha256}. Networking BLOCKED; YARDOMOB identity not yet configured."
                } else {
                    val source = contentResolver.openInputStream(uri) ?: error("Could not open document")
                    source.use {
                        val receipt = store.importVerified(it, sha)
                        "IMPORTED ${receipt.bytes} bytes; sha256 ${receipt.sha256}. No boot. Windows identity still unverified."
                    }
                }
            } catch (t: Throwable) { "Import failed safely: ${t.message}" }
            ui.post { importButton.isEnabled = true; transferButton.isEnabled = true; status.text = msg }
        }
    }
    private fun showStatus(s: String) { ui.post { if (::status.isInitialized) status.text = s } }
    override fun onDestroy() { super.onDestroy(); workers.shutdown() }
}
