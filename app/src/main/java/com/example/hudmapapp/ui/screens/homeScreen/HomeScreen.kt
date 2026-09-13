package com.example.hudmapapp.ui.screens.homeScreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.hudmapapp.ui.navigation.AppRoute
import com.example.hudmapapp.ui.theme.DeepPurple30

@Composable
fun HomeScreen(
    navController: NavController
) {
    Scaffold { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            MapPlaceholder(
                modifier = Modifier.fillMaxSize()
            )

            HomeTopBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                onSettingsClick = {
                    navController.navigate(AppRoute.Settings)
                }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        end = 20.dp,
                        bottom = 160.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MapControlButton(
                    icon = Icons.Filled.Layers,
                    contentDescription = "Map layers"
                )

                MapControlButton(
                    icon = Icons.Filled.MyLocation,
                    contentDescription = "Recenter on my location"
                )

                MapControlButton(
                    icon = Icons.Filled.DarkMode,
                    contentDescription = "Dark mode toggle"
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Row(
        modifier = modifier
            .padding(
                horizontal = 20.dp,
                vertical = 16.dp
            )
            .background(
                color = DeepPurple30,
                shape = RoundedCornerShape(50)
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Good morning",
                style = typography.labelMedium,
                color = colors.onSurfaceVariant
            )

            Text(
                text = "Where to?",
                style = typography.labelLarge,
                color = colors.onSurface
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(colors.primary)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = colors.onSurface
            )
        }
    }
}

@Composable
private fun MapPlaceholder(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier.background(
            colors.surfaceContainerLowest
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val step = 48.dp.toPx()

            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                x += step
            }

            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = colors.outlineVariant,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += step
            }
        }

        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Current location placeholder",
            tint = colors.primary,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String
) {
    val colors = MaterialTheme.colorScheme

    IconButton(
        onClick = {},
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.tertiary)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.surface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        navController = rememberNavController()
    )
}
