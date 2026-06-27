package com.dangehub.stickerbridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Log

/**
 * 分享表情到 QQ 的 Intent 工具。
 *
 * QQ 注册了多个独立的 Activity 来处理不同分享入口（发送给好友、发送到我的电脑、收藏等）。
 * 我们通过 PackageManager 动态查找「发送给好友」的 Activity 并精确跳转，
 * 跳过 QQ 内部的入口选择面板。
 */
object QQShareHelper {

    private const val TAG = "QQShareHelper"
    private const val QQ_PACKAGE = "com.tencent.mobileqq"
    private const val SEND_TO_FRIEND_LABEL = "发送给好友"

    /** 缓存找到的目标组件（运行时不再重复查询） */
    private var sendToFriendComponent: ComponentName? = null

    /**
     * 构建精确跳转到 QQ「发送给好友」的 Intent。
     *
     * @param context 上下文
     * @param imageUri 表情图片的 content:// URI（来自 SAF 或 FileProvider）
     * @return 配置好的 Intent，直接打开 QQ 的联系人选择器
     * @throws IllegalStateException 如果找不到「发送给好友」Activity
     */
    fun createShareToFriendIntent(context: Context, imageUri: Uri): Intent {
        val targetComponent = getSendToFriendComponent(context)
            ?: throw IllegalStateException("未找到 QQ「发送给好友」入口")

        return Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            component = targetComponent
        }
    }

    /** 检查 QQ 是否已安装 */
    fun isQQInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(QQ_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 泛型分享 Intent（fallback 用）。
     * 不指定具体 Activity，由 QQ 自己弹内部入口面板让用户选择。
     */
    fun createShareIntent(context: Context, imageUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            `package` = QQ_PACKAGE
        }
    }

    /**
     * 在 QQ 的所有分享 Activity 中找到「发送给好友」那个。
     */
    private fun getSendToFriendComponent(context: Context): ComponentName? {
        sendToFriendComponent?.let { return it }

        val pm = context.packageManager
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
        }

        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(baseIntent, 0)
            .filter { it.activityInfo.packageName == QQ_PACKAGE }

        Log.d(TAG, "Found ${resolveInfos.size} QQ share activities")

        for (info in resolveInfos) {
            val label = info.loadLabel(pm)?.toString() ?: ""
            Log.d(TAG, "  Activity: ${info.activityInfo.name} label='$label'")

            if (label == SEND_TO_FRIEND_LABEL) {
                sendToFriendComponent = ComponentName(
                    info.activityInfo.packageName,
                    info.activityInfo.name
                )
                Log.d(TAG, "✅ Found '发送给好友': ${sendToFriendComponent}")
                return sendToFriendComponent
            }
        }

        // fallback：Activity 名启发式匹配
        for (info in resolveInfos) {
            val name = info.activityInfo.name
            if (name.contains("ShareToFriend", ignoreCase = true) ||
                name.contains("Forward", ignoreCase = true) ||
                name.contains("SendToFriend", ignoreCase = true)
            ) {
                sendToFriendComponent = ComponentName(
                    info.activityInfo.packageName, name
                )
                Log.d(TAG, "✅ Matched by name fallback: $name")
                return sendToFriendComponent
            }
        }

        Log.w(TAG, "❌ Could not find '发送给好友' activity in QQ")
        return null
    }
}
