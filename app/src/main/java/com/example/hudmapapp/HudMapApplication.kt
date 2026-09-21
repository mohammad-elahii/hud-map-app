package com.example.hudmapapp

import android.app.Application
import com.example.hudmapapp.location.FusedLocationUpdateSource
import com.example.hudmapapp.location.LocationProvider
import com.example.hudmapapp.location.SharedLocationProvider
import com.example.hudmapapp.navigation.NavigationManager
import com.google.android.libraries.navigation.NavigationApi

class HudMapApplication : Application() {

    lateinit var navigationManager: NavigationManager
        private set

    lateinit var locationProvider: LocationProvider
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPS_API_KEY.isNotBlank()) {
            NavigationApi.setApiKey(BuildConfig.MAPS_API_KEY)
        }
        locationProvider = SharedLocationProvider(
            FusedLocationUpdateSource(applicationContext)
        )
        navigationManager = NavigationManager()
        // The Navigation SDK ships its own copy of the Maps SDK classes, and
        // those classes only finish initializing once the navigator is ready.
        // Kick off navigator init at app start so the map can mount as soon
        // as possible instead of racing it from HomeScreen.
        navigationManager.initialize(this)
    }
}
