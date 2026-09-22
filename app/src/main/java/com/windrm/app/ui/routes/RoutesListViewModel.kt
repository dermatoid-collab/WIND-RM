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
import com.windrm.app.remote.strava.StravaActivitySummary
import com.windrm.app.remote.strava.StravaAuthManager
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

    var stravaActivities by mutableStateOf<List<StravaActivitySummary>>(emptyList())
        private set
    var stravaLoading by mutableStateOf(false)
        private set
    var stravaAuthorized by mutableStateOf(false)
        private set
    var stravaError by mutableStateOf<String?>(null)
        private set
    var importingActivityId by mutableStateOf<Long?>(null)
        private set
    var gpxError by mutableStateOf<String?>(null)
        private set

    val stravaConfigured: Boolean get() = stravaAuthManager.isConfigured

    init {
        viewModelScope.launch {
            stravaAuthorized = stravaAuthManager.isAuthorized()
            if (stravaAuthorized) refreshStravaActivities()
        }
    }

    fun connectStrava() = stravaAuthManager.launchAuthorization()

    /** Call after MainActivity handles a `windrm://strava-callback` redirect intent. */
    fun onStravaAuthorized() {
        viewModelScope.launch {
            stravaAuthorized = stravaAuthManager.isAuthorized()
            if (stravaAuthorized) refreshStravaActivities()
        }
    }

    fun refreshStravaActivities() {
        viewModelScope.launch {
            stravaLoading = true
            stravaError = null
            runCatching { stravaRepository.listRecentActivities() }
                .onSuccess { stravaActivities = it }
                .onFailure { stravaError = it.message ?: "Errore nel caricamento delle attività Strava" }
            stravaLoading = false
        }
    }

    fun importStravaActivity(activity: StravaActivitySummary, onImported: (Route) -> Unit) {
        viewModelScope.launch {
            importingActivityId = activity.id
            stravaError = null
            runCatching {
                routeRepository.findByStravaActivityId(activity.id) ?: run {
                    val route = stravaRepository.importActivityAsRoute(activity)
                    val id = routeRepository.saveRoute(route)
                    route.copy(id = id)
                }
            }
                .onSuccess { onImported(it) }
                .onFailure { stravaError = it.message ?: "Errore nell'importazione dell'attività" }
            importingActivityId = null
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
