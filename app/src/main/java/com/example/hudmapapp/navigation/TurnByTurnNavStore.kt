package com.example.hudmapapp.navigation

object TurnByTurnNavStore {

    @Volatile
    var latest: Any? = null

    fun clear() {
        latest = null
    }
}
