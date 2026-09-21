package com.example.hudmapapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.HudMapApplication
import com.example.hudmapapp.navigation.NavigationSessionCoordinator
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
    val context = LocalContext.current
    val application = context.applicationContext as HudMapApplication

    // #59: one coordinator shared by Home, HUD, and MirroredHUD. Activity-scoped
    // (not back-stack-entry-scoped) so navigating Home <-> HUD, or rotating the
    // device, neither kills nor duplicates the session. SDK init stays in
    // NavigationManager; this only shares the already-tested session holder.
    // Background/foreground grace handling stays in HomeScreen's observer: the
    // NavHost caps off-screen entries at STARTED, so Home -> HUD is a pause, not
    // a stop, and never trips the 30 s background timer by itself.
    val sessionCoordinator: NavigationSessionCoordinator = viewModel(
        viewModelStoreOwner = context as ComponentActivity,
        factory = NavigationSessionCoordinator.Factory(application.navigationManager)
    )
    val sessionState by sessionCoordinator.sessionState.collectAsState()
    val navigationState by sessionCoordinator.navigationState.collectAsState()

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
                navController = navController,
                sessionCoordinator = sessionCoordinator,
                onStartNavigation = { navController.navigate(AppRoute.HUD) }
            )
        }

        composable<AppRoute.HUD> {
            HUDScreen(
                navController = navController,
                sessionState = sessionState,
                navigationState = navigationState,
                onStop = { sessionCoordinator.stopNavigation() },
                onResume = { sessionCoordinator.resume() },
                onRetry = { sessionCoordinator.retryStart() }
            )
        }

        composable<AppRoute.MirroredHUD>{
            MirroredHUDScreen(
                navController = navController,
                sessionState = sessionState,
                navigationState = navigationState,
                onStop = { sessionCoordinator.stopNavigation() },
                onResume = { sessionCoordinator.resume() },
                onRetry = { sessionCoordinator.retryStart() }
            )
        }

        composable<AppRoute.Settings> {
            SettingsScreen(
                navController = navController
            )
        }
    }
}