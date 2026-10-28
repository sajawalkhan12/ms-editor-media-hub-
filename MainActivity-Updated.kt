package com.mseditor.mediahub

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var errorView: TextView? = null

    private val homeUrl = "https://sajawalkhan12.github.io/ms-editor-media-hub-/"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileCallback
        fileCallback = null

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val selectedUris = mutableListOf<Uri>()
            data?.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let(selectedUris::add)
                }
            }
            if (selectedUris.isEmpty()) data?.data?.let(selectedUris::add)
            callback?.onReceiveValue(selectedUris.takeIf { it.isNotEmpty() }?.toTypedArray())
        } else {
            callback?.onReceiveValue(null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 16, 47)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 27)

        try {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(android.graphics.Color.rgb(8, 13, 27))
            }
            progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                visibility = View.GONE
            }
            webView = WebView(this).apply {
                setBackgroundColor(android.graphics.Color.rgb(8, 13, 27))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadsImagesAutomatically = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        progressBar?.visibility = View.VISIBLE
                        errorView?.visibility = View.GONE
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        progressBar?.visibility = View.GONE
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return false
                        val scheme = uri.scheme.orEmpty()
                        if (scheme == "http" || scheme == "https") {
                            val host = uri.host.orEmpty()
                            return if (host == "sajawalkhan12.github.io" || host.endsWith(".github.io")) {
                                false
                            } else {
                                openExternal(uri)
                                true
                            }
                        }
                        openExternal(uri)
                        return true
                    }

                    @Suppress("DEPRECATION")
                    override fun onReceivedError(
                        view: WebView?, errorCode: Int, description: String?, failingUrl: String?
                    ) {
                        if (view?.url == failingUrl) showLoadError()
                    }

                    override fun onReceivedError(
                        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) showLoadError()
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        (view?.parent as? android.view.ViewGroup)?.removeView(view)
                        view?.destroy()
                        webView = null
                        showLoadError("The web component stopped unexpectedly. Tap Retry to reopen the app page.")
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progressBar?.progress = newProgress
                        progressBar?.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        this@MainActivity.fileCallback?.onReceiveValue(null)
                        this@MainActivity.fileCallback = filePathCallback
                        val intent = try {
                            fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                        } catch (_: Exception) {
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                        }
                        return try {
                            filePicker.launch(intent)
                            true
                        } catch (_: ActivityNotFoundException) {
                            this@MainActivity.fileCallback?.onReceiveValue(null)
                            this@MainActivity.fileCallback = null
                            Toast.makeText(this@MainActivity, "File picker is not available on this device.", Toast.LENGTH_LONG).show()
                            false
                        }
                    }
                }

                setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (!mimeType.isNullOrBlank()) setDataAndType(Uri.parse(url), mimeType)
                        }
                        startActivity(Intent.createChooser(intent, "Download or open file"))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Could not open this download. Try using the website in a browser.", Toast.LENGTH_LONG).show()
                    }
                })
            }

            root.addView(progressBar, LinearLayout.LayoutParams.MATCH_PARENT, dp(3))
            root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            errorView = TextView(this).apply {
                visibility = View.GONE
                setTextColor(android.graphics.Color.WHITE)
                textSize = 16f
                setPadding(dp(24), dp(24), dp(24), dp(24))
                setOnClickListener { reloadHome() }
            }
            root.addView(errorView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setContentView(root)

            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val current = webView
                    if (current != null && current.canGoBack()) current.goBack() else finish()
                }
            })

            if (savedInstanceState == null) webView?.loadUrl(homeUrl)
            else webView?.restoreState(savedInstanceState)
        } catch (exception: Exception) {
            showLoadError("App could not start: ${exception.javaClass.simpleName}\n${exception.message ?: "Unknown error"}")
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "No app found to open this link.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoadError(message: String = "Internet connection or website loading problem. Check your internet, then tap here to retry.") {
        runOnUiThread {
            progressBar?.visibility = View.GONE
            errorView?.apply {
                text = "$message\n\nTAP HERE TO RETRY"
                visibility = View.VISIBLE
            }
        }
    }

    private fun reloadHome() {
        errorView?.visibility = View.GONE
        if (webView == null) recreate() else webView?.loadUrl(homeUrl)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
