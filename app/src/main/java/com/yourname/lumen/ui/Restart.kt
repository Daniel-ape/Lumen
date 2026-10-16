package com.yourname.lumen.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/**
 * Closes Lumen and opens it again so staged appearance changes take effect. This works on most
 * devices; if the system blocks it the app simply closes, and the user opens it again by hand.
 */
fun restartApp(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        ?: packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
        ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    context.findActivity()?.finishAffinity()
    Runtime.getRuntime().exit(0)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
