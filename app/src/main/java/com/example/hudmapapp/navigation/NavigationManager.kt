package com.example.hudmapapp.navigation

import android.app.Application
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NavigationInitState {
    data object Uninitialized : NavigationInitState
    data object Initializing : NavigationInitState
    data class Ready(val navigator: Navigator) : NavigationInitState
    data class Error(val errorCode: Int) : NavigationInitState
}

class NavigationManager {

    private val _initState = MutableStateFlow<NavigationInitState>(NavigationInitState.Uninitialized)
    val initState: StateFlow<NavigationInitState> = _initState.asStateFlow()

    private var navigator: Navigator? = null

    fun initialize(application: Application) {
        if (_initState.value !is NavigationInitState.Uninitialized) return
        _initState.value = NavigationInitState.Initializing

        NavigationApi.getNavigator(application, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(readyNavigator: Navigator) {
                navigator = readyNavigator
                _initState.value = NavigationInitState.Ready(readyNavigator)
            }

            override fun onError(errorCode: Int) {
                _initState.value = NavigationInitState.Error(errorCode)
            }
        })
    }

    fun shutdown() {
        navigator?.let {
            it.stopGuidance()
            it.cleanup()
        }
        navigator = null
        _initState.value = NavigationInitState.Uninitialized
    }
}
