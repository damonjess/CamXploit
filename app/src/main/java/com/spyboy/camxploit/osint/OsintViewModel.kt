package com.spyboy.camxploit.osint

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Application.dataStore by preferencesDataStore("osint_prefs")

class OsintViewModel(app: Application) : AndroidViewModel(app) {

    sealed class Source { object Censys : Source(); object PublicCams : Source(); object Dorks : Source() }

    private val CENSYS_ID = stringPreferencesKey("censys_id")
    private val CENSYS_SECRET = stringPreferencesKey("censys_secret")

    private val _source = MutableStateFlow<Source>(Source.PublicCams)
    val source: StateFlow<Source> = _source.asStateFlow()

    // Censys
    private val _censysId = MutableStateFlow("")
    val censysId: StateFlow<String> = _censysId.asStateFlow()
    private val _censysSecret = MutableStateFlow("")
    val censysSecret: StateFlow<String> = _censysSecret.asStateFlow()
    private val _censysResults = MutableStateFlow<List<CensysClient.Host>>(emptyList())
    val censysResults: StateFlow<List<CensysClient.Host>> = _censysResults.asStateFlow()

    // Insecam
    private val _countries = MutableStateFlow<List<InsecamCountry>>(emptyList())
    val countries: StateFlow<List<InsecamCountry>> = _countries.asStateFlow()
    private val _insecamCameras = MutableStateFlow<List<InsecamClient.PublicCamera>>(emptyList())
    val cameras: StateFlow<List<InsecamClient.PublicCamera>> = _insecamCameras.asStateFlow()
    val insecamCameras: StateFlow<List<InsecamClient.PublicCamera>> = cameras // Alias for backward compatibility if needed

    private val _publicCameras = MutableStateFlow<List<com.spyboy.camxploit.StreamSource>>(emptyList())
    val publicCameras: StateFlow<List<com.spyboy.camxploit.StreamSource>> = _publicCameras.asStateFlow()

    private val _insecamLoading = MutableStateFlow(false)
    val insecamLoading: StateFlow<Boolean> = _insecamLoading.asStateFlow()
    private val _insecamError = MutableStateFlow<String?>(null)
    val insecamError: StateFlow<String?> = _insecamError.asStateFlow()
    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private val _selectedCountry = MutableStateFlow<String?>(null)
    val selectedCountry: StateFlow<String?> = _selectedCountry.asStateFlow()
    private var currentPage = 1

    // Dorks
    private val _dorkQuery = MutableStateFlow("inurl:view.shtml intitle:live view")
    val dorkQuery: StateFlow<String> = _dorkQuery.asStateFlow()

    // Shared
    private val _query = MutableStateFlow("webcam")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            val data = getApplication<Application>().dataStore.data.first()
            _censysId.value = data[CENSYS_ID] ?: ""
            _censysSecret.value = data[CENSYS_SECRET] ?: ""
        }
    }

    fun selectSource(s: Source) {
        _source.value = s
        _error.value = null
    }

    fun setCensysId(v: String) {
        _censysId.value = v
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[CENSYS_ID] = v } }
    }
    fun setCensysSecret(v: String) {
        _censysSecret.value = v
        viewModelScope.launch { getApplication<Application>().dataStore.edit { it[CENSYS_SECRET] = v } }
    }
    fun setQuery(q: String) { _query.value = q }
    fun setDorkQuery(q: String) { _dorkQuery.value = q }
    fun applyPreset(preset: String) { _query.value = preset }

    fun runCensys() {
        val id = _censysId.value.trim()
        val secret = _censysSecret.value.trim()
        val q = _query.value.trim()
        if (id.isBlank() || secret.isBlank()) { _error.value = "Enter Censys API ID and Secret"; return }
        if (q.isBlank()) { _error.value = "Enter query"; return }
        executeCensys(id, secret, q)
    }

    fun lookupIp(ip: String) {
        val id = _censysId.value.trim()
        val secret = _censysSecret.value.trim()
        if (id.isBlank() || secret.isBlank()) { _error.value = "Enter Censys API ID and Secret for IP lookup"; return }
        executeCensys(id, secret, ip)
    }

    private fun executeCensys(id: String, secret: String, q: String) {
        _error.value = null; _censysResults.value = emptyList(); _isLoading.value = true
        viewModelScope.launch {
            try {
                val hosts = CensysClient.search(id, secret, q)
                _censysResults.value = hosts
                if (hosts.isEmpty()) _error.value = "No results"
            } catch (e: Exception) { _error.value = e.message ?: "Request failed" }
            finally { _isLoading.value = false }
        }
    }

    data class InsecamCountry(val code: String, val name: String, val count: Int)

    fun loadCountries() {
        _countries.value = listOf(
            InsecamCountry("US", "United States", 4320),
            InsecamCountry("JP", "Japan", 2840),
            InsecamCountry("IT", "Italy", 1950),
            InsecamCountry("GB", "United Kingdom", 1670),
            InsecamCountry("BR", "Brazil", 1540),
            InsecamCountry("RU", "Russia", 1420),
            InsecamCountry("DE", "Germany", 1280),
            InsecamCountry("FR", "France", 1150),
            InsecamCountry("CA", "Canada", 980),
            InsecamCountry("ES", "Spain", 870),
            InsecamCountry("NL", "Netherlands", 760),
            InsecamCountry("PL", "Poland", 650),
            InsecamCountry("AU", "Australia", 540),
            InsecamCountry("KR", "South Korea", 480),
            InsecamCountry("TW", "Taiwan", 420),
            InsecamCountry("IN", "India", 390),
            InsecamCountry("TR", "Turkey", 350),
            InsecamCountry("SE", "Sweden", 310),
            InsecamCountry("CH", "Switzerland", 280),
            InsecamCountry("BE", "Belgium", 250)
        )
    }

    // Called by UI layer when scraper finds cameras
    fun setInsecamCameras(cameras: List<InsecamClient.PublicCamera>) {
        val uniqueCameras = cameras.distinctBy { it.id }
        _insecamCameras.value = uniqueCameras
        // Convert to publicCameras for components that use StreamSource
        _publicCameras.value = uniqueCameras.map { cam ->
            com.spyboy.camxploit.StreamSource(
                id = cam.id,
                url = "http://www.insecam.org/en/view/${cam.id}/",
                title = cam.location ?: cam.ip ?: "Public Camera",
                location = cam.location ?: "Unknown",
                protocol = "mjpeg" // Insecam is usually mjpeg-wrapped in html
            )
        }
    }

    fun saveCamera(source: com.spyboy.camxploit.StreamSource) {
        viewModelScope.launch {
            val cameraDao = com.spyboy.camxploit.CameraDatabase.getDatabase(getApplication()).cameraDao()
            val savedCam = com.spyboy.camxploit.SavedCamera(
                nickname = source.title,
                ip = source.location,
                streamUrl = source.url,
                streamType = source.protocol.uppercase(),
                brand = source.brand ?: "Public"
            )
            cameraDao.insertCamera(savedCam)
        }
    }

    fun removeDeadCamera(id: String) {
        _insecamCameras.value = _insecamCameras.value.filter { it.id != id }
    }

    fun selectCountry(code: String) {
        _selectedCountry.value = code
        currentPage = 1
        _insecamCameras.value = emptyList()
        _hasMorePages.value = true
        loadInsecamCountry(code)
    }

    fun clearCountrySelection() {
        _selectedCountry.value = null
        _insecamCameras.value = emptyList()
        _publicCameras.value = emptyList()
        _hasMorePages.value = true
        currentPage = 1
    }

    fun loadInsecamCountry(code: String, append: Boolean = false) {
        if (_insecamLoading.value) return
        
        viewModelScope.launch {
            _insecamLoading.value = true
            _insecamError.value = null
            
            try {
                val results = InsecamScraper.scrapeListing(code, if (append) currentPage + 1 else 1)
                if (append) {
                    _insecamCameras.value = _insecamCameras.value + results
                    currentPage++
                } else {
                    _insecamCameras.value = results
                    currentPage = 1
                }
                _hasMorePages.value = results.size >= 6 // Insecam usually has 6 per page
            } catch (e: Exception) {
                _insecamError.value = "Failed to load cameras: ${e.message}"
            } finally {
                _insecamLoading.value = false
            }
        }
    }

    fun loadPublicCameras(rawPageUrls: List<String>) {
        viewModelScope.launch {
            _insecamLoading.value = true
            try {
                val scraped = InsecamScraper.scrapeBatch(rawPageUrls)
                val sources = scraped.map { result ->
                    com.spyboy.camxploit.StreamSource(
                        id = result.pageUrl.substringAfterLast("/").substringBefore("/"),
                        url = result.streamUrl.ifBlank { result.pageUrl },
                        title = result.title.ifBlank { "Public Camera" },
                        location = result.location.ifBlank { "Unknown" },
                        thumbnailUrl = result.thumbnailUrl,
                        protocol = "mjpeg"
                    )
                }
                _publicCameras.value = sources
            } finally {
                _insecamLoading.value = false
            }
        }
    }

    fun scrapeAndAdd(pageUrl: String) {
        viewModelScope.launch {
            val result = InsecamScraper.scrapePage(pageUrl)
            addCamera(
                com.spyboy.camxploit.StreamSource(
                    id = pageUrl.substringAfterLast("/").substringBefore("/"),
                    url = result.streamUrl.ifBlank { pageUrl },
                    title = result.title.ifBlank { "Public Camera" },
                    location = result.location.ifBlank { "Unknown" },
                    thumbnailUrl = result.thumbnailUrl,
                    protocol = "mjpeg"
                )
            )
        }
    }

    fun loadNextInsecamPage() {
        _selectedCountry.value?.let { loadInsecamCountry(it, append = true) }
    }

    fun setInsecamLoading(loading: Boolean) {
        _insecamLoading.value = loading
    }
    fun setInsecamError(error: String?) {
        _insecamError.value = error
    }

    fun addCamera(camera: com.spyboy.camxploit.StreamSource) {
        _publicCameras.value = _publicCameras.value + camera
    }

    fun clearCameras() {
        _publicCameras.value = emptyList()
    }

    fun clearError() { _error.value = null }
}
