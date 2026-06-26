package com.example.stickerplugineagle

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.example.stickerplugineagle.data.EagleSource
import com.example.stickerplugineagle.data.Folder
import com.example.stickerplugineagle.data.StickerItem
import org.json.JSONArray
import org.json.JSONObject

class EaglePluginService : Service() {

    companion object {
        private const val TAG = "EaglePlugin"
        private const val TRANSACT_QUERY = 1

        // Must match PluginProtocol in main app
        private const val METHOD_INIT = "init"
        private const val METHOD_GET_ALL = "getAll"
        private const val METHOD_GET_FOLDERS = "getFolders"
        private const val METHOD_GET_TAGS = "getTags"
        private const val METHOD_SEARCH = "search"
        private const val METHOD_FILTER = "filter"
        private const val METHOD_GET_DISPLAY_NAME = "getDisplayName"
        private const val METHOD_PING = "ping"
    }

    @Volatile
    private var eagleSource: EagleSource? = null

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != TRANSACT_QUERY) return super.onTransact(code, data, reply, flags)
            val method = data.readString() ?: ""
            val params = data.readString() ?: "{}"
            val result = handleQuery(method, params)
            reply?.writeString(result)
            return true
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Bound by ${intent.`package`}")
        return binder
    }

    private fun handleQuery(method: String, params: String): String {
        return try {
            when (method) {
                METHOD_INIT -> {
                    val path = JSONObject(params).optString("libraryPath", "")
                    if (path.isNotEmpty()) {
                        eagleSource = EagleSource(path)
                        """{"ok":true}"""
                    } else {
                        """{"ok":false,"error":"No libraryPath provided"}"""
                    }
                }
                METHOD_PING -> {
                    val ok = eagleSource != null
                    """{"ok":$ok,"name":"Eagle 图库"}"""
                }
                else -> {
                    val src = eagleSource
                    if (src == null) return """{"error":"Not initialized"}"""
                    when (method) {
                        METHOD_GET_DISPLAY_NAME -> """"${src.displayName}""""
                        METHOD_GET_ALL -> itemsToJson(src.getAll())
                        METHOD_GET_FOLDERS -> foldersToJson(src.getFolders())
                        METHOD_GET_TAGS -> listToJson(src.getTags())
                        METHOD_SEARCH -> {
                            val q = JSONObject(params).optString("query", "")
                            itemsToJson(src.search(q))
                        }
                        METHOD_FILTER -> {
                            val p = JSONObject(params)
                            itemsToJson(src.filter(
                                folderId = p.optString("folderId", "").takeIf { it.isNotEmpty() },
                                tags = jsonToStringList(p.optJSONArray("tags"))
                            ))
                        }
                        else -> """{"error":"Unknown method: $method"}"""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling $method", e)
            """{"error":"${e.message}"}"""
        }
    }

    private fun itemsToJson(items: List<StickerItem>): String =
        JSONArray(items.map { item -> JSONObject().apply {
            put("id", item.id); put("name", item.name); put("filePath", item.filePath)
            put("fileExtension", item.fileExtension)
            put("folders", JSONArray(item.folders)); put("tags", JSONArray(item.tags))
            put("annotation", item.annotation ?: ""); put("fileSize", item.fileSize)
            put("width", item.width); put("height", item.height); put("modificationTime", item.modificationTime)
        }}).toString()

    private fun foldersToJson(folders: List<Folder>): String =
        JSONArray(folders.map { folderToJson(it) }).toString()

    private fun folderToJson(folder: Folder): JSONObject = JSONObject().apply {
        put("id", folder.id); put("name", folder.name); put("parentId", folder.parentId ?: "")
        put("children", JSONArray(folder.children.map { folderToJson(it) }))
    }

    private fun listToJson(list: List<String>): String = JSONArray(list).toString()

    private fun jsonToStringList(arr: JSONArray?): List<String>? {
        if (arr == null) return null
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
    }
}
