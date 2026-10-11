package com.example.hudmapapp.data.model

import android.graphics.Bitmap

/**
 * Place details plus fetched photos for the map POI bottom sheet. Photos are
 * already-downloaded bitmaps: fetching them here keeps every Places API
 * detail (metadata lifetime, photo requests) inside the repository.
 */
data class PoiDetails(
    val destination: Destination,
    val photos: List<Bitmap>
)
