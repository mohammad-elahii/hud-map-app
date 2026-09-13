package com.example.hudmapapp.ui.screens

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

        navController.navigate(AppRoute.Home) {
            popUpTo(AppRoute.Home) {
                inclusive = true
            }
        }
    }

   SplashScreenLayout()
}