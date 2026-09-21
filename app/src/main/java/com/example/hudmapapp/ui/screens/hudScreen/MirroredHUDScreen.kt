package com.example.hudmapapp.ui.screens.hudScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.navigation.fakeNavigationState
import com.example.hudmapapp.ui.navigation.AppRoute

@Composable
fun MirroredHUDScreen(
    navController: NavController,
    sessionState: NavigationSessionState = NavigationSessionState.Idle,
    navigationState: NavigationState = NavigationState(),
    onStop: () -> Unit = {},
    onResume: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // Mirrors HUDScreen: same session state, same Idle/Stopped fallback,
        // layer flipped for windshield projection. 5.3 wires navigationState
        // through; 5.4 verifies mirrored arrow/text parity.
        if (sessionState is NavigationSessionState.Idle ||
            sessionState is NavigationSessionState.Stopped
        ) {
            HudIdleFallback(
                onBackToMap = {
                    navController.popBackStack(
                        AppRoute.Home,
                        inclusive = false
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            HUDNavigationLayer(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = -1f)
            )
        }

        HUDIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Exit mirrored HUD, return to normal HUD",
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun MirroredHUDScreenPreview() {
    MirroredHUDScreen(
        navController = rememberNavController()
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun MirroredHUDScreenActivePreview() {
    MirroredHUDScreen(
        navController = rememberNavController(),
        navigationState = fakeNavigationState()
    )
}
