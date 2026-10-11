package com.example.hudmapapp.ui.screens.hudScreen

import com.example.hudmapapp.navigation.NavigationSessionState
import com.example.hudmapapp.navigation.NavigationState
import com.example.hudmapapp.navigation.SessionRecoveryAction
import com.example.hudmapapp.navigation.recoveryActionFor
import com.example.hudmapapp.navigation.userMessageFor

internal sealed interface HudContent {
    data object Idle : HudContent
    data object Starting : HudContent
    data class Guidance(val frozen: Boolean, val statusLine: String?) : HudContent
    data class Interrupted(val message: String, val canResume: Boolean) : HudContent
    data object Arrived : HudContent
    data class Error(val message: String, val canRetry: Boolean) : HudContent
}

internal fun hudContentFor(
    sessionState: NavigationSessionState,
    navigationState: NavigationState
): HudContent {
    return when (sessionState) {
        is NavigationSessionState.Idle,
        is NavigationSessionState.Stopped -> HudContent.Idle
        is NavigationSessionState.Starting -> HudContent.Starting
        is NavigationSessionState.Active -> HudContent.Guidance(
            frozen = false,
            statusLine = null
        )
        is NavigationSessionState.Rerouting,
        is NavigationSessionState.OffRoute -> HudContent.Guidance(
            frozen = true,
            statusLine = userMessageFor(sessionState)
        )
        is NavigationSessionState.Interrupted -> HudContent.Interrupted(
            message = userMessageFor(sessionState) ?: "Navigation paused.",
            canResume = recoveryActionFor(sessionState) == SessionRecoveryAction.RESUME
        )
        is NavigationSessionState.Arrived -> HudContent.Arrived
        is NavigationSessionState.Error -> HudContent.Error(
            message = userMessageFor(sessionState) ?: "Something went wrong.",
            canRetry = recoveryActionFor(sessionState) == SessionRecoveryAction.RETRY_START
        )
        is NavigationSessionState.Stopping -> HudContent.Guidance(
            frozen = true,
            statusLine = null
        )
    }
}
