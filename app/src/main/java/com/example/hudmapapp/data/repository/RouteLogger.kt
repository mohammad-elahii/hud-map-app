package com.example.hudmapapp.data.repository

import android.util.Log

fun interface RouteLogger {
    fun log(priority: Int, message: String, throwable: Throwable?)

    companion object {
        const val TAG = "RoutePreview"

        fun android(): RouteLogger = RouteLogger { priority, message, throwable ->
            if (throwable != null) {
                Log.println(priority, TAG, "$message\n${Log.getStackTraceString(throwable)}")
            } else {
                Log.println(priority, TAG, message)
            }
        }

        fun noop(): RouteLogger = RouteLogger { _, _, _ -> Unit }
    }
}

internal fun RouteLogger.debug(message: String) = log(Log.DEBUG, message, null)

internal fun RouteLogger.warn(message: String, throwable: Throwable? = null) =
    log(Log.WARN, message, throwable)

internal fun RouteLogger.error(message: String, throwable: Throwable? = null) =
    log(Log.ERROR, message, throwable)
