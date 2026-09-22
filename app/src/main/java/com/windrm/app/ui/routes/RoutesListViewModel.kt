package com.windrm.app.ui.routes

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.windrm.app.gpx.GpxParser
import com.windrm.app.model.Route
import com.windrm.app.remote.strava.StravaAuthEvent
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.remote.strava.StravaRouteSummary
import com.windrm.app.repository.RouteRepository
import com.windrm.app.repository.StravaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutesListViewModel(
    private val routeRepository: RouteRepository,
    private val stravaRepository: StravaRepository,
    private val stravaAuthManager: StravaAuthManager,
) : ViewModel() {

    val routes: StateFlow<List<Route>> = routeRepository.observeRoutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Routes drawn with Strava's Route Builder (not recorded activities). */
    var stravaRoutes by mutableStateOf<List<StravaRouteSummary>>(emptyList())
        private set
    var stravaLoading by mutableStateOf(false)
        private set
    var stravaAuthorized by mutableStateOf(false)
        private set
    var stravaError by mutableStateOf<String?>(null)
        private set
    var importingRouteId by mutableStateOf<Long?>(null)
        private set
    var gpxError by mutableStateOf<String?>(null)
        private set

    val stravaConfigured: Boolean get() = stravaAuthManager.isConfigured

    init {
        viewModelScope.launch {
            stravaAuthorized = stravaAuthManager.isAuthorized()
            if (stravaAuthorized) refreshStravaRoutes()
        }
        // Authoritative signal for the OAuth round-trip: MainActivity hands the redirect to
        // StravaAuthManager, which exchanges the code for a token asynchronously and publishes
        // the outcome here. This must not depend on Activity resume timing (see onStravaAuthorized
        // below) — that check races the token exchange's network call and would often lose.
        viewModelScope.launch {
            stravaAuthManager.authEvents.collect { event ->
                when (event) {
                    is StravaAuthEvent.Authorized -> {
                        stravaError = null
                        stravaAuthorized = true
                        refreshStravaRoutes()
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
                refreshStravaRoutes()
            }
        }
    }

    fun refreshStravaRoutes() {
        viewModelScope.launch {
            stravaLoading = true
            stravaError = null
            runCatching { stravaRepository.listRoutes() }
                .onSuccess { stravaRoutes = it }
                .onFailure { stravaError = it.message ?: "Errore nel caricamento delle routes Strava" }
            stravaLoading = false
        }
    }

    fun importStravaRoute(route: StravaRouteSummary, onImported: (Route) -> Unit) {
        viewModelScope.launch {
            importingRouteId = route.id
            stravaError = null
            runCatching {
                routeRepository.findByStravaRouteId(route.id) ?: run {
                    val imported = stravaRepository.importRouteAsRoute(route)
                    val id = routeRepository.saveRoute(imported)
                    imported.copy(id = id)
                }
            }
                .onSuccess { onImported(it) }
                .onFailure { stravaError = it.message ?: "Errore nell'importazione della route" }
            importingRouteId = null
        }
    }

    fun importGpx(context: Context, uri: Uri, onImported: (Route) -> Unit) {
        viewModelScope.launch {
            gpxError = null
            runCatching {
                val name = queryDisplayName(context, uri) ?: "Percorso importato"
                val route = context.contentResolver.openInputStream(uri)?.use { stream ->
                    GpxParser.parse(stream, name.substringBeforeLast('.'))
                } ?: error("Impossibile aprire il file selezionato")
                val id = routeRepository.saveRoute(route)
                route.copy(id = id)
            }
                .onSuccess { onImported(it) }
                .onFailure { gpxError = it.message ?: "Errore nell'importazione del file GPX" }
        }
    }

    fun deleteRoute(route: Route) {
        viewModelScope.launch { routeRepository.deleteRoute(route) }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else null
        }
    }
}
