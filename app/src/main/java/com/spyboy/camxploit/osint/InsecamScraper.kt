package com.spyboy.camxploit.osint

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InsecamScraper(context: Context) {

    data class Camera(
        val id: String,
        val imageUrl: String,
        val location: String,
        val countryCode: String
    )

    private val _cameras = MutableStateFlow<List<Camera>>(emptyList())
    val cameras: StateFlow<List<Camera>> = _cameras.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // WebView is created externally and attached via attachWebView()
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(wv: WebView) {
        webView = wv.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.0"
            settings.blockNetworkImage = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            addJavascriptInterface(JsBridge { extracted ->
                _cameras.value = extracted
                _isLoading.value = false
            }, "CamXploit")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(EXTRACT_JS.replace("{{CODE}}", lastCountryCode), null)
                }
            }
        }
    }

    private var lastCountryCode = ""

    fun loadCountry(countryCode: String) {
        lastCountryCode = countryCode
        _isLoading.value = true
        _cameras.value = emptyList()
        webView?.loadUrl("http://www.insecam.org/en/bycountry/$countryCode/")
    }

    fun detach() {
        webView?.destroy()
        webView = null
    }

    private class JsBridge(private val onResult: (List<Camera>) -> Unit) {
        @android.webkit.JavascriptInterface
        fun onData(json: String) {
            try {
                val arr = org.json.JSONArray(json)
                val list = mutableListOf<Camera>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list += Camera(
                        id = obj.optString("id", "0"),
                        imageUrl = obj.optString("imageUrl"),
                        location = obj.optString("location", "Unknown"),
                        countryCode = obj.optString("countryCode")
                    )
                }
                onResult(list)
            } catch (_: Exception) {
                onResult(emptyList())
            }
        }
    }

    companion object {
        private val EXTRACT_JS = """
            (function() {
                var results = [];
                var items = document.querySelectorAll('.thumbnail');
                items.forEach(function(item) {
                    var img = item.querySelector('img');
                    var link = item.querySelector('a');
                    var caption = item.querySelector('.caption h4, .caption h3, .caption');
                    var ipMatch = item.innerHTML.match(/([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)/);
                    if (img && link) {
                        var id = link.href.match(/\\/view\\/(\\d+)\\//);
                        results.push({
                            id: id ? id[1] : '',
                            imageUrl: img.src,
                            location: caption ? caption.innerText.trim() : (ipMatch ? ipMatch[1] : 'Unknown'),
                            countryCode: '{{CODE}}'
                        });
                    }
                });
                CamXploit.onData(JSON.stringify(results));
            })();
        """.trimIndent()
    }
}
