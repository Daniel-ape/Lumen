package com.yourname.lumen.core.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Keeps the stack trace of the last crash on the device, so it can be read under Settings > About after the
 * app is opened again. Nothing is sent anywhere. Only the error text is stored, never logins or addresses.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(Date().toString() + "\n" + Log.getStackTraceString(error).take(6000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
