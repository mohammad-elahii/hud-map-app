package com.example.hudmapapp.ui.screens.homeScreen

import androidx.lifecycle.ViewModel
import com.example.hudmapapp.data.model.Destination
import com.example.hudmapapp.data.model.SelectedDestinationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {

    private val _selectedDestination = MutableStateFlow<SelectedDestinationState>(
        SelectedDestinationState.None
    )
    val selectedDestination: StateFlow<SelectedDestinationState> =
        _selectedDestination.asStateFlow()

    fun selectDestination(destination: Destination) {
        _selectedDestination.value = SelectedDestinationState.Selected(destination)
    }

    fun confirmDestination() {
        val current = _selectedDestination.value
        if (current is SelectedDestinationState.Selected) {
            _selectedDestination.value = SelectedDestinationState.Confirmed(current.destination)
        }
    }

    fun changeDestination(destination: Destination) {
        _selectedDestination.value = SelectedDestinationState.Selected(destination)
    }

    fun clearDestination() {
        _selectedDestination.value = SelectedDestinationState.None
    }
}
