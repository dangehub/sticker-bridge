package com.example.stickerhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
 * 在屏幕边缘显示一个可拖动的圆形按钮。
 * 点击后打开 StickerPickerActivity 让用户选择表情。
 *
 * 由 QQDetectAccessibilityService 控制显示/隐藏：
 * - ACTION_SHOW → 显示悬浮球（仅当权限已授予且窗口管理器已初始化）
 * - ACTION_HIDE → 隐藏悬浮球并移除视图
 * - 当退出 QQ 聊天时自动隐藏
 */
class BubbleService : Service() {

    companion object {
        private const val TAG = "BubbleService"
        private const val CHANNEL_ID = "bubble_service"
        private const val NOTIFICATION_ID = 1001

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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
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

    /** 显示悬浮球 */
    private fun showBubble() {
        if (bubbleView != null) {
            // 已经显示，无需重复操作
            if (bubbleView?.visibility != View.VISIBLE) {
                bubbleView?.visibility = View.VISIBLE
                isShowing = true
            }
            return
        }

        // 检查悬浮窗权限
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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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
            x = 0
            y = 200 // 初始位置：屏幕左侧靠上
        }

        // 设置触摸处理：拖动 + 点击
        bubbleView?.setOnTouchListener { _, event ->
            handleTouch(event, params)
            true // 消费所有触摸事件，否则 click 无法工作
        }

        windowManager.addView(bubbleView, params)

        // 启动前台服务通知（Android 8+ 必须）
        startForeground(NOTIFICATION_ID, createNotification())
    }

    /** 处理触摸事件：拖动悬浮球 + 点击打开选择器 */
    private fun handleTouch(event: MotionEvent, params: WindowManager.LayoutParams) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialViewX = params.x
                initialViewY = params.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                // 移动超过阈值才算拖动（避免误触）
                if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                    isDragging = true
                    params.x = initialViewX + dx
                    params.y = initialViewY + dy

                    try {
                        windowManager.updateViewLayout(bubbleView, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Update layout failed", e)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    // 拖动结束 → 吸附到边缘
                    snapToEdge(params)
                } else {
                    // 没有拖动 = 点击 → 打开表情选择器
                    openStickerPicker()
                }
            }
        }
    }

    /** 吸附到屏幕边缘 */
    private fun snapToEdge(params: WindowManager.LayoutParams) {
        try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels

            // 如果靠左半侧则吸附到左边缘，否则吸附到右边缘（部分隐藏）
            val snapLeft = params.x < screenWidth / 2
            params.x = if (snapLeft) 0 else screenWidth - dpToPx(48)

            windowManager.updateViewLayout(bubbleView, params)
        } catch (e: Exception) {
            Log.w(TAG, "Snap to edge failed", e)
        }
    }

    /** 打开表情选择器 */
    private fun openStickerPicker() {
        val intent = Intent(this, StickerPickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            isShowing = false
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
