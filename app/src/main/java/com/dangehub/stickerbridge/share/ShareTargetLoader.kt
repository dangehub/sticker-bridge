package com.dangehub.stickerbridge.share

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 从 JSON 加载 [ShareTarget] 列表。
 *
 * 加载顺序（后者覆盖前者，按 target id 合并）：
 * 1. Assets `output-targets-default.json`（内置默认：QQ + 微信 + 剪贴板）
 * 2. `/sdcard/StickerBridge/output-targets.json`（用户自定义覆盖）
 *
 * 合并规则：用户配置中出现的 id 完整覆盖内置的；用户没有的保留内置。
 * 这样用户只需在 JSON 里写要改的那几条。
 *
 * 零依赖：JSON 用 Android 内置 org.json。
 */
object ShareTargetLoader {

    private const val TAG = "ShareTargetLoader"
    private const val ASSET_DEFAULT = "output-targets-default.json"
    private const val USER_OVERRIDE_PATH = "/sdcard/StickerBridge/output-targets.json"

    suspend fun load(context: Context): List<ShareTarget> {
        // id -> 原始 JSON 节点（保留原始顺序：先内置，再用户覆盖）
        val merged = LinkedHashMap<String, JSONObject>()

        // 1. 内置默认
        runCatching { context.assets.open(ASSET_DEFAULT).use { it.readBytes().toString(Charsets.UTF_8) } }
            .onFailure { Log.w(TAG, "Default asset missing", it) }
            .getOrNull()
            ?.let { parseTargetsInto(it, merged) }

        // 2. 用户覆盖
        val userFile = File(USER_OVERRIDE_PATH)
        if (userFile.exists()) {
            runCatching { userFile.readText(Charsets.UTF_8) }
                .onFailure { Log.w(TAG, "User override unreadable", it) }
                .getOrNull()
                ?.let { parseTargetsInto(it, merged) }
        }

        return merged.values.mapNotNull { instantiate(it) }
    }

    private fun parseTargetsInto(raw: String, sink: LinkedHashMap<String, JSONObject>) {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("targets") ?: return
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            if (id.isNotEmpty()) sink[id] = obj
        }
    }

    /** 把一个 target JSON 节点实例化成 [ShareTarget]。type 未知时返回 null 并告警。 */
    private fun instantiate(obj: JSONObject): ShareTarget? {
        val id = obj.optString("id").trim()
        val name = obj.optString("name").ifEmpty { id }
        val icon = obj.optString("icon").ifEmpty { "📤" }
        val type = obj.optString("type").trim().lowercase()
        val config = obj.optJSONObject("config") ?: JSONObject()

        return when (type) {
            "intent" -> IntentShareTarget(
                id = id,
                displayName = name,
                icon = icon,
                config = IntentTargetConfig(
                    packageName = config.optString("packageName").trim(),
                    componentName = config.optString("componentName").trim().takeIf { it.isNotEmpty() },
                    mimeType = config.optString("mimeType").ifEmpty { "image/*" }.trim(),
                    action = config.optString("action").ifEmpty { android.content.Intent.ACTION_SEND }.trim(),
                    grantReadUriPermission = config.optBoolean("grantReadUriPermission", true),
                ),
            )
            "clipboard" -> ClipboardShareTarget(
                id = id,
                displayName = name,
                icon = icon,
            )
            else -> {
                Log.w(TAG, "Unknown share target type='$type' for id='$id', skipped")
                null
            }
        }
    }
}
