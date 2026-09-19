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

@Composable
fun NavigationSessionBanner(
    sessionState: NavigationSessionState,
    onStop: () -> Unit,
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
            is NavigationSessionState.Active -> {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Navigating to ${sessionState.destination.name}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onPrimaryContainer,
                        maxLines = 1
                    )
                    Text(
                        text = routeSummary(sessionState.route),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer
                    )
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
