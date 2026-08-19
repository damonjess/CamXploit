package com.spyboy.camxploit.osint

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spyboy.camxploit.StreamSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OsintViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val INSECAM_PAGE_SIZE = 6
    }

    sealed class Source { 
        object PublicCams : Source() 
        object Opentopia : Source()
        object GitHub : Source()
        object MyCameras : Source()
        object DirectStream : Source()
        object Browser : Source()
    }

    private val _source = MutableStateFlow<Source>(Source.PublicCams)
    val source: StateFlow<Source> = _source.asStateFlow()

    private val _sourceHealth = MutableStateFlow(
        IntelSourceId.values().associateWith { SourceHealth() }
    )
    val sourceHealth: StateFlow<Map<IntelSourceId, SourceHealth>> = _sourceHealth.asStateFlow()

    private fun updateSourceHealth(
        sourceId: IntelSourceId,
        status: SourceHealthStatus,
        message: String,
        itemCount: Int = 0
    ) {
        _sourceHealth.value = _sourceHealth.value + (
            sourceId to SourceHealth(status, message, itemCount, System.currentTimeMillis())
        )
    }

    // Insecam
    private val _countries = MutableStateFlow<List<InsecamCountry>>(emptyList())
    val countries: StateFlow<List<InsecamCountry>> = _countries.asStateFlow()
    private val _insecamCameras = MutableStateFlow<List<InsecamClient.PublicCamera>>(emptyList())
    val cameras: StateFlow<List<InsecamClient.PublicCamera>> = _insecamCameras.asStateFlow()

    private val _publicCameras = MutableStateFlow<List<StreamSource>>(emptyList())
    val publicCameras: StateFlow<List<StreamSource>> = _publicCameras.asStateFlow()

    private val _cameraDiagnostics = MutableStateFlow<Map<String, CameraDiagnostics>>(emptyMap())
    val cameraDiagnostics: StateFlow<Map<String, CameraDiagnostics>> = _cameraDiagnostics.asStateFlow()

    fun verifyCamera(camera: StreamSource) {
        val id = camera.id
        _cameraDiagnostics.value = _cameraDiagnostics.value + (
            id to CameraDiagnostics(
                source = camera.sourceLabel,
                effectiveUrl = camera.bestPlaybackUrl(),
                verification = CameraVerification.VERIFYING,
                message = "Checking feed…"
            )
        )
        viewModelScope.launch {
            try {
                val result = CameraUrlProbe.probe(camera.bestPlaybackUrl())
                val verification = when {
                    result.isMjpeg -> CameraVerification.MJPEG
                    result.isSnapshot -> CameraVerification.SNAPSHOT
                    result.url.startsWith("rtsp://", ignoreCase = true) -> CameraVerification.RTSP
                    result.isHtml -> CameraVerification.WEB
                    else -> CameraVerification.UNAVAILABLE
                }
                val diagnostics = CameraDiagnostics(
                    source = camera.sourceLabel,
                    effectiveUrl = result.url,
                    contentType = result.contentType,
                    verification = verification,
                    checkedAt = System.currentTimeMillis(),
                    message = if (verification == CameraVerification.UNAVAILABLE) "No supported camera response detected" else "Verified"
                )
                _cameraDiagnostics.value = _cameraDiagnostics.value + (id to diagnostics)
                _publicCameras.value = _publicCameras.value.map {
                    if (it.id == id) it.copy(
                        verification = verification.label,
                        contentType = result.contentType,
                        verifiedAt = diagnostics.checkedAt ?: 0L
                    ) else it
                }
            } catch (e: Exception) {
                _cameraDiagnostics.value = _cameraDiagnostics.value + (
                    id to CameraDiagnostics(
                        source = camera.sourceLabel,
                        effectiveUrl = camera.bestPlaybackUrl(),
                        verification = CameraVerification.UNAVAILABLE,
                        checkedAt = System.currentTimeMillis(),
                        message = e.message ?: "Verification failed"
                    )
                )
            }
        }
    }

    private val _insecamLoading = MutableStateFlow(false)
    val insecamLoading: StateFlow<Boolean> = _insecamLoading.asStateFlow()
    private val _insecamError = MutableStateFlow<String?>(null)
    val insecamError: StateFlow<String?> = _insecamError.asStateFlow()
    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    private val _selectedCountry = MutableStateFlow<String?>(null)
    val selectedCountry: StateFlow<String?> = _selectedCountry.asStateFlow()
    private val _currentCountryPage = MutableStateFlow(1)
    val currentCountryPage: StateFlow<Int> = _currentCountryPage.asStateFlow()
    private var currentPage = 1

    private val _recentlyViewed = MutableStateFlow<List<StreamSource>>(emptyList())
    val recentlyViewed: StateFlow<List<StreamSource>> = _recentlyViewed.asStateFlow()

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

    fun addToRecentlyViewed(camera: StreamSource) {
        val current = _recentlyViewed.value.toMutableList()
        current.removeAll { it.id == camera.id || it.url == camera.url }
        current.add(0, camera)
        _recentlyViewed.value = current.take(10)
    }

    fun refreshCurrentSource() {
        when (val s = _source.value) {
            is Source.Opentopia -> loadOpentopiaCameras(_publicCameras.value.size.coerceAtLeast(50))
            is Source.GitHub -> loadGitHubMotionJpegSources()
            is Source.MyCameras -> loadMyCameras()
            is Source.PublicCams -> {
                if (_selectedCountry.value != null) {
                    loadInsecamCountry(_selectedCountry.value!!)
                } else {
                    loadCountries()
                }
            }
            else -> {}
        }
    }

    fun loadOpentopiaCameras(limit: Int = 50) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            updateSourceHealth(IntelSourceId.OPENTOPIA, SourceHealthStatus.LOADING, "Loading directory…")
            _selectedCountry.value = null // Clear country selection when loading Opentopia
            try {
                val cameras = OpentopiaScraper.fetchCameras(limit)
                _publicCameras.value = cameras
                _source.value = Source.Opentopia
                if (cameras.isEmpty()) {
                    _error.value = "Opentopia returned no cameras. The source may be empty or its layout may have changed."
                    updateSourceHealth(IntelSourceId.OPENTOPIA, SourceHealthStatus.ERROR, "No usable camera cards returned")
                } else {
                    updateSourceHealth(IntelSourceId.OPENTOPIA, SourceHealthStatus.HEALTHY, "Loaded ${cameras.size} cameras", cameras.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Opentopia failed: ${e.message}"
                updateSourceHealth(IntelSourceId.OPENTOPIA, SourceHealthStatus.ERROR, e.message ?: "Source request failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Load direct MotionJPEG sources from GitHub repo */
    fun loadGitHubMotionJpegSources() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            updateSourceHealth(IntelSourceId.GITHUB, SourceHealthStatus.LOADING, "Loading curated stream list…")
            _selectedCountry.value = null
            try {
                val sources = GitHubMotionJpegClient.fetchSources()
                _publicCameras.value = sources
                _source.value = Source.GitHub
                if (sources.isEmpty()) {
                    _error.value = "No supported stream URLs were found in the source list."
                    updateSourceHealth(IntelSourceId.GITHUB, SourceHealthStatus.ERROR, "No supported stream URLs found")
                } else {
                    updateSourceHealth(IntelSourceId.GITHUB, SourceHealthStatus.HEALTHY, "Loaded ${sources.size} stream URLs", sources.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "GitHub fetch failed: ${e.message}"
                updateSourceHealth(IntelSourceId.GITHUB, SourceHealthStatus.ERROR, e.message ?: "Source request failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMyCameras() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _selectedCountry.value = null
            updateSourceHealth(IntelSourceId.MY_CAMERAS, SourceHealthStatus.LOADING, "Loading saved cameras…")
            try {
                val cameraDao = com.spyboy.camxploit.CameraDatabase.getDatabase(getApplication()).cameraDao()
                val cameras = cameraDao.getAllCameras().first().map { saved ->
                    saved.toStreamSource().copy(
                        id = "saved-${saved.id}",
                        sourceLabel = "My Cameras",
                        verification = saved.streamType.uppercase()
                    )
                }
                _publicCameras.value = cameras
                _source.value = Source.MyCameras
                updateSourceHealth(
                    IntelSourceId.MY_CAMERAS,
                    if (cameras.isEmpty()) SourceHealthStatus.PARTIAL else SourceHealthStatus.HEALTHY,
                    if (cameras.isEmpty()) "No saved cameras yet" else "Loaded ${cameras.size} saved camera(s)",
                    cameras.size
                )
            } catch (e: Exception) {
                _error.value = "Could not load saved cameras: ${e.message}"
                updateSourceHealth(IntelSourceId.MY_CAMERAS, SourceHealthStatus.ERROR, e.message ?: "Database read failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchPublicCameras(query: String) {
        // Filtering is applied by the Compose screen to preserve all loaded source results.
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
        _currentCountryPage.value = 1
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
        _currentCountryPage.value = 1
    }

    fun loadInsecamCountry(code: String, append: Boolean = false) {
        if (_insecamLoading.value) return
        viewModelScope.launch {
            _insecamLoading.value = true
            _insecamError.value = null
            updateSourceHealth(IntelSourceId.COUNTRY_DIRECTORY, SourceHealthStatus.LOADING, "Loading country page…")
            try {
                val requestedPage = if (append) currentPage + 1 else 1
                val listing = InsecamScraper.scrapeListing(code, requestedPage)
                val results = listing.cameras

                if (append) {
                    // Keep earlier pages and discard any duplicate camera IDs from the source.
                    _insecamCameras.value = (_insecamCameras.value + results).distinctBy { it.id }
                    if (results.isNotEmpty()) currentPage = requestedPage
                } else {
                    _insecamCameras.value = results
                    currentPage = 1
                }

                // Follow the source pagination control instead of assuming a fixed page size.
                _hasMorePages.value = listing.hasNextPage
                _currentCountryPage.value = currentPage
                updateSourceHealth(
                    IntelSourceId.COUNTRY_DIRECTORY,
                    SourceHealthStatus.HEALTHY,
                    "Loaded ${_insecamCameras.value.size} camera(s)",
                    _insecamCameras.value.size
                )
            } catch (e: Exception) {
                _insecamError.value = "Failed to load cameras: ${e.message}"
                updateSourceHealth(IntelSourceId.COUNTRY_DIRECTORY, SourceHealthStatus.ERROR, e.message ?: "Country request failed")
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
