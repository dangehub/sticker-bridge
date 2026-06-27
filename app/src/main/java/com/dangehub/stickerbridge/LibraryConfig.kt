package com.dangehub.stickerbridge

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract

/**
 * 图库路径配置管理。
 *
 * 通过 SAF（Storage Access Framework）目录选择器让用户选 Eagle 库位置，
 * 将真实文件系统路径保存到 SharedPreferences，供 EagleSource 用 java.io.File 读取。
 *
 * SAF 返回 content:// URI，但在 Android 本地存储的场景下，
 * 可以从 URI 中提取出真实的文件系统路径（如 /storage/emulated/0/...），
 * 这样可以保持 EagleSource 使用高效的 File API 而非慢速的 DocumentFile。
 */
object LibraryConfig {

    private const val PREFS_NAME = "sticker_bridge_prefs"
    private const val KEY_PATH = "eagle_library_path"
    private const val KEY_URI = "eagle_library_uri"

    /**
     * 从 SharedPreferences 读取已保存的图库路径。
     */
    fun getLibraryPath(context: Context): String? {
        return prefs(context).getString(KEY_PATH, null)
    }

    /**
     * 获取已保存的 SAF URI（用于显示已选择的库信息）。
     */
    fun getLibraryUri(context: Context): String? {
        return prefs(context).getString(KEY_URI, null)
    }

    /**
     * 保存 SAF 选择器返回的 URI，同时提取真实路径。
     */
    fun saveLibraryUri(context: Context, uri: Uri): String? {
        // 申请持久化读取权限，下次不用再选
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // 有些设备/文件管理器可能不支持持久化，不影响使用
        }

        val realPath = resolveRealPath(uri)
        prefs(context).edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_PATH, realPath ?: uri.toString())
            .apply()
        return realPath
    }

    /**
     * 清除已保存的图库路径。
     */
    fun clearLibrary(context: Context) {
        prefs(context).edit()
            .remove(KEY_PATH)
            .remove(KEY_URI)
            .apply()
    }

    /**
     * 是否已配置图库路径。
     */
    fun hasLibrary(context: Context): Boolean {
        return getLibraryPath(context) != null
    }

    /**
     * 从 SAF content:// URI 提取真实文件系统路径。
     *
     * 例：
     *   content://com.android.externalstorage.documents/tree/primary%3ASyncthing%2FEagle
     *   → /storage/emulated/0/Syncthing/Eagle
     *
     *   content://com.android.externalstorage.documents/tree/XXXX-XXXX%3AEagle
     *   → /storage/XXXX-XXXX/Eagle  （SD 卡）
     */
    private fun resolveRealPath(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val colonIndex = docId.indexOf(':')
        if (colonIndex < 0) return null

        val storageId = docId.substring(0, colonIndex)
        val path = docId.substring(colonIndex + 1)

        return if (storageId == "primary") {
            // 内置存储
            "/storage/emulated/0/$path"
        } else {
            // 外置 SD 卡
            "/storage/$storageId/$path"
        }
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
