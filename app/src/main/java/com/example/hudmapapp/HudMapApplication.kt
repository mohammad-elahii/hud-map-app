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

        navigationManager.initialize(this)
    }
}
