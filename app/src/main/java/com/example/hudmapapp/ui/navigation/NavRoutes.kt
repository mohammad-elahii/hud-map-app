package com.example.hudmapapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class AppRoute {

    @Serializable
    object Splash : AppRoute()

    @Serializable
    object Introduction : AppRoute()

    @Serializable
    object Home : AppRoute()

    @Serializable
    object HUD : AppRoute()

    @Serializable
    object Settings : AppRoute()
}