package com.spyboy.camxploit.osint

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("osint_prefs")

class OsintViewModel(application: Application) : AndroidViewModel(application) {

    private val API_KEY = stringPreferencesKey("shodan_api_key")

    sealed class OsintTab { object Shodan : OsintTab(); object WebDork : OsintTab() }

    private val _activeTab = MutableStateFlow<OsintTab>(OsintTab.Shodan)
    val activeTab: StateFlow<OsintTab> = _activeTab.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _query = MutableStateFlow("webcam")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _results = mutableStateListOf<ShodanClient.ShodanHost>()
    val results: List<ShodanClient.ShodanHost> = _results

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _dorkResult = MutableStateFlow("")
    val dorkResult: StateFlow<String> = _dorkResult.asStateFlow()

    init {
        viewModelScope.launch {
            val savedKey = getApplication<Application>().dataStore.data
                .map { preferences -> preferences[API_KEY] ?: "" }
                .first()
            _apiKey.value = savedKey
        }
    }

    fun setTab(tab: OsintTab) { _activeTab.value = tab }

    fun setApiKey(k: String) {
        _apiKey.value = k
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { preferences ->
                preferences[API_KEY] = k
            }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun applyPreset(preset: String) {
        _query.value = preset
    }

    fun runShodanScan() {
        val key = _apiKey.value.trim()
        val q = _query.value.trim()
        if (key.isBlank() || q.isBlank()) {
            _error.value = "Enter API key and query"
            return
        }
        _error.value = null
        _results.clear()
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val hosts = ShodanClient.search(key, q)
                _results.addAll(hosts)
                if (hosts.isEmpty()) _error.value = "No results"
            } catch (e: Exception) {
                _error.value = e.message ?: "Network error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun lookupIp(ip: String) {
        val key = _apiKey.value.trim()
        if (key.isBlank()) { _error.value = "Enter API key"; return }
        _error.value = null
        _results.clear()
        _isLoading.value = true
        viewModelScope.launch {
            try {
                ShodanClient.lookupIp(key, ip)?.let { _results += it }
                    ?: run { _error.value = "No data for $ip" }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateDork() {
        _dorkResult.value = ShodanClient.generateDork(_query.value)
    }

    fun clearError() { _error.value = null }
}
