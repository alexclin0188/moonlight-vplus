package com.alexclin.moonlink.android.device

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import com.limelight.utils.UiHelper
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import com.alexclin.moonlink.android.R

/**
 * 内嵌 WebView 打开 Sunshine 主机的 Web 管理界面。
 *
 * 关键点：
 * 1. 通过传入的 serverCert（Moonlight 配对时持有的服务端证书）做 TLS pinning，
 *    匹配则忽略浏览器对自签证书的"不安全"警告，否则按系统默认行为拒绝。
 * 2. HTTP Basic Auth 弹出原生对话框收集凭据，并写回 WebView 的 HttpAuthDatabase
 *    便于同会话/同 host 复用，不持久化跨进程明文密码。
 */
class SunshineWebUiActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var pinnedCertSha256: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 隐藏顶部 title bar（默认 Activity 主题自带）；必须在 setContentView 之前调用
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        UiHelper.notifyNewRootView(this)
        setContentView(R.layout.activity_sunshine_webui)
        // 进入沉浸式：必须在 setContentView 之后，否则 DecorView 未初始化导致 NPE
        applyImmersiveMode()

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, R.string.pcview_open_webui_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.pcview_menu_open_webui)
        pinnedCertSha256 = parsePinnedCertSha256(intent.getByteArrayExtra(EXTRA_SERVER_CERT))

        webView = findViewById(R.id.sunshine_webui)
        progressBar = findViewById(R.id.sunshine_webui_progress)

        webView.settings.apply {
            javaScriptEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                val certBytes = intent.getByteArrayExtra(EXTRA_SERVER_CERT)
                if (certBytes != null && error != null) {
                    try {
                        val cert = CertificateFactory.getInstance("X.509")
                            .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                        val digest = MessageDigest.getInstance("SHA-256")
                        val certHash = digest.digest(cert.encoded)

                        val serverCertHash = pinnedCertSha256
                        if (serverCertHash != null && certHash.contentEquals(serverCertHash)) {
                            handler?.proceed()
                            return
                        }
                    } catch (_: Exception) {}
                }
                handler?.cancel()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                if (handler != null && handler.useHttpAuthUsernamePassword()) {
                    // WebView.httpAuthUsernamePassword() 已在较新 API 中移除，改用 WebViewDatabase
                    val wvDb = android.webkit.WebViewDatabase.getInstance(this@SunshineWebUiActivity)
                    val credentials = wvDb?.getHttpAuthUsernamePassword(host, realm)
                    if (credentials != null && credentials.size == 2) {
                        handler.proceed(credentials[0], credentials[1])
                        return
                    }
                }
                showHttpAuthDialog(handler, host, realm)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrEmpty() && title != "about:blank") {
                    this@SunshineWebUiActivity.title = title
                }
            }
        }

        webView.loadUrl(url)
    }

    private fun showHttpAuthDialog(handler: HttpAuthHandler?, host: String?, realm: String?) {
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }

        val usernameInput = EditText(this).apply {
            hint = "Username"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val passwordInput = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        inputLayout.addView(usernameInput)
        inputLayout.addView(passwordInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.sunshine_webui_auth_title)
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val username = usernameInput.text.toString()
                val password = passwordInput.text.toString()
                if (handler != null && handler.useHttpAuthUsernamePassword()) {
                    handler.proceed(username, password)
                    webView.setHttpAuthUsernamePassword(host, realm, username, password)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun parsePinnedCertSha256(certDer: ByteArray?): ByteArray? {
        if (certDer == null) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(certDer)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_URL = "com.limelight.SunshineWebUi.URL"
        const val EXTRA_TITLE = "com.limelight.SunshineWebUi.TITLE"
        const val EXTRA_SERVER_CERT = "com.limelight.SunshineWebUi.SERVER_CERT"

        @JvmStatic
        fun createIntent(
            context: android.content.Context,
            url: String,
            title: String?,
            serverCertDer: ByteArray?
        ): Intent = Intent(context, SunshineWebUiActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            if (title != null) putExtra(EXTRA_TITLE, title)
            if (serverCertDer != null) putExtra(EXTRA_SERVER_CERT, serverCertDer)
        }
    }
}
