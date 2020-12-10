package com.mightylama.runblind

import android.app.Activity
import android.os.Bundle
import android.webkit.WebViewClient
import com.mightylama.runblind.databinding.ActivityMapBinding

class MapActivity : Activity() {

    private lateinit var binding: ActivityMapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.webView.apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            loadUrl("file:///android_asset/leafletjs.html")
        }
    }
}