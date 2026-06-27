package com.dangehub.stickerbridge.plugin.eagle.data

data class StickerItem(
    val id: String,
    val name: String,
    val filePath: String,
    val fileExtension: String,
    val folders: List<String>,
    val tags: List<String>,
    val annotation: String?,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val modificationTime: Long,
)
