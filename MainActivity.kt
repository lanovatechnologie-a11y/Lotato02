package com.lotato.pro.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lotato.pro.bridge.AndroidPrintBridge
import com.lotato.pro.databinding.ActivityMainBinding
import com.lotato.pro.print.PrintManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var printManager: PrintManager
    private lateinit var webView: WebView

    // =========================================================
    // URL de votre backend (même qu'avant)
    // Changez ici si vous utilisez un serveur local
    // =========================================================
    private val APP_URL = "https://lotato2.onrender.com"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Masquer la barre d'action (plein écran POS)
        supportActionBar?.hide()

        // Initialiser le gestionnaire d'impression
        printManager = PrintManager(this)

        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = binding.webView

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // localStorage / sessionStorage
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " LotoatoNative/1.0 AndroidPOS"
        }

        // =========================================================
        // PONT JAVASCRIPT ↔ ANDROID
        // Votre code JS appelle déjà window.AndroidPrint.printHTML(html)
        // C'est ici qu'on intercepte cet appel
        // =========================================================
        val printBridge = AndroidPrintBridge(this, printManager)
        webView.addJavascriptInterface(printBridge, "AndroidPrint")

        // WebViewClient : gestion navigation
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Injecter un script pour informer la WebApp qu'elle est dans l'app native
                view?.evaluateJavascript(
                    """
                    window.__NATIVE_ANDROID__ = true;
                    window.__ANDROID_VERSION__ = '${android.os.Build.VERSION.SDK_INT}';
                    window.__DEVICE_MODEL__ = '${android.os.Build.MODEL}';
                    console.log('[NATIVE] Lotato Android App chargée');
                    """.trimIndent(),
                    null
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // Si le serveur est inaccessible, charger une page d'erreur locale
                if (request?.isForMainFrame == true) {
                    view?.loadUrl("file:///android_asset/offline.html")
                }
            }

            // Autoriser tous les certificats pour le développement
            // EN PRODUCTION : retirez ce bloc
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.proceed()
            }
        }

        // WebChromeClient : alerts, confirms, console.log
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?, url: String?, message: String?, result: JsResult?
            ): Boolean {
                // Permettre les alerts JavaScript normales
                return false
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                android.util.Log.d("LotAtoPro_JS",
                    "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
                return true
            }
        }

        // Charger l'application
        webView.loadUrl(APP_URL)
    }

    // Gestion du bouton retour (navigation WebView)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        printManager.reconnect()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        printManager.disconnect()
    }
}
