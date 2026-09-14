package com.example.hudmapapp.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Typed navigation routes. Only ONE addition was made for this issue:
 * AppRoute.MirroredHUD — pushed on top of AppRoute.HUD, outside the
 * Main/Scaffold graph, same as HUD itself. Merge just that entry in if
 * your real file already has everything else.
 */
sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Introduction1 : AppRoute
    @Serializable data object Introduction2 : AppRoute
    @Serializable data object Introduction3 : AppRoute
      @Serializable data object Home : AppRoute
    @Serializable data object HUD : AppRoute
    @Serializable data object MirroredHUD : AppRoute
    @Serializable data object Settings : AppRoute
}