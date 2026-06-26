package com.example.stickerhelper

import android.content.Context
import org.json.JSONObject

/**
 * 表情发送次数持久化追踪。
 *
 * 用 SharedPreferences 存一个 JSON map（stickerId -> count），
 * 供「最常用」排序使用。零依赖：JSON 用 Android 内置 org.json 解析。
 *
 * 存储格式：
 * ```
 * {"MQURLJXZ5PX4L":12,"MQURK3Z79K595":5}
 * ```
 */
class SendCountTracker(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读取某个表情的累计发送次数，未记录过返回 0。 */
    fun getCount(id: String): Int = readMap()[id] ?: 0

    /** 某个表情发送次数 +1 并落盘。 */
    fun increment(id: String) {
        val map = readMap().toMutableMap()
        map[id] = (map[id] ?: 0) + 1
        writeMap(map)
    }

    /** 返回全部 stickerId -> count 映射（只读快照）。 */
    fun getAllCounts(): Map<String, Int> = readMap()

    // ---- internals ----

    private fun readMap(): Map<String, Int> {
        val raw = prefs.getString(KEY_COUNTS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, Int>()
            for (key in obj.keys()) {
                out[key] = obj.optInt(key, 0)
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeMap(map: Map<String, Int>) {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, v)
        }
        prefs.edit().putString(KEY_COUNTS, obj.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "sticker_send_counts"
        const val KEY_COUNTS = "counts_json"
    }
}
