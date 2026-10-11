package com.example.hudmapapp.navigation

/**
 * Holds the latest [Any] = NavInfo delivered by the Navigation SDK's
 * turn-by-turn service. The service runs in the same process, so a volatile
 * singleton is a safe hand-off to the guidance feed polling loop.
 */
object TurnByTurnNavStore {

    @Volatile
    var latest: Any? = null

    fun clear() {
        latest = null
    }
}
