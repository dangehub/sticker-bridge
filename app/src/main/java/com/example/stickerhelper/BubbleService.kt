package com.example.stickerhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/**
 * 悬浮球前台服务。
 *
 * 在屏幕边缘显示一个可交互的圆形按钮，不依赖无障碍服务。
 *
 * 交互行为：
 * - 短按 → 打开表情选择器
 * - 长按 → 拖动位置
 * - 拖到边缘 → 自动收缩成小圆点（可选项）
 *
 * 显示控制：
 * - 有 AccessibilityService 时：由 [AppDetectService] 发 ACTION_SHOW/HIDE 控制
 * - 无 AccessibilityService 时：启动后持续显示，手动使用
 */
class BubbleService : Service() {

    companion object {
        private const val TAG = "BubbleService"
        private const val CHANNEL_ID = "bubble_service"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_MS = 300L
        private const val EDGE_SHRINK_THRESHOLD_DP = 30
        private const val BUBBLE_SIZE_DP = 48
        private const val SHRUNK_SIZE_DP = 24

        const val ACTION_SHOW = "com.example.stickerhelper.action.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.example.stickerhelper.action.HIDE_BUBBLE"

        /** 悬浮球是否正在显示（供其他组件查询） */
        @Volatile
        var isShowing: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null

    /** 拖动相关 */
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialViewX = 0
    private var initialViewY = 0
    private var isDragging = false
    private var touchDownTime = 0L

    /** 是否已收缩（边缘自动收缩） */
    private var isShrunk = false
    private var shrinkEnabled = true

    /** 布局参数缓存 */
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        shrinkEnabled = getShrinkPreference()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showBubble()
            ACTION_HIDE -> hideBubble()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeBubbleView()
        super.onDestroy()
    }

    /** 重新读取边缘收缩偏好设置 */
    fun refreshShrinkPreference() {
        shrinkEnabled = getShrinkPreference()
    }

    private fun getShrinkPreference(): Boolean {
        val prefs: SharedPreferences =
            getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("edge_shrink", true)
    }

    /** 保存悬浮球位置到偏好设置 */
    private fun saveBubblePosition(x: Int, y: Int) {
        getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("bubble_x", x)
            .putInt("bubble_y", y)
            .apply()
    }

    /** 显示悬浮球 */
    private fun showBubble() {
        if (bubbleView != null) {
            if (bubbleView?.visibility != View.VISIBLE) {
                bubbleView?.visibility = View.VISIBLE
                isShowing = true
            }
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted")
            isShowing = false
            return
        }

        try {
            createBubbleView()
            isShowing = true
            Log.d(TAG, "Bubble shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show bubble", e)
            isShowing = false
        }
    }

    /** 隐藏悬浮球 */
    private fun hideBubble() {
        bubbleView?.visibility = View.GONE
        isShowing = false
        Log.d(TAG, "Bubble hidden")
    }

    /** 创建悬浮球视图 */
    private fun createBubbleView() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        bubbleView = inflater.inflate(R.layout.bubble_overlay, null)

        val bubbleSize = dpToPx(BUBBLE_SIZE_DP)
        layoutParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            // 恢复上次保存的位置
            val savedX = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
                .getInt("bubble_x", 0)
            val savedY = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
                .getInt("bubble_y", 200)
            x = savedX
            y = savedY
        }

        // 触摸处理：短按打开 / 长按拖动
        bubbleView?.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        windowManager.addView(bubbleView, layoutParams)

        // 前台服务通知
        startForeground(NOTIFICATION_ID, createNotification())
    }

    /** 处理触摸事件 */
    private fun handleTouch(event: MotionEvent) {
        val params = layoutParams ?: return

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialViewX = params.x
                initialViewY = params.y
                isDragging = false
                touchDownTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                // 移动超过阈值才算拖动
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true
                    params.x = initialViewX + dx
                    params.y = initialViewY + dy

                    // 拖动时展开（如果之前收缩了）
                    if (isShrunk) {
                        expandBubble(params)
                    }

                    try {
                        windowManager.updateViewLayout(bubbleView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Update layout failed", e)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - touchDownTime

                if (isDragging) {
                    // 拖动结束 → 吸附到边缘 + 可选收缩
                    snapToEdge(params)
                } else if (elapsed < LONG_PRESS_MS && !isShrunk) {
                    // 短按（300ms 内）→ 打开表情选择器
                    openStickerPicker()
                } else if (isShrunk) {
                    // 收缩状态下点击 → 展开
                    expandBubble(params)
                }
            }
        }
    }

    /** 展开悬浮球到正常大小 */
    private fun expandBubble(params: WindowManager.LayoutParams) {
        isShrunk = false
        val fullSize = dpToPx(BUBBLE_SIZE_DP)
        params.width = fullSize
        params.height = fullSize
        try {
            windowManager.updateViewLayout(bubbleView, params)
            bubbleView?.findViewById<ImageView>(R.id.bubble_icon)?.let {
                it.layoutParams.width = fullSize
                it.layoutParams.height = fullSize
                it.requestLayout()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Expand failed", e)
        }
    }

    /** 吸附到屏幕边缘 */
    private fun snapToEdge(params: WindowManager.LayoutParams) {
        try {
            val dm = resources.displayMetrics
            val screenWidth = dm.widthPixels

            val snapLeft = params.x < screenWidth / 2
            params.x = if (snapLeft) 0 else screenWidth - dpToPx(BUBBLE_SIZE_DP)

            windowManager.updateViewLayout(bubbleView, params)

            // 保存位置
            saveBubblePosition(params.x, params.y)

            // 可选项：边缘收缩
            if (shrinkEnabled) {
                shrinkBubble(params)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Snap to edge failed", e)
        }
    }

    /** 收缩悬浮球为小圆点 */
    private fun shrinkBubble(params: WindowManager.LayoutParams) {
        isShrunk = true
        val shrunkSize = dpToPx(SHRUNK_SIZE_DP)
        params.width = shrunkSize
        params.height = shrunkSize
        try {
            windowManager.updateViewLayout(bubbleView, params)
            bubbleView?.findViewById<ImageView>(R.id.bubble_icon)?.let {
                it.layoutParams.width = shrunkSize
                it.layoutParams.height = shrunkSize
                it.requestLayout()
            }
            Log.d(TAG, "Bubble shrunk to edge")
        } catch (e: Exception) {
            Log.w(TAG, "Shrink failed", e)
        }
    }

    /** 打开表情选择器 */
    private fun openStickerPicker() {
        val foregroundPkg = AppDetectService.queryForegroundPackage()
        Log.d(TAG, "openStickerPicker: foregroundPackage=$foregroundPkg")
        val intent = Intent(this, StickerPickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            // 在气泡点击瞬间实时查询前台包名（此时用户还在微信/QQ里）
            putExtra("foreground_package", foregroundPkg)
        }
        startActivity(intent)
    }

    /** 移除悬浮球视图 */
    private fun removeBubbleView() {
        try {
            bubbleView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remove view failed", e)
        } finally {
            bubbleView = null
            layoutParams = null
            isShowing = false
            isShrunk = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bubble_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.bubble_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_running))
                .setSmallIcon(R.drawable.ic_bubble)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_running))
                .setSmallIcon(R.drawable.ic_bubble)
                .setOngoing(true)
                .build()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
