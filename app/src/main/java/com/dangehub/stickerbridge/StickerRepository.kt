package com.dangehub.stickerbridge

import com.dangehub.stickerbridge.data.StickerItem
import com.dangehub.stickerbridge.data.StickerSource

/**
 * 表情仓库。
 *
 * 仅做一层薄封装，把具体数据源（StickerSource）注入进来，供 UI 调用。
 * v0.2 起，数据来自 EagleSource，占位图逻辑已移除。
 */
class StickerRepository(private val source: StickerSource) {
    suspend fun getAll(): List<StickerItem> = source.getAll()
    suspend fun getFolders() = source.getFolders()
    suspend fun getTags() = source.getTags()
    suspend fun search(query: String): List<StickerItem> = source.search(query)
    suspend fun filter(folderId: String?, tags: List<String>?): List<StickerItem> =
        source.filter(folderId, tags)
    val displayName: String get() = source.displayName
}
