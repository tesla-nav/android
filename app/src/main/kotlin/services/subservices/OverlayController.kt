package io.github.teslanav.app.services.subservices

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.teslanav.app.MainActivity
import io.github.teslanav.app.R
import io.github.teslanav.app.openapp.OpenAppProvider
import io.github.teslanav.app.services.SubService
import io.github.teslanav.app.services.helpers.OverlayToast
import io.github.teslanav.app.tesla.SyncResult
import io.github.teslanav.app.tesla.TeslaDestinationSync
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "OverlayController"

/** Floating overlay bubble (open app / open map). */
class OverlayController(private val context: Context) : SubService {
    companion object {
        private const val CLICK_DRAG_THRESHOLD_PX = 12
        private const val BUTTON_SIZE_DP = 56
    }

    private var windowManager: WindowManager? = null
    private var panelView: View? = null
    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val overlayToast by lazy { OverlayToast(context) }

    override fun start() {
        Log.d(TAG, "start")
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Missing draw-over-other-apps permission, skipping overlay")
            return
        }
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addPanel()
    }

    override fun stop() {
        Log.d(TAG, "stop")
        controllerScope.cancel()
        panelView?.let { windowManager?.removeView(it) }
        panelView = null
        windowManager = null
    }

    private fun addPanel() {
        val buttonSizePx = (BUTTON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        panel.addView(createButton(buttonSizePx, R.mipmap.ic_launcher_round, panel, params, ::openMainActivity))
        panel.addView(createButton(buttonSizePx, android.R.drawable.ic_menu_mapmode, panel, params, ::openMapApp, withBackground = true))

        panelView = panel
        windowManager?.addView(panel, params)
    }

    /** A drag started from any button moves the whole panel (shared [params]/[panel]). */
    private fun createButton(
        sizePx: Int,
        iconResId: Int,
        panel: View,
        params: WindowManager.LayoutParams,
        onTap: () -> Unit,
        withBackground: Boolean = false
    ): View {
        val button = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            if (withBackground) {
                setBackgroundResource(R.drawable.bg_overlay_button)
            }
            setImageResource(iconResId)
        }

        var touchStartX = 0f
        var touchStartY = 0f
        var layoutStartX = 0
        var layoutStartY = 0
        var moved = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    layoutStartX = params.x
                    layoutStartY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > CLICK_DRAG_THRESHOLD_PX || abs(dy) > CLICK_DRAG_THRESHOLD_PX) {
                        moved = true
                    }
                    params.x = layoutStartX + dx
                    params.y = layoutStartY + dy
                    windowManager?.updateViewLayout(panel, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onTap()
                    }
                    true
                }
                else -> false
            }
        }

        return button
    }

    private fun openMainActivity() {
        Log.d(TAG, "Opening MainActivity from the overlay")
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Sets the active Tesla destination in the nav app if there is one, otherwise just opens it. */
    private fun openMapApp() {
        Log.d(TAG, "Opening the navigation app from the overlay")
        controllerScope.launch {
            val result = TeslaDestinationSync(context).sync()
            if (result is SyncResult.DestinationOpened) {
                return@launch
            }
            withContext(Dispatchers.Main) {
                val strategy = OpenAppProvider.getPreferredStrategy(context)
                if (strategy == null) {
                    overlayToast.show(context.getString(R.string.sync_no_nav_app))
                } else {
                    strategy.open(context)
                }
            }
        }
    }
}
