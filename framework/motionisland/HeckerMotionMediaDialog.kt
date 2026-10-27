// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.session.MediaSession
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.systemui.compose.ComposeInitializer
import com.android.systemui.statusbar.phone.SystemUIDialog

/** The lockscreen/QS card itself, bound to the capsule's current-user music session. */
object HeckerMotionMediaDialog {
    @JvmStatic
    fun show(context: Context, token: MediaSession.Token, dismissed: Runnable): Dialog {
        val popupContext = context.createWindowContext(SystemUIDialog.DIALOG_WINDOW_TYPE, null)
        val dialog = SystemUIDialog(popupContext)
        val root = object : FrameLayout(popupContext) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                ComposeInitializer.onAttachedToWindow(this)
            }
            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                ComposeInitializer.onDetachedFromWindow(this)
            }
        }
        val compose = ComposeView(popupContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                HeckerMediaCard(modifier = Modifier.fillMaxWidth(), hideWhenInactive = true,
                    sessionToken = token, musicOnly = true, onInactive = { dialog.dismiss() })
            }
        }
        root.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        // ComposeInitializer requires the direct android.R.id.content child, rather than
        // AlertDialog's nested custom panel.
        dialog.create()
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { compose.disposeComposition(); dismissed.run() }
        dialog.show()
        val density = context.resources.displayMetrics.density
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            setLayout(minOf((440 * density).toInt(),
                context.resources.displayMetrics.widthPixels - (24 * density).toInt()),
                ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply {
                y = (40 * density).toInt()
                dimAmount = .16f
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        return dialog
    }
}
