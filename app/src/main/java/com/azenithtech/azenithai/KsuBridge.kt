package com.azenithtech.azenithai

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Meniru API WebUI KernelSU/APatch: `ksu.exec(cmd, optionsJson, callbackName)`.
 * Callback dipanggil sebagai window[callbackName](errno, stdout, stderr).
 * Dengan begitu webroot/index.html milik module jalan TANPA diubah sama sekali di dalam app.
 *
 * Hanya dipasang di WebView app ini, yang cuma memuat halaman milik module (atau salinan
 * bawaan di assets). Navigasi ke luar diblokir di MainActivity.
 */
class KsuBridge(private val web: WebView) {

    private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
    private val cbName = Regex("[A-Za-z0-9_]{1,80}")

    @JavascriptInterface
    fun exec(cmd: String?, options: String?, callback: String?) {
        if (callback == null || !cbName.matches(callback)) return
        val script = cmd ?: ""
        pool.execute {
            val r = RootShell.exec(script)
            val js = "window['$callback']&&window['$callback'](" +
                "${r.code},${JSONObject.quote(r.out)},${JSONObject.quote(r.err)});"
            web.post { web.evaluateJavascript(js, null) }
        }
    }

    @JavascriptInterface
    fun toast(msg: String?) {
        web.post { Toast.makeText(web.context, msg ?: "", Toast.LENGTH_SHORT).show() }
    }
}
