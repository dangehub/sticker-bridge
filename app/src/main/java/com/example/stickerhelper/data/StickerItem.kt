package com.example.stickerhelper.data

/**
 * 统一表情数据模型。
 *
 * 所有数据源（Eagle、未来本地方案等）的输出都转成此结构，
 * 上层 UI 只认这个，不关心数据来源。
 */
data class StickerItem(
    val id: String,
    val name: String,
    val filePath: String,
    val fileExtension: String,
    val folders: List<String>,    // 文件夹名称（已从 Eagle 的 folder ID 解析为 name）
    val tags: List<String>,
    val annotation: String?,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val modificationTime: Long,
    val sendCount: Int = 0,   // 由 SendCountTracker 在 UI 层填充，数据源不感知
)
