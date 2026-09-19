package com.example.hudmapapp.ui.screens.homeScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.recoveryActionFor
import com.example.hudmapapp.navigation.userMessageFor
import com.example.hudmapapp.ui.theme.components.HudButton
import com.example.hudmapapp.navigation.SessionRecoveryAction

@Composable
fun NavigationSessionBanner(
    sessionState: NavigationSessionState,
    onStop: () -> Unit,
    onRetry: () -> Unit = {},
    onResume: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .padding(16.dp)
            .background(colors.primaryContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (sessionState) {
            is NavigationSessionState.Starting -> {
                CircularProgressIndicator(
                    color = colors.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Starting navigation…",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            is NavigationSessionState.Active,
            is NavigationSessionState.Rerouting,
            is NavigationSessionState.OffRoute,
            is NavigationSessionState.Interrupted -> {
                val title = when (sessionState) {
                    is NavigationSessionState.Active ->
                        "Navigating to ${sessionState.destination.name}"
                    is NavigationSessionState.Rerouting ->
                        "Finding a better route"
                    is NavigationSessionState.OffRoute ->
                        "Off route — recalculating"
                    is NavigationSessionState.Interrupted ->
                        userMessageFor(sessionState) ?: "Navigation paused"
                    else -> "Navigation"
                }
                val subtitle = when (sessionState) {
                    is NavigationSessionState.Active -> routeSummary(sessionState.route)
                    is NavigationSessionState.Rerouting -> routeSummary(sessionState.route)
                    is NavigationSessionState.OffRoute -> routeSummary(sessionState.route)
                    is NavigationSessionState.Interrupted -> routeSummary(sessionState.route)
                    else -> ""
                }
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onPrimaryContainer,
                        maxLines = 1
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onPrimaryContainer
                        )
                    }
                    if (sessionState is NavigationSessionState.Interrupted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        HudButton(
                            text = "Resume",
                            onClick = onResume,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            is NavigationSessionState.Error -> {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userMessageFor(sessionState) ?: "Navigation error",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onPrimaryContainer
                    )
                    if (recoveryActionFor(sessionState) == SessionRecoveryAction.RETRY_START) {
                        HudButton(
                            text = "Retry",
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = "Navigation",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        IconButton(onClick = onStop) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Stop navigation",
                tint = colors.onPrimaryContainer
            )
        }
    }
}
