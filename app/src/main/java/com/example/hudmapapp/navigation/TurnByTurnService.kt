package com.example.hudmapapp.navigation

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.google.android.libraries.mapsplatform.turnbyturn.TurnByTurnManager

class TurnByTurnService : Service() {

    private lateinit var incomingMessenger: Messenger

    private class IncomingNavStepHandler(
        looper: Looper,
        val turnByTurnManager: TurnByTurnManager = TurnByTurnManager.createInstance()
    ) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            if (TurnByTurnManager.MSG_NAV_INFO == msg.what) {
                turnByTurnManager.readNavInfoFromBundle(msg.data)?.let { navInfo ->
                    TurnByTurnNavStore.latest = navInfo
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread(
            "TurnByTurnService",
            Process.THREAD_PRIORITY_DEFAULT
        )
        thread.start()
        incomingMessenger = Messenger(IncomingNavStepHandler(thread.looper))
    }

    override fun onBind(intent: Intent?): IBinder = incomingMessenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        TurnByTurnNavStore.clear()
        return super.onUnbind(intent)
    }
}
