package com.stripedlens.calcloner.ui.dialogs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import com.stripedlens.calcloner.AppConstants

/**
 * Configures hardware background window blur behind pop-up dialogs (supported on Android 12+ / API 31+).
 */
@Composable
fun ApplyDialogBlurEffect() {
    val view = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val window = findDialogWindow(view)
            window?.let {
                it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                it.attributes = it.attributes.apply {
                    blurBehindRadius = AppConstants.Ui.DialogBlurBehindRadiusPx
                }
            }
        }
    }
}

private fun findDialogWindow(view: View): Window? {
    // In Jetpack Compose Dialogs, the DialogWindowProvider interface is attached to the view or its parent
    var current: Any? = view
    while (current != null) {
        if (current is androidx.compose.ui.window.DialogWindowProvider) {
            return current.window
        }
        val parent = if (current is View) current.parent else null
        if (parent == null && current is View) {
            var ctx: Context? = current.context
            while (ctx is ContextWrapper) {
                if (ctx is androidx.compose.ui.window.DialogWindowProvider) return ctx.window
                if (ctx is Activity) return ctx.window
                ctx = ctx.baseContext
            }
        }
        current = parent
    }
    return null
}
