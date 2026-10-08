package com.windrm.app

import android.app.Application
import com.windrm.app.di.AppContainer
import org.osmdroid.config.Configuration

class WindRmApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // osmdroid needs a writable cache dir and a distinct user-agent to respect OSM's tile usage policy.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = getExternalFilesDir("osmdroid") ?: filesDir
            osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles")
            // Raster tiles come one by one: osmdroid's defaults (2 download and 2 disk threads, 9 tiles kept in
            // memory) leave a fast-panned map showing blank squares for a while. More threads and a bigger
            // in-memory cache make panning and zooming back over seen ground much quicker.
            tileDownloadThreads = 8
            tileFileSystemThreads = 8
            tileDownloadMaxQueueSize = 64
            tileFileSystemMaxQueueSize = 64
            cacheMapTileCount = 64
            cacheMapTileOvershoot = 16
        }
    }
}
