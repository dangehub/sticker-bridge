package com.dangehub.stickerbridge.plugin.eagle.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Eagle 4.0 数据源（插件版）。
 * 同步、File-based 读取，运行在 Binder 线程。
 */
class EagleSource(private val libraryPath: String) {

    val displayName: String = "Eagle 图库"

    private var allItems: List<StickerItem> = emptyList()
    private var folderTree: List<Folder> = emptyList()
    private var tags: List<String> = emptyList()
    private var folderIdToName: Map<String, String> = emptyMap()
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        loadLibrary()
        loaded = true
    }

    @Synchronized
    fun getAll(): List<StickerItem> {
        ensureLoaded(); return allItems
    }

    @Synchronized
    fun getFolders(): List<Folder> {
        ensureLoaded(); return folderTree
    }

    @Synchronized
    fun getTags(): List<String> {
        ensureLoaded(); return tags
    }

    @Synchronized
    fun search(query: String): List<StickerItem> {
        ensureLoaded()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return allItems
        return allItems.filter { item ->
            item.name.lowercase().contains(q) ||
                (item.annotation?.lowercase()?.contains(q) == true) ||
                item.tags.any { it.lowercase().contains(q) }
        }
    }

    @Synchronized
    fun filter(folderId: String?, tags: List<String>?): List<StickerItem> {
        ensureLoaded()
        val folderName = folderId?.takeIf { it.isNotEmpty() }?.let { folderIdToName[it] }
        val wantedTags = tags?.filter { it.isNotEmpty() }
        return allItems.filter { item ->
            val folderOk = folderName == null || item.folders.contains(folderName)
            val tagsOk = wantedTags == null || wantedTags.isEmpty() ||
                wantedTags.all { tag -> item.tags.contains(tag) }
            folderOk && tagsOk
        }
    }

    private fun loadLibrary() {
        val dir = File(libraryPath)
        if (!dir.exists()) return

        val folderList = mutableListOf<Pair<String, FolderRaw>>()
        val idToName = mutableMapOf<String, String>()

        // 1. metadata.json — folders
        val metaFile = File(dir, "metadata.json")
        if (metaFile.exists()) {
            try {
                val meta = JSONObject(metaFile.readText())
                val folders: JSONArray = meta.optJSONArray("folders") ?: JSONArray()
                for (i in 0 until folders.length()) {
                    val f = folders.optJSONObject(i) ?: continue
                    val id = f.optString("id")
                    val name = f.optString("name")
                    val parent = f.optString("parent")
                    if (id.isNotEmpty()) {
                        idToName[id] = name
                        folderList.add(id to FolderRaw(name, parent))
                    }
                }
            } catch (_: Exception) {}
        }
        folderIdToName = idToName

        // 2. tags.json
        val tagSet = LinkedHashSet<String>()
        val tagsFile = File(dir, "tags.json")
        if (tagsFile.exists()) {
            try {
                val tagsObj = JSONObject(tagsFile.readText())
                collectTags(tagsObj.optJSONArray("historyTags"), tagSet)
                collectTags(tagsObj.optJSONArray("starredTags"), tagSet)
            } catch (_: Exception) {}
        }

        // 3. images/*.info/metadata.json
        val items = mutableListOf<StickerItem>()
        val imagesDir = File(dir, "images")
        val infoDirs = imagesDir.listFiles { f -> f.isDirectory && f.name.endsWith(".info") }
        if (infoDirs != null) {
            for (infoDir in infoDirs.sortedBy { it.name }) {
                val itemMeta = File(infoDir, "metadata.json")
                if (!itemMeta.exists()) continue
                try {
                    parseItem(itemMeta.readText(), infoDir, items, tagSet)
                } catch (_: Exception) { continue }
            }
        }

        allItems = items
        tags = tagSet.toList()
        folderTree = buildFolderTree(folderList)
    }

    private fun parseItem(text: String, infoDir: File, items: MutableList<StickerItem>, tagSet: LinkedHashSet<String>) {
        val item = JSONObject(text)
        if (item.optBoolean("isDeleted", false)) return
        val id = item.optString("id")
        val name = item.optString("name")
        val ext = item.optString("ext")
        if (id.isEmpty() || name.isEmpty() || ext.isEmpty()) return

        val folderNames = mutableListOf<String>()
        val folderIds = item.optJSONArray("folders")
        if (folderIds != null) {
            for (i in 0 until folderIds.length()) {
                val fname = folderIdToName[folderIds.optString(i)]
                if (fname != null) folderNames.add(fname)
            }
        }

        val itemTags = mutableListOf<String>()
        val tagsArr = item.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i)
                if (t.isNotEmpty()) { itemTags.add(t); tagSet.add(t) }
            }
        }

        val annotation = item.optString("annotation", "").takeIf { it.isNotEmpty() }
        val filePath = File(infoDir, "$name.$ext").absolutePath

        items.add(StickerItem(id, name, filePath, ext, folderNames, itemTags, annotation,
            item.optLong("size", 0L), item.optInt("width", 0), item.optInt("height", 0),
            pickModificationTime(item)))
    }

    private fun collectTags(arr: JSONArray?, out: LinkedHashSet<String>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val t = arr.optString(i)
            if (t.isNotEmpty()) out.add(t)
        }
    }

    private fun pickModificationTime(item: JSONObject): Long {
        return item.optLong("modificationTime", 0L).takeIf { it > 0 }
            ?: item.optLong("mtime", 0L).takeIf { it > 0 }
            ?: item.optLong("btime", 0L)
    }

    private fun buildFolderTree(raw: List<Pair<String, FolderRaw>>): List<Folder> {
        val byId = raw.associate { it.first to FolderRawHolder(it.second) }
        for ((id, holder) in byId) {
            val parent = holder.raw.parent
            if (!parent.isNullOrEmpty() && byId.containsKey(parent))
                byId[parent]!!.children.add(id to holder)
        }
        return byId.filter { (_, h) -> h.raw.parent.isNullOrEmpty() || !byId.containsKey(h.raw.parent) }
            .map { (id, h) -> buildNode(id, h, byId) }
    }

    private fun buildNode(id: String, holder: FolderRawHolder, byId: Map<String, FolderRawHolder>): Folder {
        return Folder(id, holder.raw.name, holder.raw.parent.takeIf { it.isNotEmpty() },
            holder.children.map { (cid, _) -> buildNode(cid, byId[cid]!!, byId) })
    }

    private data class FolderRaw(val name: String, val parent: String)
    private class FolderRawHolder(val raw: FolderRaw) {
        val children: MutableList<Pair<String, FolderRawHolder>> = mutableListOf()
    }
}
