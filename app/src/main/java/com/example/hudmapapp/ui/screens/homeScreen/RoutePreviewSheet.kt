package com.example.hudmapapp.ui.screens.homeScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.RoutePreview
import com.example.hudmapapp.data.model.RoutePreviewError
import com.example.hudmapapp.data.model.RoutePreviewState
import com.example.hudmapapp.ui.theme.components.HudButton
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePreviewSheet(
    destination: Destination,
    routePreviewState: RoutePreviewState,
    selectedRoute: RoutePreview?,
    onRouteSelected: (String) -> Unit,
    onStartNavigation: (RoutePreview) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun hideAnd(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    LaunchedEffect(Unit) {
        sheetState.show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose a route",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { hideAnd(onDismiss) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss route previews",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = destination.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (routePreviewState) {
                RoutePreviewState.Idle,
                RoutePreviewState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Finding routes…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RoutePreviewState.Empty -> {
                    RoutePreviewMessage(
                        title = "No routes found",
                        message = "Try a different destination or check your connection."
                    )
                }
                RoutePreviewState.Cancelled -> {
                    RoutePreviewMessage(
                        title = "Route search cancelled",
                        message = "Confirm the destination again to retry."
                    )
                }
                is RoutePreviewState.Error -> {
                    RoutePreviewMessage(
                        title = "Routes unavailable",
                        message = routeErrorMessage(routePreviewState.error)
                    )
                }
                is RoutePreviewState.Available -> {
                    routePreviewState.routes.forEachIndexed { index, route ->
                        RouteOptionRow(
                            route = route,
                            index = index,
                            isSelected = route.id == selectedRoute?.id,
                            onClick = { onRouteSelected(route.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HudButton(
                        text = "Start navigation",
                        onClick = {
                            val route = selectedRoute
                                ?: routePreviewState.routes.firstOrNull()
                            if (route != null) {
                                hideAnd { onStartNavigation(route) }
                            }
                        },
                        leadingIcon = Icons.Filled.Navigation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteOptionRow(
    route: RoutePreview,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (isSelected) colors.primary else colors.outlineVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) colors.primaryContainer else colors.surfaceContainerLow
            )
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routeLabel(index, route),
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) colors.onPrimaryContainer else colors.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = routeSummary(route),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoutePreviewMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun routeLabel(index: Int, route: RoutePreview): String {
    val base = when (index) {
        0 -> "Fastest route"
        1 -> "Alternative 1"
        else -> "Alternative $index"
    }
    return route.label?.takeIf { it.isNotBlank() }?.let { "$base · $it" } ?: base
}

internal fun routeSummary(route: RoutePreview): String {
    return "${formatDistance(route.distanceMeters)} · ${formatDuration(route.durationSeconds)}"
}

internal fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "$meters m"
    }
}

internal fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes} min"
        else -> "${totalSeconds}s"
    }
}

internal fun routeErrorMessage(error: RoutePreviewError): String {
    return when (error) {
        RoutePreviewError.Network -> "No internet connection. Check your connection and retry."
        RoutePreviewError.Timeout -> "The request timed out. Please retry."
        RoutePreviewError.Authentication ->
            "Route service authentication failed. Check the API configuration."
        RoutePreviewError.QuotaExceeded ->
            "Route quota exceeded. Try again later."
        RoutePreviewError.InvalidRequest ->
            "Could not plan a route for this destination."
        RoutePreviewError.NoResults -> "No routes found for this destination."
        RoutePreviewError.Cancelled -> "Route search was cancelled."
        RoutePreviewError.Unknown -> "Something went wrong. Please retry."
    }
}
