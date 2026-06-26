package com.example.stickerhelper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务：检测用户是否在 QQ 聊天对话框内。
 *
 * 检测逻辑：
 * 1. 只处理来自 com.tencent.mobileqq 的事件
 * 2. 检查当前窗口是否包含聊天输入框（EditText）
 * 3. 当进入聊天 → 启动 BubbleService（显示悬浮球）
 * 4. 当离开聊天 → 停止 BubbleService（隐藏悬浮球）
 *
 * 额外功能：自动点击 QQ 分享面板的「发送给好友」
 * - 由 StickerPickerActivity 设置 pendingAutoSend = true
 * - 检测到「发送给好友」按钮后自动点击
 * - 3 秒超时自动清空标记
 */
class QQDetectAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "QQDetect"
        private const val QQ_PACKAGE = "com.tencent.mobileqq"
        private const val AUTO_SEND_TIMEOUT_MS = 3000L

        /** 输入框的常见 ID 后缀（不同 QQ 版本可能不同） */
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
        )

        /** 是否在 QQ 聊天中（供 BubbleService 查询） */
        @Volatile
        var isInQQChat: Boolean = false
            private set

        /** 标记：下次检测到 QQ 分享面板时自动点击「发送给好友」 */
        @Volatile
        var pendingAutoSend: Boolean = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoSendTimeoutRunnable = Runnable {
        if (pendingAutoSend) {
            Log.d(TAG, "Auto-send timeout, clearing flag")
            pendingAutoSend = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 只处理 QQ 的事件
        if (event.packageName != QQ_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                checkIfInQQChat()

                // 如果有待处理的自动点击，尝试找「发送给好友」按钮
                if (pendingAutoSend) {
                    tryAutoClickSendToFriend()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
        isInQQChat = false
        pendingAutoSend = false
        stopBubbleService()
        mainHandler.removeCallbacks(autoSendTimeoutRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isInQQChat = false
        pendingAutoSend = false
        stopBubbleService()
        mainHandler.removeCallbacks(autoSendTimeoutRunnable)
    }

    /** 检测当前是否在 QQ 聊天窗口中 */
    private fun checkIfInQQChat() {
        val rootNode = rootInActiveWindow ?: run {
            updateChatState(false)
            return
        }

        try {
            val hasChatInput = findChatInput(rootNode)
            updateChatState(hasChatInput)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking chat state", e)
        } finally {
            rootNode.recycle()
        }
    }

    /** 尝试自动点击「发送给好友」 */
    private fun tryAutoClickSendToFriend() {
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
        // 检查本节点
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (nodeText == text || contentDesc == text) {
            // 克隆一份，因为调用方会回收父节点
            return AccessibilityNodeInfo.obtain(node)
        }

        // 递归子节点
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

    /** 递归查找聊天输入框 */
    private fun findChatInput(node: AccessibilityNodeInfo): Boolean {
        if (isInputField(node)) return true

        if (node.childCount > 0) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    if (findChatInput(child)) return true
                } finally {
                    child.recycle()
                }
            }
        }

        return false
    }

    /** 判断一个节点是否是聊天输入框 */
    private fun isInputField(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEditable && node.className != "android.widget.EditText") return false
        if (node.isPassword) return false

        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""

        return className in CHAT_INPUT_CLASSES ||
                viewId.lowercase() in CHAT_INPUT_IDS ||
                viewId.lowercase().contains("input")
    }

    /** 更新聊天状态并控制悬浮球 */
    private fun updateChatState(inChat: Boolean) {
        if (inChat == isInQQChat) return

        isInQQChat = inChat
        Log.d(TAG, "QQ chat state changed: inChat=$inChat")

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
