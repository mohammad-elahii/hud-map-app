package com.example.hudmapapp.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Introduction1 : AppRoute
    @Serializable data object Introduction2 : AppRoute
    @Serializable data object Introduction3 : AppRoute
      @Serializable data object Home : AppRoute
    @Serializable data object HUD : AppRoute
    @Serializable data object Settings : AppRoute
}
