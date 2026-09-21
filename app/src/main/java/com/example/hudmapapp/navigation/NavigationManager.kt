package com.example.hudmapapp.navigation

import android.app.Application
import android.util.Log
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
    data object TermsNotAccepted : NavigationInitState
}

class NavigationManager {

    private val _initState = MutableStateFlow<NavigationInitState>(NavigationInitState.Uninitialized)
    val initState: StateFlow<NavigationInitState> = _initState.asStateFlow()

    private var navigator: Navigator? = null

    private fun setState(state: NavigationInitState) {
        Log.d("HudMapNav", "NavigationInitState -> $state")
        _initState.value = state
    }

    fun initialize(application: Application) {
        if (_initState.value !is NavigationInitState.Uninitialized) return
        if (!NavigationApi.areTermsAccepted(application)) {
            setState(NavigationInitState.TermsNotAccepted)
            return
        }
        setState(NavigationInitState.Initializing)

        NavigationApi.getNavigator(application, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(readyNavigator: Navigator) {
                navigator = readyNavigator
                setState(NavigationInitState.Ready(readyNavigator))
            }

            override fun onError(errorCode: Int) {
                if (errorCode == NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING) {
                    // Permission is requested asynchronously by the UI; go back
                    // to Uninitialized so initialize() is retried once the
                    // grant lands instead of being stuck in Error forever.
                    Log.w("HudMapNav", "getNavigator: location permission missing, will retry after grant")
                    setState(NavigationInitState.Uninitialized)
                } else {
                    setState(NavigationInitState.Error(errorCode))
                }
            }
        })
    }

    fun notifyTermsAccepted(application: Application) {
        if (_initState.value !is NavigationInitState.TermsNotAccepted &&
            _initState.value !is NavigationInitState.Error
        ) {
            return
        }
        setState(NavigationInitState.Uninitialized)
        initialize(application)
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
