package com.example.hudmapapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.ui.screens.HUDScreen
import com.example.hudmapapp.ui.screens.HomeScreen
import com.example.hudmapapp.ui.screens.IntroductionScreen
import com.example.hudmapapp.ui.screens.SettingsScreen
import com.example.hudmapapp.ui.screens.SplashScreen

@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Splash
    ) {

        composable<AppRoute.Splash> {
            SplashScreen(
                navController = navController
            )
        }

        composable<AppRoute.Introduction> {
            IntroductionScreen(
                navController = navController
            )
        }

        composable<AppRoute.Home> {
            HomeScreen(
                navController = navController
            )
        }

        composable<AppRoute.HUD> {
            HUDScreen(
                navController = navController
            )
        }

        composable<AppRoute.Settings> {
            SettingsScreen(
                navController = navController
            )
        }
    }
}