package com.sentinel.antiscamvn

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        WebView.setWebContentsDebuggingEnabled(false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.setOnLongClickListener { true }

        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://www.google.com")

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }
    }
}
