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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.navigation.fakeNavigationState
import com.example.hudmapapp.ui.navigation.AppRoute

/**
 * Mirrored windshield projection of the HUD.
 *
 * Mirroring decision (5.4): the whole guidance layer is flipped geometrically
 * ([graphicsLayer] `scaleX = -1f`) and NOT pre-compensated. A windshield
 * reflection mirrors the image a second time, so the geometrically mirrored
 * source is what reads correctly in the glass — including arrow direction.
 * On the phone screen itself a left turn therefore renders right-mirrored;
 * that is the projection transform, not a bug. Text mirrors with the layer
 * for the same reason; close controls stay outside the flipped layer and
 * remain unmirrored and tappable.
 */
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

        // Same content switch as HUDScreen; only live guidance flips (handled
        // inside HudSessionContent via mirrorGuidance). Idle fallback and
        // close controls stay unmirrored and tappable.
        HudSessionContent(
            sessionState = sessionState,
            navigationState = navigationState,
            onBackToMap = {
                navController.popBackStack(
                    AppRoute.Home,
                    inclusive = false
                )
            },
            onStop = onStop,
            onResume = onResume,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
            mirrorGuidance = true
        )

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
        sessionState = activeSessionForPreview(),
        navigationState = fakeNavigationState()
    )
}
