package com.azenithtech.azenithai

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * UI = WebUI module (webroot/index.html). Fitur = module (azenith-ai CLI + daemon).
 * App ini hanya: (1) memuat halaman WebUI dari module yang terpasang, (2) menyalurkan
 * ksu.exec() dari halaman itu ke shell root.
 */
class MainActivity : Activity() {

    companion object {
        const val MODULE_DIR = "/data/adb/modules/azenith-ai"
        private const val BASE_URL = "https://azenith.local/"   // origin tetap supaya localStorage (tema, dll) awet
        private const val REQ_FILE = 4711
        private const val BG = 0xFF0F1115.toInt()
        private const val TX = 0xFFE8EAF0.toInt()
    }

    private lateinit var web: WebView
    private lateinit var status: TextView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        web = WebView(this).apply {
            setBackgroundColor(BG)
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.INVISIBLE
        }
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(TX)
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { connect() }
        }
        root.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        setupWebView()
        connect()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            textZoom = 100                  // jangan ikut skala font sistem: tampilan harus sama dengan WebUI
            setSupportZoom(false)
            builtInZoomControls = false
        }
        web.addJavascriptInterface(KsuBridge(web), "ksu")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                if (uri.toString().startsWith(BASE_URL)) return false
                openExternal(uri)      // link TikTok/Telegram di banner -> browser/app luar
                return true            // jangan pernah memuat halaman asing di WebView yang punya akses root
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                runOnUiThread { recreate() }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                chooser?.onReceiveValue(null)
                chooser = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    chooser = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }
    }

    /** Cek root -> cek module -> ambil webroot dari module (fallback: salinan di assets) -> tampilkan. */
    private fun connect() {
        showStatus("Menghubungkan ke module…")
        status.isClickable = false
        worker.execute {
            val id = RootShell.exec("id -u", 60)
            if (id.code != 0 || id.out.trim() != "0") {
                fail(
                    "Akses root ditolak atau tidak tersedia.\n\n" +
                        "Buka Manager (Magisk / KernelSU / APatch), izinkan root untuk Azenith-AI, " +
                        "lalu ketuk layar ini untuk mencoba lagi." +
                        (if (id.err.isNotBlank()) "\n\n${id.err.trim().take(200)}" else "")
                )
                return@execute
            }

            val cli = RootShell.exec("[ -f $MODULE_DIR/system/bin/azenith-ai ] && echo ok", 20)
            if (cli.out.trim() != "ok") {
                fail(
                    "Module Azenith-AI belum aktif.\n\n" +
                        "Pastikan zip module sudah di-flash lewat Manager dan perangkat sudah di-reboot, " +
                        "lalu ketuk layar ini."
                )
                return@execute
            }

            val live = RootShell.exec("cat $MODULE_DIR/webroot/index.html", 30)
            val html =
                if (live.code == 0 && live.out.length > 2000 && live.out.contains("</html>")) live.out
                else assets.open("webroot/index.html").bufferedReader().use { it.readText() }

            runOnUiThread {
                status.visibility = View.GONE
                web.visibility = View.VISIBLE
                web.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)
            }
        }
    }

    private fun showStatus(msg: String) {
        status.text = msg
        status.visibility = View.VISIBLE
        web.visibility = View.INVISIBLE
    }

    private fun fail(msg: String) {
        runOnUiThread {
            showStatus(msg)
            status.isClickable = true
        }
    }

    private fun openExternal(uri: Uri) {
        val scheme = uri.scheme ?: return
        if (scheme != "http" && scheme != "https" && scheme != "tg") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            chooser = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        chooser?.onReceiveValue(null)
        chooser = null
        worker.shutdownNow()
        web.destroy()
        super.onDestroy()
    }
}
