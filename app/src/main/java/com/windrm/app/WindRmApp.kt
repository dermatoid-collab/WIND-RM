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
        }
    }
}
