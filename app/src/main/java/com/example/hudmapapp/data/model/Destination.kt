package com.example.hudmapapp.data.model

data class Destination(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Float? = null,
    val userRatingsTotal: Int? = null
)
