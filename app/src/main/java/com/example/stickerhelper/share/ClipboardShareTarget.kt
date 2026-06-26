package com.example.stickerhelper.share

import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.stickerhelper.data.StickerItem

/**
 * 剪贴板类型发送目标。
 *
 * 把表情以 URI 形式复制到系统剪贴板。系统剪贴板通过 ContentResolver 持有 URI 引用，
 * 读取方（如聊天框粘贴）会通过 ContentResolver 再去取真实图片字节，因此这里直接
 * 用 [ClipData.newUri] 即可，无需自己把 InputStream 读进来再拷贝。
 *
 * 注意：sticker.filePath 可能是 content:// URI（SAF/FileProvider）或 file:// URI，
 * 两者都能放进剪贴板；但粘贴方只能读到自己有权限的 URI。
 */
class ClipboardShareTarget(
    override val id: String,
    override val displayName: String,
    override val icon: String,
) : ShareTarget {

    override suspend fun share(context: Context, sticker: StickerItem): ShareResult {
        return try {
            val uri = Uri.parse(sticker.filePath)
            // newUri 会通过 contentResolver 解析出 mime 写入 ClipDescription
            val clip = ClipData.newUri(context.contentResolver, "Sticker", uri)
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
            if (cm !is android.content.ClipboardManager) {
                return ShareResult.Failed("无法获取剪贴板服务")
            }
            cm.setPrimaryClip(clip)
            Log.d(TAG, "Copied sticker to clipboard: ${sticker.id}")
            ShareResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard share failed", e)
            ShareResult.Failed("复制失败：${e.message}")
        }
    }

    private companion object {
        const val TAG = "ClipboardShareTarget"
    }
}
