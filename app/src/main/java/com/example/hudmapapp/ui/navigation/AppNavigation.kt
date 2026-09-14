package com.example.hudmapapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.ui.screens.hudScreen.HUDScreen
import com.example.hudmapapp.ui.screens.homeScreen.HomeScreen
import com.example.hudmapapp.ui.screens.hudScreen.MirroredHUDScreen
import com.example.hudmapapp.ui.screens.introductionScreens.IntroductionScreen1
import com.example.hudmapapp.ui.screens.introductionScreens.IntroductionScreen2
import com.example.hudmapapp.ui.screens.introductionScreens.IntroductionScreen3
import com.example.hudmapapp.ui.screens.settingScreen.SettingsScreen
import com.example.hudmapapp.ui.screens.splashScreen.SplashScreen

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

        composable<AppRoute.Introduction1> {
            IntroductionScreen1(
                navController = navController
            )
        }

        composable<AppRoute.Introduction2> {
            IntroductionScreen2(
                navController = navController
            )
        }

        composable<AppRoute.Introduction3> {
            IntroductionScreen3(
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

        composable<AppRoute.MirroredHUD>{
            MirroredHUDScreen(
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