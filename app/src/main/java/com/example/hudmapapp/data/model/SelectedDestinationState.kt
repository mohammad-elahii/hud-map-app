package com.example.hudmapapp.data.model

sealed interface SelectedDestinationState {
    data object None : SelectedDestinationState
    data class Selected(val destination: Destination) : SelectedDestinationState
    data class Confirmed(val destination: Destination) : SelectedDestinationState
}
