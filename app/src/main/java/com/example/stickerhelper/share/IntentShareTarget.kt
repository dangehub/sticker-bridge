package com.example.stickerhelper.share

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.stickerhelper.data.StickerItem

/**
 * Intent 类型目标的配置（来自 JSON 的 "config" 节点）。
 *
 * @param packageName 目标 App 包名，如 "com.tencent.mobileqq"
 * @param componentName 精确 Activity，格式 "pkg/cls"，设了就跳过目标 App 内部入口面板
 * @param mimeType MIME 类型，默认 image 通用类型
 * @param action Intent action，默认 ACTION_SEND
 * @param grantReadUriPermission 是否授予 URI 读权限（FileProvider/SAF URI 必需）
 */
data class IntentTargetConfig(
    val packageName: String,
    val componentName: String? = null,
    val mimeType: String = "image/*",
    val action: String = Intent.ACTION_SEND,
    val grantReadUriPermission: Boolean = true,
)

/**
 * Intent 类型发送目标。
 *
 * 通过 ACTION_SEND Intent 把图片发到指定 App。
 * - 配置了 [IntentTargetConfig.componentName] → 精确跳转（跳过选择器），例如 QQ「发送给好友」
 * - 只配置 [IntentTargetConfig.packageName] → 限定包名，由目标 App 自行处理入口
 *
 * 这是 QQShareHelper 的配置化通用版本，所有 Intent 类发送目标共享同一实现。
 *
 * @see QQShareHelper
 */
class IntentShareTarget(
    override val id: String,
    override val displayName: String,
    override val icon: String,
    private val config: IntentTargetConfig,
) : ShareTarget {

    /** 目标 App 的包名，用于前台进程匹配 */
    val packageName: String get() = config.packageName

    override suspend fun share(context: Context, sticker: StickerItem): ShareResult {
        val imageUri = Uri.parse(sticker.filePath)
        val intent = Intent(config.action).apply {
            type = config.mimeType
            putExtra(Intent.EXTRA_STREAM, imageUri)
            if (config.grantReadUriPermission) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val comp = config.componentName
            if (comp != null) {
                // 精确跳转到指定 Activity（跳过目标 App 内部入口面板）
                // unflattenFromString 正确处理 "pkg/cls" 和 "pkg/.cls" 两种格式
                val cn = ComponentName.unflattenFromString(comp)
                if (cn != null) {
                    component = cn
                }
            } else if (config.packageName.isNotEmpty()) {
                // 限定包名，由目标 App 自行选择入口
                `package` = config.packageName
            }
        }

        return try {
            context.startActivity(intent)
            ShareResult.Success
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Target not available: $id", e)
            ShareResult.Failed("未找到 ${displayName}，请确认已安装", canFallback = true)
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied for $id", e)
            ShareResult.Failed("无权限发送到 ${displayName}", canFallback = true)
        } catch (e: Exception) {
            Log.w(TAG, "Share failed for $id", e)
            ShareResult.Failed("发送失败：${e.message}", canFallback = false)
        }
    }

    private companion object {
        const val TAG = "IntentShareTarget"
    }
}
