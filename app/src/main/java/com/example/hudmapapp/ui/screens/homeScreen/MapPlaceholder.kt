package com.example.hudmapapp.ui.screens.homeScreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Composable
internal fun MapPlaceholder(
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