package com.dangehub.stickerbridge.plugin.eagle.data

data class Folder(
    val id: String,
    val name: String,
    val parentId: String?,
    val children: List<Folder> = emptyList()
)
