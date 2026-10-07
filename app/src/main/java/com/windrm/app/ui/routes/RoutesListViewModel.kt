package com.windrm.app.ui.routes

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.gpx.GpxFolder
import com.windrm.app.gpx.GpxUriImporter
import com.windrm.app.model.Route
import com.windrm.app.remote.strava.StravaActivitySummary
import com.windrm.app.remote.strava.StravaAuthEvent
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.remote.strava.StravaRouteSummary
import com.windrm.app.remote.strava.StravaSegmentSummary
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.StravaRepository
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mirrors the Activities / Routes / Segments selector in Epic Ride Weather's Strava tab. */
enum class StravaSection { ACTIVITIES, ROUTES, SEGMENTS }

class RoutesListViewModel(
    private val routeRepository: RouteRepository,
    private val stravaRepository: StravaRepository,
    private val stravaAuthManager: StravaAuthManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val routes: StateFlow<List<Route>> = routeRepository.observeRoutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var stravaSection by mutableStateOf(StravaSection.ACTIVITIES)
        private set

    var stravaActivities by mutableStateOf<List<StravaActivitySummary>>(emptyList())
        private set
    var stravaRoutes by mutableStateOf<List<StravaRouteSummary>>(emptyList())
        private set
    var stravaSegments by mutableStateOf<List<StravaSegmentSummary>>(emptyList())
        private set

    var stravaLoading by mutableStateOf(false)
        private set
    var stravaAuthorized by mutableStateOf(false)
        private set
    var stravaError by mutableStateOf<String?>(null)
        private set
    var importingId by mutableStateOf<Long?>(null)
        private set
    var gpxError by mutableStateOf<String?>(null)
        private set

    val stravaConfigured: Boolean get() = stravaAuthManager.isConfigured

    /** The GPX folder chosen in Settings (null = none), its name, and the GPX and TCX files found in it. */
    var gpxFolderUri by mutableStateOf<Uri?>(null)
        private set
    var gpxFolderName by mutableStateOf<String?>(null)
        private set
    var gpxFiles by mutableStateOf<List<GpxFolder.Entry>>(emptyList())
        private set
    var filesLoading by mutableStateOf(false)
        private set
    var filesError by mutableStateOf<String?>(null)
        private set

    /** Reads the folder again; called each time the Files tab is shown, so files added elsewhere (Drive, a PC) appear. */
    fun loadFiles(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            filesLoading = true
            filesError = null
            val tree = settingsRepository.current().gpxFolderUri?.let(Uri::parse)
            gpxFolderUri = tree
            if (tree == null) {
                gpxFolderName = null
                gpxFiles = emptyList()
            } else {
                runCatching {
                    withContext(Dispatchers.IO) { GpxFolder.displayName(appContext, tree) to GpxFolder.listTracks(appContext, tree) }
                }
                    .onSuccess { (name, files) ->
                        gpxFolderName = name
                        gpxFiles = files
                    }
                    .onFailure {
                        gpxFiles = emptyList()
                        filesError = it.message ?: "Couldn't read the folder"
                    }
            }
            filesLoading = false
        }
    }

    /** Picked from the Files tab itself: remembered like a choice made in Settings. */
    fun setGpxFolder(context: Context, uri: Uri) {
        viewModelScope.launch {
            settingsRepository.setGpxFolder(uri)
            loadFiles(context)
        }
    }

    /** Opens the saved route a file already is; imports it (once) when it is new to the app. */
    fun openGpxFile(context: Context, entry: GpxFolder.Entry, onOpened: (Route) -> Unit) {
        viewModelScope.launch {
            gpxError = null
            val key = entry.documentUri.toString()
            val known = settingsRepository.linkedRouteId(key)?.let { routeRepository.getRoute(it) }
            if (known != null) {
                onOpened(known)
                return@launch
            }
            runCatching { GpxUriImporter.import(context, entry.documentUri, routeRepository) }
                .onSuccess {
                    settingsRepository.linkGpx(key, it.id)
                    onOpened(it)
                }
                .onFailure { gpxError = it.message ?: "Couldn't import the GPX file" }
        }
    }

    fun toggleFavorite(route: Route) {
        viewModelScope.launch { routeRepository.setFavorite(route.id, !route.isFavorite) }
    }

    init {
        viewModelScope.launch {
            stravaAuthorized = stravaAuthManager.isAuthorized()
            if (stravaAuthorized) refreshStravaSection()
        }
        // Authoritative signal for the OAuth round-trip: MainActivity hands the redirect to
        // StravaAuthManager, which exchanges the code for a token asynchronously and publishes
        // the outcome here. This must not depend on Activity resume timing (see onStravaAuthorized
        // below) -- that check races the token exchange's network call and would often lose.
        viewModelScope.launch {
            stravaAuthManager.authEvents.collect { event ->
                when (event) {
                    is StravaAuthEvent.Authorized -> {
                        stravaError = null
                        stravaAuthorized = true
                        refreshStravaSection()
                    }
                    is StravaAuthEvent.Failed -> stravaError = event.message
                }
            }
        }
    }

    fun connectStrava() = stravaAuthManager.launchAuthorization()

    /**
     * Best-effort re-check on resume (e.g. access revoked from strava.com in a browser tab).
     * The actual "just authorized" transition is driven by [StravaAuthManager.authEvents] above.
     */
    fun onStravaAuthorized() {
        viewModelScope.launch {
            val nowAuthorized = stravaAuthManager.isAuthorized()
            if (nowAuthorized && !stravaAuthorized) {
                stravaAuthorized = true
                refreshStravaSection()
            }
        }
    }

    fun selectStravaSection(section: StravaSection) {
        if (section == stravaSection) return
        stravaSection = section
        refreshStravaSection()
    }

    fun refreshStravaSection() {
        viewModelScope.launch {
            stravaLoading = true
            stravaError = null
            when (stravaSection) {
                StravaSection.ACTIVITIES -> runCatching { stravaRepository.listActivities() }
                    .onSuccess { stravaActivities = it }
                    .onFailure { stravaError = it.message ?: "Couldn't load Strava activities" }
                StravaSection.ROUTES -> runCatching { stravaRepository.listRoutes() }
                    .onSuccess { stravaRoutes = it }
                    .onFailure { stravaError = it.message ?: "Couldn't load Strava routes" }
                StravaSection.SEGMENTS -> runCatching { stravaRepository.listSegments() }
                    .onSuccess { stravaSegments = it }
                    .onFailure { stravaError = it.message ?: "Couldn't load Strava segments" }
            }
            stravaLoading = false
        }
    }

    fun importStravaActivity(activity: StravaActivitySummary, onImported: (Route) -> Unit) {
        importStrava(activity.id, "Couldn't import the activity", onImported) {
            val imported = stravaRepository.importActivityAsRoute(activity)
            imported.copy(id = routeRepository.saveRoute(imported))
        }
    }

    fun importStravaRoute(route: StravaRouteSummary, onImported: (Route) -> Unit) {
        importStrava(route.id, "Couldn't import the route", onImported) {
            routeRepository.findByStravaRouteId(route.id) ?: run {
                val imported = stravaRepository.importRouteAsRoute(route)
                imported.copy(id = routeRepository.saveRoute(imported))
            }
        }
    }

    fun importStravaSegment(segment: StravaSegmentSummary, onImported: (Route) -> Unit) {
        importStrava(segment.id, "Couldn't import the segment", onImported) {
            val imported = stravaRepository.importSegmentAsRoute(segment)
            imported.copy(id = routeRepository.saveRoute(imported))
        }
    }

    /** Runs [block], tracking loading/error state, then hands its result to [onImported]. */
    private fun importStrava(id: Long, errorFallback: String, onImported: (Route) -> Unit, block: suspend () -> Route) {
        viewModelScope.launch {
            importingId = id
            stravaError = null
            runCatching { block() }
                .onSuccess { onImported(it) }
                .onFailure { stravaError = it.message ?: errorFallback }
            importingId = null
        }
    }

    fun importGpx(context: Context, uri: Uri, onImported: (Route) -> Unit) {
        viewModelScope.launch {
            gpxError = null
            runCatching { GpxUriImporter.import(context, uri, routeRepository) }
                .onSuccess { onImported(it) }
                .onFailure { gpxError = it.message ?: "Couldn't import the GPX file" }
        }
    }

    fun deleteRoute(route: Route) {
        viewModelScope.launch { routeRepository.deleteRoute(route) }
    }
}
