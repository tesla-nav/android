package io.github.teslanav.app.services.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

private const val TAG = "OverlayToast"

/**
 * Replaces android.widget.Toast for messages emitted from a Service: on this ROM
 * (car head unit), Toasts coming from a context with no foreground Activity are blocked
 * by the system (BadTokenException, permission denied for window type). So we draw our
 * own popup via TYPE_APPLICATION_OVERLAY, the same window type as the overlay bubble,
 * which does work (SYSTEM_ALERT_WINDOW permission already granted).
 */
class OverlayToast(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var removeRunnable: Runnable? = null

    fun show(message: String, durationMs: Long = 2500) {
        handler.post {
            removeCurrent()

            val textView = TextView(context).apply {
                text = message
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#CC202020"))
                setPadding(32, 24, 32, 24)
                textSize = 14f
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = 200
            }

            try {
                windowManager.addView(textView, params)
                currentView = textView
                val runnable = Runnable { removeCurrent() }
                removeRunnable = runnable
                handler.postDelayed(runnable, durationMs)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to display the overlay message: ${e.message}")
            }
        }
    }

    private fun removeCurrent() {
        removeRunnable?.let { handler.removeCallbacks(it) }
        removeRunnable = null
        currentView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // already removed, nothing to do
            }
        }
        currentView = null
    }
}
