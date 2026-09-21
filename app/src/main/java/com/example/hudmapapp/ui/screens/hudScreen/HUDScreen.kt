package com.example.hudmapapp.ui.screens.hudScreen

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.navigation.GuidanceStatus
import com.example.hudmapapp.navigation.ManeuverInfo
import com.example.hudmapapp.navigation.ManeuverType
import com.example.hudmapapp.navigation.NavigationProgress
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.ui.navigation.AppRoute
import com.example.hudmapapp.ui.theme.MainGradient
import com.example.hudmapapp.ui.theme.components.HudButton

private const val TAG = "HUDScreen"

/**
 * Distraction-free HUD navigation display.
 *
 * Fed app-owned [NavigationState] from the shared session coordinator (see
 * AppNavigation): this screen never touches Navigation SDK types. Every
 * session state renders a defined variant driven by [hudContentFor], using
 * the same [userMessageFor]/[recoveryActionFor] strings as the Home banner.
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

        // The turn-by-turn Navigation SDK map is only mounted while a session
        // is live; Idle/Error/etc. fall back to the plain black HUD.
        val showNavMap = when (sessionState) {
            is NavigationSessionState.Active,
            is NavigationSessionState.Starting,
            is NavigationSessionState.Rerouting,
            is NavigationSessionState.OffRoute,
            is NavigationSessionState.Interrupted -> true
            else -> false
        }
        if (showNavMap) {
            HudNavMapView(modifier = Modifier.fillMaxSize())
            // Scrim keeps the guidance text readable over the live map.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

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
            modifier = Modifier.fillMaxSize()
        )

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
    state: NavigationState = NavigationState(),
    statusLine: String? = null,
    dimmed: Boolean = false,
    hideCarGlyph: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedRotation by animateFloatAsState(
        targetValue = rotationForManeuver(
            state.currentManeuver?.type
                ?: ManeuverType.UNKNOWN
        ),
        animationSpec = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing
        ),
        label = "maneuver_rotation"
    )
    val instruction = headlineFor(state)
    val nextLine = nextLineFor(state)
    val footer = footerFor(state)
    val distances = state.progress
    val contentAlpha = if (dimmed) 0.6f else 1f

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer(alpha = contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (statusLine != null) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            PulsingManeuverArrow(
                rotationDegrees = animatedRotation,
                contentDescription = "$instruction in ${formatHudDistance(distances.distanceToManeuverMeters)}"
            )

            Text(
                text = formatHudDistance(distances.distanceToManeuverMeters),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary
            )

            Text(
                text = instruction,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (nextLine != null) {
                Text(
                    text = nextLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (footer != null) {
                Text(
                    text = footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        if (!hideCarGlyph) {
            SubtleCarGlyph(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            )
        }
    }
}

@Composable
private fun PulsingManeuverArrow(
    rotationDegrees: Float,
    contentDescription: String
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
            contentDescription = contentDescription,
            modifier = Modifier
                .size(120.dp)
                .rotate(rotationDegrees)
        )
    }
}

@Composable
private fun GradientIcon(
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(
        colors = MainGradient
    )

    Icon(
        imageVector = Icons.Filled.ArrowUpward,
        contentDescription = contentDescription,
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
private fun HUDScreenLeftTurnPreview() {
    HUDScreen(
        navController = rememberNavController(),
        sessionState = activeSessionForPreview(),
        navigationState = previewState(
            type = ManeuverType.LEFT,
            instruction = "Turn left onto Main St",
            roadName = "Main St",
            nextInstruction = "Continue on Main St",
            distanceToManeuverMeters = 300,
            remainingDistanceMeters = 1200,
            remainingDurationSeconds = 420L
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenStraightPreview() {
    HUDScreen(
        navController = rememberNavController(),
        sessionState = activeSessionForPreview(),
        navigationState = previewState(
            type = ManeuverType.STRAIGHT,
            instruction = "Continue on Highway 1",
            roadName = "Highway 1",
            nextInstruction = "Take the exit toward Downtown",
            distanceToManeuverMeters = 1200,
            remainingDistanceMeters = 8400,
            remainingDurationSeconds = 720L
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenRoundaboutPreview() {
    HUDScreen(
        navController = rememberNavController(),
        sessionState = activeSessionForPreview(),
        navigationState = previewState(
            type = ManeuverType.ROUNDABOUT,
            instruction = "At the roundabout, take the 2nd exit",
            roadName = "Ring Road",
            nextInstruction = "Continue on Ring Road",
            distanceToManeuverMeters = 150,
            remainingDistanceMeters = 2600,
            remainingDurationSeconds = 300L,
            roundaboutExit = 2
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenDestinationPreview() {
    HUDScreen(
        navController = rememberNavController(),
        sessionState = activeSessionForPreview(),
        navigationState = previewState(
            type = ManeuverType.DESTINATION,
            instruction = "You have arrived",
            roadName = "Main St",
            nextInstruction = null,
            distanceToManeuverMeters = 0,
            remainingDistanceMeters = 0,
            remainingDurationSeconds = 0L
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenMissingValuesPreview() {
    HUDScreen(
        navController = rememberNavController(),
        sessionState = activeSessionForPreview(),
        navigationState = previewState(
            type = ManeuverType.UNKNOWN,
            instruction = "",
            roadName = "",
            nextInstruction = null,
            distanceToManeuverMeters = null,
            remainingDistanceMeters = null,
            remainingDurationSeconds = null
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenReroutingPreview() {
    val session = activeSessionForPreview() as NavigationSessionState.Active
    HUDScreen(
        navController = rememberNavController(),
        sessionState = NavigationSessionState.Rerouting(
            destination = session.destination,
            route = session.route
        ),
        navigationState = previewState(
            type = ManeuverType.LEFT,
            instruction = "Turn left onto Main St",
            roadName = "Main St",
            nextInstruction = "Continue on Main St",
            distanceToManeuverMeters = 300,
            remainingDistanceMeters = 1200,
            remainingDurationSeconds = 420L
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenInterruptedPreview() {
    val session = activeSessionForPreview() as NavigationSessionState.Active
    HUDScreen(
        navController = rememberNavController(),
        sessionState = NavigationSessionState.Interrupted(
            destination = session.destination,
            route = session.route,
            reason = com.example.hudmapapp.navigation.InterruptionReason.LOCATION_UNAVAILABLE
        ),
        navigationState = previewState(
            type = ManeuverType.LEFT,
            instruction = "Turn left onto Main St",
            roadName = "Main St",
            nextInstruction = "Continue on Main St",
            distanceToManeuverMeters = 300,
            remainingDistanceMeters = 1200,
            remainingDurationSeconds = 420L
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenArrivedPreview() {
    HUDScreen(
        navController = rememberNavController(),
        sessionState = NavigationSessionState.Arrived,
        navigationState = previewState(
            type = ManeuverType.DESTINATION,
            instruction = "You have arrived",
            roadName = "Main St",
            nextInstruction = null,
            distanceToManeuverMeters = 0,
            remainingDistanceMeters = 0,
            remainingDurationSeconds = 0L
        )
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun HUDScreenErrorPreview() {
    HUDScreen(
        navController = rememberNavController(),
        sessionState = NavigationSessionState.Error(
            com.example.hudmapapp.navigation.NavigationSessionError.NetworkError
        ),
        navigationState = NavigationState()
    )
}

internal fun activeSessionForPreview(): NavigationSessionState {
    return NavigationSessionState.Active(
        destination = Destination(
            placeId = "preview",
            name = "Preview Place",
            address = "Preview Address",
            latitude = 36.30,
            longitude = 59.60
        ),
        route = RoutePreview(
            id = "preview-route",
            polylinePoints = emptyList(),
            encodedPolyline = "",
            distanceMeters = 1200,
            durationSeconds = 420L
        )
    )
}

internal fun previewState(
    type: ManeuverType,
    instruction: String,
    roadName: String,
    nextInstruction: String?,
    distanceToManeuverMeters: Int?,
    remainingDistanceMeters: Int?,
    remainingDurationSeconds: Long?,
    roundaboutExit: Int? = null
): NavigationState {
    return NavigationState(
        status = GuidanceStatus.ACTIVE,
        currentManeuver = ManeuverInfo(
            type = type,
            instruction = instruction,
            roadName = roadName,
            roundaboutExit = roundaboutExit
        ),
        nextManeuver = nextInstruction?.let {
            ManeuverInfo(
                type = ManeuverType.STRAIGHT,
                instruction = it,
                roadName = roadName
            )
        },
        progress = NavigationProgress(
            distanceToManeuverMeters = distanceToManeuverMeters,
            remainingDistanceMeters = remainingDistanceMeters,
            remainingDurationSeconds = remainingDurationSeconds,
            etaMillis = remainingDurationSeconds?.let {
                if (it < 0) null else System.currentTimeMillis() + it * 1000L
            }
        )
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

/**
 * Shared session-state switch for HUD and MirroredHUD. Rerouting, off-route,
 * and interruption render the last live guidance snapshot so values never
 * tear mid-transition; the coordinator resumes live values on Active.
 */
@Composable
internal fun HudSessionContent(
    sessionState: NavigationSessionState,
    navigationState: NavigationState,
    onBackToMap: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    mirrorGuidance: Boolean = false
) {
    var frozenGuidance by remember {
        mutableStateOf(navigationState)
    }
    LaunchedEffect(sessionState, navigationState) {
        if (sessionState is NavigationSessionState.Active ||
            sessionState is NavigationSessionState.Starting
        ) {
            frozenGuidance = navigationState
        }
    }

    // Windshield parity: only live guidance mirrors. The Idle fallback stays
    // readable without reflection; see MirroredHUDScreen.
    val guidanceModifier = if (mirrorGuidance) {
        modifier.graphicsLayer(scaleX = -1f)
    } else {
        modifier
    }

    when (val content = hudContentFor(sessionState, navigationState)) {
        is HudContent.Idle -> HudIdleFallback(
            onBackToMap = onBackToMap,
            modifier = modifier
        )
        is HudContent.Starting -> HudStartingLayer(modifier = guidanceModifier)
        is HudContent.Guidance -> HUDNavigationLayer(
            state = if (content.frozen) frozenGuidance else navigationState,
            statusLine = content.statusLine,
            modifier = guidanceModifier
        )
        is HudContent.Interrupted -> HudInterruptedLayer(
            message = content.message,
            canResume = content.canResume,
            state = frozenGuidance,
            onResume = onResume,
            modifier = guidanceModifier
        )
        is HudContent.Arrived -> HudArrivedLayer(
            state = navigationState,
            modifier = guidanceModifier
        )
        is HudContent.Error -> HudErrorLayer(
            message = content.message,
            canRetry = content.canRetry,
            onRetry = onRetry,
            onStop = onStop,
            modifier = guidanceModifier
        )
    }
}

@Composable
private fun HudStartingLayer(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = "Starting navigation…",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

@Composable
private fun HudInterruptedLayer(
    message: String,
    canResume: Boolean,
    state: NavigationState,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        HUDNavigationLayer(
            state = state,
            statusLine = message,
            dimmed = true,
            modifier = Modifier.fillMaxSize()
        )
        if (canResume) {
            HudButton(
                text = "Resume",
                onClick = onResume,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }
    }
}

@Composable
private fun HudArrivedLayer(
    state: NavigationState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        HUDNavigationLayer(
            state = state,
            statusLine = "You have arrived.",
            hideCarGlyph = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun HudErrorLayer(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (canRetry) {
            HudButton(
                text = "Retry",
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        HudButton(
            text = "Stop",
            onClick = onStop,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}