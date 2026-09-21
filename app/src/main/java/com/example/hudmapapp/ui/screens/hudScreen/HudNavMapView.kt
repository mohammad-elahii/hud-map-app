package com.example.hudmapapp.ui.screens.hudScreen

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.libraries.navigation.NavigationView

private const val TAG = "HudNavMapView"

/**
 * The turn-by-turn map for the HUD screen: the Navigation SDK's own
 * NavigationView with its navigation UI left at the defaults, so while a
 * session is running it draws the route, follows the vehicle camera, and
 * shows the SDK's guidance chrome. Composed only while a session is active
 * (see HUDScreen); the text HUD overlays it. The guidance text in
 * HudSessionContent mirrors independently — a mirrored live map would be
 * unreadable, so this map is always presented unmirrored.
 */
@Composable
internal fun HudNavMapView(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var navigationView by remember { mutableStateOf<NavigationView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            NavigationView(viewContext).apply {
                onCreate(null as Bundle?)
                navigationView = this
                // Do NOT forward onStart/onResume here: registering the
                // lifecycle observer below makes LifecycleRegistry dispatch
                // ON_START/ON_RESUME to sync it with the current activity
                // state, and NavView enforces strict call ordering.
                getMapAsync(
                    OnMapReadyCallback { map ->
                        Log.d(TAG, "Navigation map ready")
                        // The navigation UI renders the vehicle position
                        // itself; enabling the my-location layer on top of it
                        // would draw a duplicate dot.
                        map.isMyLocationEnabled = false
                    }
                )
            }
        },
        onRelease = { view ->
            Log.d(TAG, "Navigation map view destroyed")
            navigationView = null
            view.onDestroy()
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> navigationView?.onStart()
                Lifecycle.Event.ON_RESUME -> navigationView?.onResume()
                Lifecycle.Event.ON_PAUSE -> navigationView?.onPause()
                Lifecycle.Event.ON_STOP -> navigationView?.onStop()
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
