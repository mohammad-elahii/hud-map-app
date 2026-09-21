package com.example.hudmapapp.ui.screens.hudScreen

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.navigation.fakeNavigationState
import com.example.hudmapapp.ui.navigation.AppRoute
import com.example.hudmapapp.ui.theme.MainGradient
import com.example.hudmapapp.ui.theme.components.HudButton

private const val TAG = "HUDScreen"

/**
 * Distraction-free HUD navigation display.
 *
 * Fed app-owned [NavigationState] from the shared session coordinator (see
 * AppNavigation): this screen never touches Navigation SDK types. Live
 * guidance rendering lands in 5.3; session-state variants in 5.4.
 *
 * The actual navigation visuals live in [HUDNavigationLayer] so that
 * [MirroredHUDScreen] (issue #18) can reuse the exact same content,
 * flipped, instead of duplicating it.
 */
@Composable
fun HUDScreen(
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

        // 5.3 consumes navigationState/onStop/onResume/onRetry here to render
        // live guidance + session variants. 5.2 only wires the state through.
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
            HUDNavigationLayer(modifier = Modifier.fillMaxSize())
        }

        HUDIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Exit HUD, return to Home",
            onClick = {
                Log.d(TAG, "Close button clicked — popping back stack")
                val popped = navController.popBackStack()
                Log.d(TAG, "popBackStack() returned $popped")
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
        )

        // Entry point into the mirrored presentation (issue #18). Kept as a
        // plain, unmirrored text pill so it never has to be second-guessed
        // as part of the navigation instruction itself.
        MirrorEntryButton(
            onClick = {
                Log.d(TAG, "Mirror button clicked — attempting navigate(AppRoute.MirroredHUD)")
                try {
                    navController.navigate(AppRoute.MirroredHUD)
                    Log.d(TAG, "navigate(AppRoute.MirroredHUD) call completed without throwing")
                } catch (e: Exception) {
                    Log.e(TAG, "navigate(AppRoute.MirroredHUD) failed", e)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
        )
    }
}

@Composable
internal fun HUDNavigationLayer(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PulsingManeuverArrow(
                rotationDegrees = -90f
            )

            Text(
                text = "300 m",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary
            )

            Text(
                text = "Turn left",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = "Then continue on Main St · 1.2 km",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        SubtleCarGlyph(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        )
    }
}

@Composable
private fun PulsingManeuverArrow(
    rotationDegrees: Float
) {
    val infiniteTransition = rememberInfiniteTransition(
        label = "hud_pulse"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(glowScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = glowAlpha
                            ),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        GradientIcon(
            modifier = Modifier
                .size(120.dp)
                .rotate(rotationDegrees)
        )
    }
}

@Composable
private fun GradientIcon(
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(
        colors = MainGradient
    )

    Icon(
        imageVector = Icons.Filled.ArrowUpward,
        contentDescription = "Turn left in 300 meters",
        tint = Color.White,
        modifier = modifier
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.Offscreen
            )
            .drawWithCache {
                onDrawWithContent {
                    // Draw the arrow into the offscreen layer.
                    drawContent()

                    // Apply the gradient only to the existing arrow pixels.
                    drawRect(
                        brush = gradient,
                        blendMode = BlendMode.SrcIn
                    )
                }
            }
    )
}

@Composable
private fun SubtleCarGlyph(
    modifier: Modifier = Modifier
) {
    val carColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier.size(
            width = 46.dp,
            height = 24.dp
        )
    ) {
        drawCarGlyph(carColor)
    }
}

private fun DrawScope.drawCarGlyph(
    color: Color
) {
    val bodyHeight = size.height * 0.55f
    val bodyTop = size.height - bodyHeight

    // Body
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, bodyTop),
        size = Size(size.width, bodyHeight),
        cornerRadius = CornerRadius(
            bodyHeight / 2f,
            bodyHeight / 2f
        )
    )

    // Cabin
    drawRoundRect(
        color = color,
        topLeft = Offset(
            size.width * 0.22f,
            0f
        ),
        size = Size(
            size.width * 0.56f,
            bodyTop + bodyHeight * 0.35f
        ),
        cornerRadius = CornerRadius(
            6.dp.toPx(),
            6.dp.toPx()
        )
    )

    // Wheels
    val wheelRadius = bodyHeight * 0.32f

    drawCircle(
        color = color,
        radius = wheelRadius,
        center = Offset(
            size.width * 0.24f,
            size.height - wheelRadius * 0.6f
        )
    )

    drawCircle(
        color = color,
        radius = wheelRadius,
        center = Offset(
            size.width * 0.76f,
            size.height - wheelRadius * 0.6f
        )
    )
}

/** Shared circular icon button style used for both HUD screens' exit controls. */
@Composable
internal fun HUDIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun MirrorEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Mirror",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenPreview() {
    HUDScreen(
        navController = rememberNavController()
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenActivePreview() {
    HUDScreen(
        navController = rememberNavController(),
        navigationState = fakeNavigationState()
    )
}

/**
 * Defined no-session fallback: shown when the HUD is opened with no active
 * navigation (Idle / Stopped) instead of a blank screen or stale guidance.
 */
@Composable
internal fun HudIdleFallback(
    onBackToMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No active navigation",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Start navigation from the map to see guidance here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 8.dp)
        )
        HudButton(
            text = "Back to map",
            onClick = onBackToMap,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}