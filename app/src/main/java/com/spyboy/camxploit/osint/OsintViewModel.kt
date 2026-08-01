package com.spyboy.camxploit.osint

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spyboy.camxploit.StreamSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OsintViewModel(app: Application) : AndroidViewModel(app) {

    sealed class Source { 
        object PublicCams : Source() 
        object Opentopia : Source()
        object DirectStream : Source()
        object Browser : Source()
    }

    private val _source = MutableStateFlow<Source>(Source.PublicCams)
    val source: StateFlow<Source> = _source.asStateFlow()

    // Insecam
    private val _countries = MutableStateFlow<List<InsecamCountry>>(emptyList())
    val countries: StateFlow<List<InsecamCountry>> = _countries.asStateFlow()
    private val _insecamCameras = MutableStateFlow<List<InsecamClient.PublicCamera>>(emptyList())
    val cameras: StateFlow<List<InsecamClient.PublicCamera>> = _insecamCameras.asStateFlow()

    private val _publicCameras = MutableStateFlow<List<StreamSource>>(emptyList())
    val publicCameras: StateFlow<List<StreamSource>> = _publicCameras.asStateFlow()

    private val _insecamLoading = MutableStateFlow(false)
    val insecamLoading: StateFlow<Boolean> = _insecamLoading.asStateFlow()
    private val _insecamError = MutableStateFlow<String?>(null)
    val insecamError: StateFlow<String?> = _insecamError.asStateFlow()
    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private val _selectedCountry = MutableStateFlow<String?>(null)
    val selectedCountry: StateFlow<String?> = _selectedCountry.asStateFlow()
    private var currentPage = 1

    // Shared
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadCountries()
    }

    fun selectSource(s: Source) {
        _source.value = s
        _error.value = null
        if (s == Source.PublicCams) {
            _selectedCountry.value = null
            _publicCameras.value = emptyList()
        }
    }

    fun loadOpentopiaCameras(limit: Int = 50) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _selectedCountry.value = null // Clear country selection when loading Opentopia
            try {
                val cameras = OpentopiaScraper.fetchCameras(limit)
                _publicCameras.value = cameras
                _source.value = Source.Opentopia
                if (cameras.isEmpty()) {
                    _error.value = "Opentopia returned no cameras. Site layout may have changed."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Opentopia failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchPublicCameras(query: String) {
        // Placeholder if needed, but Insecam is mostly country-based in current implementation
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

    fun setInsecamCameras(cameras: List<InsecamClient.PublicCamera>) {
        val uniqueCameras = cameras.distinctBy { it.id }
        _insecamCameras.value = uniqueCameras
        _publicCameras.value = uniqueCameras.map { cam ->
            StreamSource(
                id = cam.id,
                url = "http://www.insecam.org/en/view/${cam.id}/",
                title = cam.location ?: cam.ip ?: "Public Camera",
                location = cam.location ?: "Unknown",
                protocol = "mjpeg"
            )
        }
    }

    fun saveCamera(source: StreamSource) {
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
        _publicCameras.value = emptyList()
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
                _hasMorePages.value = results.size >= 6
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
                _publicCameras.value = scraped.map { result ->
                    StreamSource(
                        id = result.pageUrl.substringAfterLast("/").substringBefore("/"),
                        url = result.streamUrl.ifBlank { result.pageUrl },
                        title = result.title.ifBlank { "Public Camera" },
                        location = result.location.ifBlank { "Unknown" },
                        thumbnailUrl = result.thumbnailUrl,
                        protocol = "mjpeg"
                    )
                }
            } finally {
                _insecamLoading.value = false
            }
        }
    }

    fun scrapeAndAdd(pageUrl: String) {
        viewModelScope.launch {
            val result = InsecamScraper.scrapePage(pageUrl)
            addCamera(
                StreamSource(
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

    fun setInsecamLoading(loading: Boolean) { _insecamLoading.value = loading }
    fun setInsecamError(error: String?) { _insecamError.value = error }
    fun addCamera(camera: StreamSource) { _publicCameras.value = _publicCameras.value + camera }
    fun clearCameras() { _publicCameras.value = emptyList(); _insecamCameras.value = emptyList() }
    fun clearError() { _error.value = null }
}
