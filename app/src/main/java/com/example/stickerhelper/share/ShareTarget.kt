package com.example.stickerhelper.share

import android.content.Context
import com.example.stickerhelper.data.StickerItem

/**
 * 发送目标抽象。
 *
 * 输出侧的扩展点：新增一个发送方式（QQ、微信、剪贴板、未来的无障碍…）
 * 只需在 JSON 配置里加一条，并实现此接口，无需改 UI 或数据源。
 *
 * @see IntentShareTarget
 * @see ClipboardShareTarget
 * @see ShareTargetLoader
 */
interface ShareTarget {
    /** 唯一标识，用于 JSON 配置覆盖与持久化选中状态。 */
    val id: String

    /** 展示名称（顶部目标切换行 / 长按选择器中显示）。 */
    val displayName: String

    /** emoji 图标，如 "💬"。 */
    val icon: String

    /**
     * 执行发送。
     *
     * @return [ShareResult.Success] 或 [ShareResult.Failed]（带是否可 fallback 标记）
     */
    suspend fun share(context: Context, sticker: StickerItem): ShareResult
}

/**
 * 发送结果。Failed 带 [canFallback] 标记，调用方可决定是否回退到选择器或下一个目标。
 */
sealed class ShareResult {
    data object Success : ShareResult()
    data class Failed(val message: String, val canFallback: Boolean = false) : ShareResult()
}
