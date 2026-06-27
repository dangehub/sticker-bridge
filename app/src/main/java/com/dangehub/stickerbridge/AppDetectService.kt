package com.dangehub.stickerbridge

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dangehub.stickerbridge.share.ShareTargetLoader

/**
 * 无障碍服务：检测用户是否在已启用的输出目标 App（QQ/微信等）的输入框中。
 *
 * 功能：
 * 1. 从输出目标配置中读取启用的 App 包名列表，动态识别
 * 2. 检测到已启用 App 的输入框获得焦点 → 显示悬浮球
 * 3. 离开输入框或窗口 → 隐藏悬浮球
 * 4. 保留自动点击 QQ「发送给好友」按钮
 */
class AppDetectService : AccessibilityService() {

    companion object {
        private const val TAG = "AppDetect"
        private const val AUTO_SEND_TIMEOUT_MS = 3000L
        private const val AUTO_PASTE_TIMEOUT_MS = 5000L

        /** 当前运行的服务实例（供外部实时查询） */
        private var serviceInstance: AppDetectService? = null

        /** 输入框的常见 ID 后缀 */
        private val CHAT_INPUT_IDS = listOf(
            "input",
            "et_input",
            "edit_text",
            "AieEditText",
        )

        /** 输入框的常见 className */
        private val CHAT_INPUT_CLASSES = listOf(
            "android.widget.EditText",
            "com.tencent.mobileqq.activity.aio.AIEEditText",
            "android.widget.MultiAutoCompleteTextView",
        )

        /** 是否在任意启用的 App 的输入框中（供 BubbleService 查询） */
        @Volatile
        var isInChat: Boolean = false
            private set

        /** 标记：下次检测到 QQ 分享面板时自动点击「发送给好友」 */
        @Volatile
        var pendingAutoSend: Boolean = false

        /** 当前前台 App 的包名（仅在聊天中时有效） */
        @Volatile
        var currentForegroundPackage: String? = null

        /** 标记：需要自动粘贴到当前输入框（微信剪贴板粘贴模式） */
        @Volatile
        var pendingAutoPaste: Boolean = false

        /** 自动粘贴的时间戳（用于超时判断） */
        @Volatile
        var autoPasteTimestamp: Long = 0L

        /**
         * 实时查询当前前台 App 包名，通过无障碍服务实例。
         * 与 [currentForegroundPackage] 的区别：这是即时查询，不是缓存值。
         * 无障碍服务未运行时返回 null。
         */
        fun queryForegroundPackage(): String? {
            return serviceInstance?.queryCurrentForegroundPackage()
        }
    }

    /** 当前被追踪的 App 包名列表（从输出目标配置动态加载） */
    private val trackedPackages = mutableSetOf<String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoSendTimeoutRunnable = Runnable {
        if (pendingAutoSend) {
            Log.d(TAG, "Auto-send timeout, clearing flag")
            pendingAutoSend = false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = this
        reloadTrackedPackages()
        Log.d(TAG, "AppDetectService connected, tracking: $trackedPackages")
    }

    /**
     * 实时查询当前前台 App 的包名（无缓存）。
     * 通过无障碍的 rootInActiveWindow 获取当前活动窗口的包名。
     * 返回 null 表示无法确定（服务未运行或无活动窗口）。
     */
    fun queryCurrentForegroundPackage(): String? {
        val pkg = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (_: Exception) {
            null
        }
        Log.d(TAG, "queryCurrentForegroundPackage: rootPkg=$pkg, root=${rootInActiveWindow != null}")
        return pkg
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "reload") {
            reloadTrackedPackages()
            Log.d(TAG, "Tracked packages reloaded: $trackedPackages")
        }
        return START_STICKY
    }

    /** 重新加载启用的输出目标包名列表 */
    fun reloadTrackedPackages() {
        trackedPackages.clear()
        kotlinx.coroutines.runBlocking {
            val allTargets = ShareTargetLoader.load(this@AppDetectService)
            val enabledIds = loadEnabledTargetIds()
            for (target in allTargets) {
                if (target.id !in enabledIds) continue
                val pkg = when (target) {
                    is com.dangehub.stickerbridge.share.IntentShareTarget -> target.packageName
                    else -> null
                }
                if (!pkg.isNullOrEmpty()) {
                    trackedPackages.add(pkg)
                }
            }
        }
        Log.d(TAG, "Tracked packages updated: $trackedPackages")
    }

    private fun loadEnabledTargetIds(): Set<String> {
        val prefs = getSharedPreferences("target_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("enabled_targets", null)
            ?: setOf("qq_friend", "wechat_friend", "clipboard", "generic_share")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 只处理已启用目标 App 的事件
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in trackedPackages) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentForegroundPackage = pkg
                checkInputFocus()
                tryAutoClickSendToFriend()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkInputFocus()
                tryAutoClickSendToFriend()
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                currentForegroundPackage = pkg
                // 输入框获得/失去焦点
                if (event.className?.toString() in CHAT_INPUT_CLASSES ||
                    isInputFieldByAttributes(event)
                ) {
                    updateChatState(true)
                } else {
                    // 焦点离开输入框，延迟隐藏防闪烁
                    mainHandler.postDelayed({
                        checkInputFocus()
                    }, 500)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        isInChat = false
        pendingAutoSend = false
        stopBubbleService()
        mainHandler.removeCallbacks(autoSendTimeoutRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isInChat = false
        pendingAutoSend = false
        stopBubbleService()
        mainHandler.removeCallbacks(autoSendTimeoutRunnable)
    }

    /** 检测当前活动窗口中是否有输入框获得焦点 */
    private fun checkInputFocus() {
        val rootNode = rootInActiveWindow ?: run {
            updateChatState(false)
            return
        }

        try {
            val hasFocusedInput = findFocusedInput(rootNode)
            updateChatState(hasFocusedInput)

            // 自动粘贴：如果有待处理的粘贴请求，且输入框有焦点
            if (hasFocusedInput && pendingAutoPaste) {
                // 超时检查（5秒后自动清除）
                if (autoPasteTimestamp > 0 && System.currentTimeMillis() - autoPasteTimestamp > AUTO_PASTE_TIMEOUT_MS) {
                    Log.w(TAG, "Auto-paste expired, clearing")
                    pendingAutoPaste = false
                    autoPasteTimestamp = 0L
                } else {
                    performAutoPaste()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking input focus", e)
        } finally {
            rootNode.recycle()
        }
    }

    /** 递归查找是否有获得焦点的输入框 */
    private fun findFocusedInput(node: AccessibilityNodeInfo): Boolean {
        if (node.isFocused && isInputField(node)) return true

        if (node.childCount > 0) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    if (findFocusedInput(child)) return true
                } finally {
                    child.recycle()
                }
            }
        }

        return false
    }

    /** 尝试自动点击「发送给好友」 */
    private fun tryAutoClickSendToFriend() {
        if (!pendingAutoSend) return
        if (currentForegroundPackage != "com.tencent.mobileqq") return

        val rootNode = rootInActiveWindow ?: return

        try {
            val targetButton = findNodeByText(rootNode, "发送给好友")
            if (targetButton != null) {
                Log.d(TAG, "Found '发送给好友', auto-clicking...")
                targetButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                pendingAutoSend = false
                mainHandler.removeCallbacks(autoSendTimeoutRunnable)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error auto-clicking send to friend", e)
        } finally {
            rootNode.recycle()
        }
    }

    /** 递归查找包含指定文本的节点 */
    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (nodeText == text || contentDesc == text) {
            return AccessibilityNodeInfo.obtain(node)
        }

        if (node.childCount > 0) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    val result = findNodeByText(child, text)
                    if (result != null) return result
                } finally {
                    child.recycle()
                }
            }
        }

        return null
    }

    /** 执行自动粘贴到当前获得焦点的输入框 */
    private fun performAutoPaste() {
        val rootNode = rootInActiveWindow ?: return
        try {
            val focusedInput = findFocusedInputNode(rootNode)
            if (focusedInput != null) {
                val success = focusedInput.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                Log.d(TAG, "Auto-paste: performed=${success} on ${focusedInput.className}")
                if (success) {
                    pendingAutoPaste = false
                    autoPasteTimestamp = 0L
                }
            } else {
                Log.w(TAG, "Auto-paste: no focused input found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-paste error", e)
        } finally {
            rootNode.recycle()
        }
    }

    /** 递归查找获得焦点的输入框节点 */
    private fun findFocusedInputNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = findFocusedInputNode(child)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun isInputField(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEditable && node.className != "android.widget.EditText") return false
        if (node.isPassword) return false

        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""

        return className in CHAT_INPUT_CLASSES ||
                viewId.lowercase() in CHAT_INPUT_IDS ||
                viewId.lowercase().contains("input")
    }

    /** 通过事件属性判断是否为输入框 */
    private fun isInputFieldByAttributes(event: AccessibilityEvent): Boolean {
        val className = event.className?.toString() ?: ""
        return className in CHAT_INPUT_CLASSES || className.contains("EditText")
    }

    /** 更新聊天状态并控制悬浮球 */
    private fun updateChatState(inChat: Boolean) {
        if (inChat == isInChat) return

        isInChat = inChat
        Log.d(TAG, "Chat state changed: inChat=$inChat")

        if (inChat) {
            startBubbleService()
        } else {
            stopBubbleService()
        }
    }

    private fun startBubbleService() {
        try {
            val intent = Intent(this, BubbleService::class.java)
            intent.action = BubbleService.ACTION_SHOW
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BubbleService", e)
        }
    }

    private fun stopBubbleService() {
        try {
            val intent = Intent(this, BubbleService::class.java)
            intent.action = BubbleService.ACTION_HIDE
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop BubbleService", e)
        }
    }
}
