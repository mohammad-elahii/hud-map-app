package com.example.hudmapapp.data.model

sealed interface DestinationSearchState {
    data object Idle : DestinationSearchState
    data object Searching : DestinationSearchState
    data class Results(val destinations: List<Destination>) : DestinationSearchState
    data object Empty : DestinationSearchState
    data class Error(val message: String) : DestinationSearchState
    data class Selected(val destination: Destination) : DestinationSearchState
}
