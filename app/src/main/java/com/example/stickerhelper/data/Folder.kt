package com.example.stickerhelper.data

/**
 * 文件夹模型（树形）。
 *
 * parentId 为 null 表示根目录（Eagle 里 parent 为空字符串或不存在）。
 */
data class Folder(
    val id: String,
    val name: String,
    val parentId: String?,
    val children: List<Folder> = emptyList()
)
