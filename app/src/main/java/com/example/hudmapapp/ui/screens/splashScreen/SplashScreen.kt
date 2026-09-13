package com.example.hudmapapp.ui.screens.splashScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import com.example.hudmapapp.ui.navigation.AppRoute

import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavController
) {
    LaunchedEffect(Unit) {
        delay(2000L)

        navController.navigate(AppRoute.Introduction1) {
            popUpTo(AppRoute.Introduction1) {
                inclusive = true
            }
        }
    }

   SplashScreenLayout()
}