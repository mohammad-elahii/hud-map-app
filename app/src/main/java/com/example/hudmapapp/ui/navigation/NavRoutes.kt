package com.example.hudmapapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class AppRoute {

    @Serializable
    object Splash : AppRoute()

    @Serializable
    object Introduction1 : AppRoute()

    @Serializable
    object Introduction2 : AppRoute()

    @Serializable
    object Introduction3 : AppRoute()

    @Serializable
    object Home : AppRoute()

    @Serializable
    object HUD : AppRoute()

    @Serializable
    object Settings : AppRoute()
}