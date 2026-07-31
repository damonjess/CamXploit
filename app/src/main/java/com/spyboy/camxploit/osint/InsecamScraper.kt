package com.spyboy.camxploit.osint

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class InsecamScraper {

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

    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var webView: WebView? = null
    private var lastCountryCode = ""
    private var currentPage = 1
    private var isAccumulating = false 
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun attachWebView(wv: WebView) {
        webView = wv.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            
            // Modernized User Agent string: Avoids instant Cloudflare blocks
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            
            settings.blockNetworkImage = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE 
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(JsBridge { extracted ->
                handler.post {
                    cancelTimeout()
                    if (extracted.isNotEmpty()) {
                        val currentList = if (isAccumulating) _cameras.value.toMutableList() else mutableListOf()
                        // Filter out duplicates
                        val newItems = extracted.filter { newItem -> currentList.none { it.id == newItem.id } }
                        _cameras.value = currentList + newItems
                        
                        // Insecam shows 6 per page; if we get less, likely last page
                        _hasMorePages.value = extracted.size >= 4 
                        _isLoading.value = false
                        _error.value = null
                        retryCount = 0
                    } else if (retryCount < 2) {
                        retryCount++
                        handler.postDelayed({ injectExtraction() }, 2000)
                    } else {
                        _isLoading.value = false
                        _hasMorePages.value = false
                        if (_cameras.value.isEmpty()) {
                            _error.value = "No cameras found. Cloudflare block or design layout updated."
                        }
                    }
                }
            }, "CamXploit")

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    handler.post {
                        _isLoading.value = true
                        _error.value = null
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    handler.postDelayed({ injectExtraction() }, 2000)
                    startTimeout()
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        handler.post {
                            cancelTimeout()
                            _isLoading.value = false
                            _hasMorePages.value = false
                            _error.value = "Connection Error: ${error?.description}"
                        }
                    }
                }
            }
        }
    }

    fun loadCountry(countryCode: String, page: Int = 1, append: Boolean = false) {
        lastCountryCode = countryCode.uppercase()
        currentPage = page
        isAccumulating = append
        retryCount = 0
        
        if (!append) {
            _cameras.value = emptyList()
            _hasMorePages.value = true
        }
        
        _isLoading.value = true
        _error.value = null
        
        val url = if (page == 1) {
            "http://www.insecam.org/en/bycountry/$lastCountryCode/"
        } else {
            "http://www.insecam.org/en/bycountry/$lastCountryCode/?page=$page"
        }
        webView?.loadUrl(url)
    }

    fun loadNextPage() {
        if (_isLoading.value || !_hasMorePages.value) return
        loadCountry(lastCountryCode, currentPage + 1, append = true)
    }

    private fun injectExtraction() {
        webView?.evaluateJavascript(EXTRACT_JS.replace("{{CODE}}", lastCountryCode), null)
    }

    private fun startTimeout() {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (_isLoading.value) {
                _isLoading.value = false
                _hasMorePages.value = false
                _error.value = "Server timed out. Check connection or browser verification wall."
            }
        }.also { handler.postDelayed(it, 20000) } 
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun retry() {
        if (lastCountryCode.isNotEmpty()) loadCountry(lastCountryCode, currentPage, isAccumulating)
    }

    fun detach() {
        cancelTimeout()
        handler.removeCallbacksAndMessages(null)
        webView?.post {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    private class JsBridge(private val onResult: (List<Camera>) -> Unit) {
        @android.webkit.JavascriptInterface
        fun onData(json: String) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<Camera>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val img = obj.optString("imageUrl")
                    if (img.isBlank() || img == "null" || img.contains("clear.gif")) continue
                    
                    list += Camera(
                        id = obj.optString("id", "0"),
                        imageUrl = img,
                        location = obj.optString("location", "Unknown").take(50),
                        countryCode = obj.optString("countryCode")
                    )
                }
                onResult(list)
            } catch (e: Exception) {
                Log.e("CamXploitParser", "JSON error", e)
                onResult(emptyList())
            }
        }
    }

    companion object {
        private val EXTRACT_JS = """
            (function() {
                var results = [];
                var items = document.querySelectorAll('.thumbnail, .thumbnail-container, [class*="col-"]');
                
                items.forEach(function(item) {
                    var img = item.querySelector('img');
                    var link = item.querySelector('a[href*="/view/"]');
                    var caption = item.querySelector('.caption h4, .caption h3, .caption, p');
                    var ipMatch = item.innerHTML.match(/([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)/);
                    
                    if (img && link) {
                        var idMatch = link.href.match(/\/view\/(\d+)/);
                        var cleanId = idMatch ? idMatch[1] : '';
                        
                        if (cleanId && !results.some(r => r.id === cleanId)) {
                            results.push({
                                id: cleanId,
                                imageUrl: img.src,
                                location: caption ? caption.innerText.replace(/\s+/g, ' ').trim() : (ipMatch ? ipMatch[1] : 'Unknown Endpoint'),
                                countryCode: '{{CODE}}'
                            });
                        }
                    }
                });
                CamXploit.onData(JSON.stringify(results));
            })();
        """.trimIndent()
    }
}
