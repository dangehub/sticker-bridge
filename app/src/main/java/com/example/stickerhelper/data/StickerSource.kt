package com.example.stickerhelper.data

/**
 * 表情数据源抽象接口。
 *
 * 上层（StickerRepository / UI）只依赖此接口，不耦合具体数据来源。
 * search / filter 当前实现为基础版本，后续迭代再增强。
 */
interface StickerSource {
    suspend fun getAll(): List<StickerItem>
    suspend fun getFolders(): List<Folder>
    suspend fun getTags(): List<String>
    suspend fun search(query: String): List<StickerItem>
    suspend fun filter(folderId: String?, tags: List<String>?): List<StickerItem>

    /** 数据源展示名（如 "Eagle 图库"），用于 UI 标题。 */
    val displayName: String
}
