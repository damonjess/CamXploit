package com.spyboy.camxploit.osint

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("osint_prefs")

class OsintViewModel(app: Application) : AndroidViewModel(app) {

    sealed class Source {
        object ZoomEye : Source()
        object PublicCams : Source()
        object Dorks : Source()
    }

    private val ZOOMEYE_KEY = stringPreferencesKey("zoomeye_api_key")

    private val _source = MutableStateFlow<Source>(Source.ZoomEye)
    val source: StateFlow<Source> = _source.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _query = MutableStateFlow("webcam")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ZoomEye results
    private val _zoomEyeResults = MutableStateFlow<List<ZoomEyeClient.Host>>(emptyList())
    val zoomEyeResults: StateFlow<List<ZoomEyeClient.Host>> = _zoomEyeResults.asStateFlow()

    // Dorks
    private val _dork = MutableStateFlow("")
    val dork: StateFlow<String> = _dork.asStateFlow()

    private val _credits = MutableStateFlow<ZoomEyeClient.UserInfo?>(null)
    val credits: StateFlow<ZoomEyeClient.UserInfo?> = _credits.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = app.dataStore.data.map { it[ZOOMEYE_KEY] ?: "" }.first()
            _apiKey.value = saved
            if (saved.isNotBlank()) fetchCredits(saved)
        }
    }

    fun selectSource(s: Source) {
        _source.value = s
        _error.value = null
    }

    fun setApiKey(k: String) {
        _apiKey.value = k
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[ZOOMEYE_KEY] = k }
            if (k.length > 20) fetchCredits(k)
        }
    }

    private fun fetchCredits(key: String) {
        viewModelScope.launch {
            try {
                _credits.value = ZoomEyeClient.getUserInfo(key)
            } catch (_: Exception) {}
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun applyPreset(preset: String) { _query.value = preset }

    // ─── ZOOMEYE ───
    fun runZoomEye() {
        val key = _apiKey.value.trim()
        val q = _query.value.trim()
        if (key.isBlank()) { _error.value = "Enter ZoomEye API Key (zoomeye.org)"; return }
        if (q.isBlank()) { _error.value = "Enter search query"; return }

        _error.value = null
        _zoomEyeResults.value = emptyList()
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val hosts = ZoomEyeClient.search(key, q)
                _zoomEyeResults.value = hosts
                if (hosts.isEmpty()) {
                    _error.value = "No results found for '$q'"
                }
                fetchCredits(key)
            } catch (e: Exception) {
                val msg = e.message ?: "Connection error"
                _error.value = when {
                    msg.contains("402") -> "Insufficient credits for host search. Check your ZoomEye plan."
                    msg.contains("401") -> "Invalid API Key. Please update it."
                    msg.contains("403") -> "Access Denied. Check your permissions."
                    else -> msg
                }
                fetchCredits(key)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── DORKS ───
    fun generateDork() {
        _dork.value = ShodanClient.generateDork(_query.value)
    }

    fun lookupIp(ip: String) {
        val key = _apiKey.value.trim()
        if (key.isBlank()) { _error.value = "Enter API key"; return }
        _error.value = null
        _zoomEyeResults.value = emptyList()
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val hosts = ZoomEyeClient.search(key, ip)
                _zoomEyeResults.value = hosts
                if (hosts.isEmpty()) _error.value = "No results for IP: $ip"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }
}
