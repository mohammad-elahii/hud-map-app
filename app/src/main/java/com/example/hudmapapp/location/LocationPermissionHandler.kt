package com.example.hudmapapp.location

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class LocationPermissionActions(
    private val requestPermission: () -> Unit,
    private val openAppSettings: () -> Unit
) {
    fun request() = requestPermission()
    fun openSettings() = openAppSettings()
}

@Composable
fun LocationPermissionHandler(
    onPermissionGranted: () -> Unit,
    content: @Composable (hasPermission: Boolean, actions: LocationPermissionActions) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }
    var showDeniedRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            hasPermission = true
            onPermissionGranted()
        } else {
            val shouldShowRationale = (context as? Activity)?.let { activity ->
                activity.shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
                activity.shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            } ?: false

            if (!shouldShowRationale) {
                showPermanentlyDeniedDialog = true
            } else {
                showDeniedRationale = true
            }
        }
    }

    fun requestPermission() {
        permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    val actions = remember {
        LocationPermissionActions(
            requestPermission = { requestPermission() },
            openAppSettings = { openAppSettings() }
        )
    }

    if (showDeniedRationale) {
        PermissionDeniedDialog(
            title = "Location Permission Required",
            message = "This app needs location permission to show your position on the map. Please grant the permission to continue.",
            confirmText = "Try Again",
            dismissText = "Cancel",
            onConfirm = {
                showDeniedRationale = false
                requestPermission()
            },
            onDismiss = {
                showDeniedRationale = false
            }
        )
    }

    if (showPermanentlyDeniedDialog) {
        PermissionDeniedDialog(
            title = "Location Permission Denied",
            message = "Location permission has been permanently denied. Please enable it in app settings to use location features.",
            confirmText = "Open Settings",
            dismissText = "Cancel",
            onConfirm = {
                showPermanentlyDeniedDialog = false
                openAppSettings()
            },
            onDismiss = {
                showPermanentlyDeniedDialog = false
            }
        )
    }

    content(hasPermission, actions)
}

@Composable
private fun PermissionDeniedDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        }
    )
}
