package com.dangehub.stickerbridge.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.dangehub.stickerbridge.data.Folder
import com.dangehub.stickerbridge.data.StickerItem
import com.dangehub.stickerbridge.data.StickerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 插件包装器：将远程插件 Service 封装为 StickerSource 接口。
 * 所有 IPC 调用在 Binder 线程同步执行。
 */
class PluginStickerSource(
    private val context: Context,
    private val pluginInfo: PluginInfo,
) : StickerSource {

    data class PluginInfo(
        val packageName: String,
        val displayName: String,
        val author: String,
        val version: String,
    )

    companion object {
        private const val TAG = "PluginSrc"
        private const val TRANSACT_QUERY = 1
    }

    override val displayName: String get() = pluginInfo.displayName

    // 缓存的 binder
    @Volatile
    private var binder: IBinder? = null
    private val connectedLatch = CountDownLatch(1)

    /**
     * 等待 Service 连接就绪（最多 5 秒）。
     * bind() 后调用此方法，确保 Binder 可用。
     */
    suspend fun awaitConnected(): Boolean = withContext(Dispatchers.IO) {
        connectedLatch.await(5, TimeUnit.SECONDS)
        binder != null
    }

    fun init(libraryPath: String) {
        sendQuery("init", """{"libraryPath":"$libraryPath"}""")
    }

    override suspend fun getAll(): List<StickerItem> {
        return parseItems(sendQuery("getAll", "{}"))
    }

    override suspend fun getFolders(): List<Folder> {
        return parseFolders(sendQuery("getFolders", "{}"))
    }

    override suspend fun getTags(): List<String> {
        return parseStringList(sendQuery("getTags", "{}"))
    }

    override suspend fun search(query: String): List<StickerItem> {
        return parseItems(sendQuery("search", """{"query":"$query"}"""))
    }

    override suspend fun filter(folderId: String?, tags: List<String>?): List<StickerItem> {
        val params = JSONObject().apply {
            folderId?.let { put("folderId", it) }
            if (tags != null) put("tags", JSONArray(tags))
        }
        return parseItems(sendQuery("filter", params.toString()))
    }

    // ---- IPC ----

    fun bind(): Boolean {
        val intent = Intent(PluginProtocol.ACTION_STICKER_PLUGIN).apply {
            `package` = pluginInfo.packageName
        }
        return try {
            context.bindService(intent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    binder = service
                    connectedLatch.countDown()
                    Log.d(TAG, "Bound to ${pluginInfo.displayName}")
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    binder = null
                    Log.d(TAG, "Disconnected from ${pluginInfo.displayName}")
                }
            }, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to ${pluginInfo.displayName}", e)
            false
        }
    }

    private fun sendQuery(method: String, params: String): String {
        val svc = binder ?: return """{"error":"Not bound"}"""
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeString(method)
            data.writeString(params)
            svc.transact(TRANSACT_QUERY, data, reply, 0)
            return reply.readString() ?: ""
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // ---- JSON parsing ----

    private fun parseItems(json: String): List<StickerItem> {
        if (json.startsWith("{")) return emptyList() // error response
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StickerItem(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    filePath = o.optString("filePath"),
                    fileExtension = o.optString("fileExtension"),
                    folders = jsonToStringList(o.optJSONArray("folders")),
                    tags = jsonToStringList(o.optJSONArray("tags")),
                    annotation = o.optString("annotation", "").takeIf { it.isNotEmpty() },
                    fileSize = o.optLong("fileSize", 0L),
                    width = o.optInt("width", 0),
                    height = o.optInt("height", 0),
                    modificationTime = o.optLong("modificationTime", 0L),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseItems error", e)
            emptyList()
        }
    }

    private fun parseFolders(json: String): List<Folder> {
        if (json.startsWith("{")) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> parseFolder(arr.getJSONObject(i)) }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseFolder(o: JSONObject): Folder = Folder(
        id = o.optString("id"),
        name = o.optString("name"),
        parentId = o.optString("parentId", "").takeIf { it.isNotEmpty() },
        children = jsonToFolderList(o.optJSONArray("children")),
    )

    private fun jsonToFolderList(arr: JSONArray?): List<Folder> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i -> parseFolder(arr.getJSONObject(i)) }
    }

    private fun parseStringList(json: String): List<String> {
        if (json.startsWith("{")) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    private fun jsonToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
    }
}
