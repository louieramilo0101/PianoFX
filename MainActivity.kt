package com.pianofx

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.media.midi.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Shell: WebView hosts the UI + audio; native MidiManager feeds MIDI bytes to the page. */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var mm: MidiManager
    private val ui = Handler(Looper.getMainLooper())
    private val open = HashMap<Int, MidiDevice>()
    private var fileCb: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#0b0d12")
        window.navigationBarColor = Color.parseColor("#0b0d12")
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0b0d12"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                    fileCb?.onReceiveValue(null); fileCb = cb
                    val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                    }
                    return try { startActivityForResult(Intent.createChooser(i, "Choose file"), 1); true }
                    catch (e: Exception) { fileCb = null; false }
                }
            }
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(web)
        mm = getSystemService(MIDI_SERVICE) as MidiManager
        mm.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(d: MidiDeviceInfo) = connect(d)
            override fun onDeviceRemoved(d: MidiDeviceInfo) { open.remove(d.id)?.close(); status() }
        }, ui)
        mm.devices.forEach { connect(it) }
        ui.postDelayed({ status() }, 800)
    }

    private fun name(d: MidiDeviceInfo) =
        d.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI device"

    private fun connect(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0 || open.containsKey(info.id)) return
        mm.openDevice(info, { dev ->
            if (dev == null) return@openDevice
            open[info.id] = dev
            dev.openOutputPort(0)?.connect(object : MidiReceiver() {
                override fun onSend(m: ByteArray, off: Int, cnt: Int, ts: Long) {
                    var i = off
                    while (i < off + cnt) {
                        val s = m[i].toInt() and 0xFF
                        val len = when (s and 0xF0) { 0xC0, 0xD0 -> 2; in 0x80..0xE0 -> 3; else -> 1 }
                        if (s in 0x80..0xEF && i + len <= off + cnt) {
                            val js = (0 until len).joinToString(",") { (m[i + it].toInt() and 0xFF).toString() }
                            ui.post { web.evaluateJavascript("window.onNativeMidi&&onNativeMidi([$js])", null) }
                        }
                        i += len
                    }
                }
            })
            status()
        }, ui)
    }

    private fun status() {
        val names = open.values.map { name(it.info) }
        val t = if (names.isEmpty()) "No keyboard connected" else names.joinToString(", ")
        web.evaluateJavascript("window.onNativeStatus&&onNativeStatus(${names.size},${org.json.JSONObject.quote(t)})", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (rc == 1) { fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, d)); fileCb = null }
        else super.onActivityResult(rc, res, d)
    }

    override fun onDestroy() { open.values.forEach { it.close() }; super.onDestroy() }
}
