package com.dangehub.stickerbridge.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.util.Log
import com.dangehub.stickerbridge.data.StickerSource

/**
 * 表情插件管理器。
 *
 * 扫描已安装的 APK 中声明了 STICKER_PLUGIN action 的 Service，
 * 读取其 meta-data，绑定后返回 PluginStickerSource 供 UI 层使用。
 */
object PluginManager {

    private const val TAG = "PluginManager"

    /**
     * 发现的插件列表（不重复绑定）。
     */
    private val discoveredPlugins = mutableListOf<PluginEntry>()

    data class PluginEntry(
        val packageName: String,
        val serviceName: String,
        val displayName: String,
        val author: String,
        val version: String,
        val description: String,
    )

    /**
     * 扫描已安装的插件。
     */
    fun discover(context: Context): List<PluginEntry> {
        discoveredPlugins.clear()

        val pm = context.packageManager
        val intent = Intent(PluginProtocol.ACTION_STICKER_PLUGIN)
        val resolveInfos = try {
            pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query plugins", e)
            return emptyList()
        }

        for (info in resolveInfos) {
            val serviceInfo: ServiceInfo = info.serviceInfo
            val pkg = serviceInfo.packageName
            val svc = serviceInfo.name
            val meta = serviceInfo.metaData

            val name = meta?.getString(PluginProtocol.META_PLUGIN_NAME) ?: pkg
            val author = meta?.getString("com.dangehub.stickerbridge.plugin_author") ?: ""
            val version = meta?.getString(PluginProtocol.META_PLUGIN_VERSION) ?: ""
            val description = meta?.getString(PluginProtocol.META_PLUGIN_DESCRIPTION) ?: ""

            val entry = PluginEntry(pkg, svc, name, author, version, description)
            discoveredPlugins.add(entry)
            Log.d(TAG, "Discovered plugin: $name ($pkg/$svc)")
        }

        return discoveredPlugins.toList()
    }

    /**
     * 绑定指定插件，返回 PluginStickerSource。
     * 调用方需要保持对返回对象的引用，否则 Service 会被系统回收。
     */
    fun bind(context: Context, entry: PluginEntry): PluginStickerSource? {
        val source = PluginStickerSource(context, PluginStickerSource.PluginInfo(
            packageName = entry.packageName,
            displayName = entry.displayName,
            author = entry.author,
            version = entry.version,
        ))
        val ok = source.bind()
        return if (ok) source else null
    }

    /**
     * 发现并绑定第一个可用插件。
     * 如果没找到插件，返回 null。
     */
    fun discoverAndBindFirst(context: Context): PluginStickerSource? {
        val plugins = discover(context)
        if (plugins.isEmpty()) return null
        return bind(context, plugins.first())
    }
}
