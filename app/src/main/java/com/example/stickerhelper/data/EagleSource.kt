package com.example.stickerhelper.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Eagle 4.0 数据源实现。
 *
 * 通过 SAF DocumentFile API 读取 Eagle 库，兼容 Android 11+ Scoped Storage。
 *
 * Eagle 4.0 数据结构：
 *   {library}/
 *   ├── metadata.json           ← 库级：folders 树、smartFolders、quickAccess
 *   ├── tags.json               ← historyTags、starredTags
 *   └── images/
 *       ├── {id}.info/
 *       │   ├── metadata.json   ← 单图元数据
 *       │   └── {name}.{ext}    ← 图片文件
 *       └── ...
 *
 * @param context 用于 ContentResolver 访问 SAF 文件
 * @param libraryUri SAF tree URI（由 LibraryConfig 保存）
 */
class EagleSource(
    private val context: Context,
    private val libraryUri: Uri,
) : StickerSource {

    override val displayName: String = "Eagle 图库"

    private var loaded = false
    private var allItems: List<StickerItem> = emptyList()
    private var folderTree: List<Folder> = emptyList()
    private var tags: List<String> = emptyList()
    private var folderIdToName: Map<String, String> = emptyMap()

    private suspend fun ensureLoaded() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            if (loaded) return@withContext
            loadLibrary()
            loaded = true
        }
    }

    private fun loadLibrary() {
        val libraryRoot = DocumentFile.fromTreeUri(context, libraryUri) ?: return
        val folderList = mutableListOf<Pair<String, FolderRaw>>()
        val idToName = mutableMapOf<String, String>()

        // 1. 库级 metadata.json：解析文件夹树
        val metaFile = libraryRoot.findFile("metadata.json")
        if (metaFile?.exists() == true) {
            val text = readTextFromUri(metaFile.uri) ?: return
            val meta = JSONObject(text)
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
        }
        folderIdToName = idToName

        // 2. tags.json：historyTags + starredTags
        val tagSet = LinkedHashSet<String>()
        val tagsFile = libraryRoot.findFile("tags.json")
        if (tagsFile?.exists() == true) {
            val text = readTextFromUri(tagsFile.uri)
            if (text != null) {
                val tagsObj = JSONObject(text)
                collectTags(tagsObj.optJSONArray("historyTags"), tagSet)
                collectTags(tagsObj.optJSONArray("starredTags"), tagSet)
            }
        }

        // 3. 遍历 images/*.info/metadata.json
        val items = mutableListOf<StickerItem>()
        val imagesDir = libraryRoot.findFile("images")
        if (imagesDir?.exists() == true) {
            val infoDirs = (imagesDir.listFiles() ?: emptyArray())
                .filter { it.name?.endsWith(".info") == true }
                .sortedBy { it.name.orEmpty() }

            for (infoDir in infoDirs) {
                val itemMeta = infoDir.findFile("metadata.json") ?: continue
                val text = readTextFromUri(itemMeta.uri) ?: continue
                val item: JSONObject
                try {
                    item = JSONObject(text)
                } catch (_: Exception) {
                    continue
                }

                // 过滤已删除
                if (item.optBoolean("isDeleted", false)) continue

                val id = item.optString("id")
                val name = item.optString("name")
                val ext = item.optString("ext")
                if (id.isEmpty() || name.isEmpty() || ext.isEmpty()) continue

                // folders ID 数组 → 名称数组
                val folderNames = mutableListOf<String>()
                val folderIds = item.optJSONArray("folders")
                if (folderIds != null) {
                    for (i in 0 until folderIds.length()) {
                        val fid = folderIds.optString(i)
                        val fname = folderIdToName[fid]
                        if (fname != null) folderNames.add(fname)
                    }
                }

                // 标签
                val itemTags = mutableListOf<String>()
                val tagsArr = item.optJSONArray("tags")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        val t = tagsArr.optString(i)
                        if (t.isNotEmpty()) {
                            itemTags.add(t)
                            tagSet.add(t)
                        }
                    }
                }

                val annotation = item.optString("annotation", "")
                val annotationStr = if (annotation.isNullOrEmpty()) null else annotation

                // 图片文件路径：使用 infoDir 下第一个匹配扩展名的文件
                val imageFile = findImageFile(infoDir, name, ext)
                val filePath = imageFile?.uri?.toString() ?: ""

                items.add(
                    StickerItem(
                        id = id,
                        name = name,
                        filePath = filePath,
                        fileExtension = ext,
                        folders = folderNames,
                        tags = itemTags,
                        annotation = annotationStr,
                        fileSize = item.optLong("size", 0L),
                        width = item.optInt("width", 0),
                        height = item.optInt("height", 0),
                        modificationTime = pickModificationTime(item),
                    )
                )
            }
        }

        allItems = items
        tags = tagSet.toList()
        folderTree = buildFolderTree(folderList)
    }

    override suspend fun getAll(): List<StickerItem> {
        ensureLoaded()
        return allItems
    }

    override suspend fun getFolders(): List<Folder> {
        ensureLoaded()
        return folderTree
    }

    override suspend fun getTags(): List<String> {
        ensureLoaded()
        return tags
    }

    override suspend fun search(query: String): List<StickerItem> {
        ensureLoaded()
        val q = query.trim()
        if (q.isEmpty()) return allItems
        return allItems.filter { item ->
            item.name.contains(q, ignoreCase = true) ||
                (item.annotation?.contains(q, ignoreCase = true) == true) ||
                item.tags.any { it.contains(q, ignoreCase = true) }
        }
    }

    override suspend fun filter(folderId: String?, tags: List<String>?): List<StickerItem> {
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

    // ---- helpers ----

    /** 通过 ContentResolver 读取 SAF URI 对应的文本内容。 */
    private fun readTextFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        } catch (_: Exception) {
            null
        }
    }

    /** 在 .info 目录中查找图片文件。优先匹配 name.ext，其次查找首个匹配扩展名的文件。 */
    private fun findImageFile(infoDir: DocumentFile, name: String, ext: String): DocumentFile? {
        // 优先精确匹配 name.ext
        val exact = infoDir.findFile("$name.$ext")
        if (exact?.exists() == true) return exact

        // 兜底：扫描目录找第一个图片文件
        val imageExts = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        return (infoDir.listFiles() ?: emptyArray())
            .firstOrNull { f ->
                val fName = f.name?.lowercase() ?: return@firstOrNull false
                imageExts.any { fName.endsWith(".$it") }
            }
    }

    private fun collectTags(arr: JSONArray?, out: LinkedHashSet<String>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val t = arr.optString(i)
            if (t.isNotEmpty()) out.add(t)
        }
    }

    private fun pickModificationTime(item: JSONObject): Long {
        return item.optLong("modificationTime", 0L)
            .takeIf { it > 0 }
            ?: item.optLong("mtime", 0L)
            .takeIf { it > 0 }
            ?: item.optLong("btime", 0L)
    }

    private fun buildFolderTree(raw: List<Pair<String, FolderRaw>>): List<Folder> {
        val byId = raw.associate { it.first to FolderRawHolder(it.second) }
        for ((id, holder) in byId) {
            val parent = holder.raw.parent
            if (!parent.isNullOrEmpty() && byId.containsKey(parent)) {
                byId[parent]!!.children.add(id to holder)
            }
        }
        val roots = mutableListOf<Folder>()
        for ((id, holder) in byId) {
            val parent = holder.raw.parent
            if (parent.isNullOrEmpty() || !byId.containsKey(parent)) {
                roots.add(buildNode(id, holder, byId))
            }
        }
        return roots
    }

    private fun buildNode(
        id: String,
        holder: FolderRawHolder,
        byId: Map<String, FolderRawHolder>,
    ): Folder {
        val children = holder.children.map { (childId, _) -> buildNode(childId, byId[childId]!!, byId) }
        return Folder(
            id = id,
            name = holder.raw.name,
            parentId = holder.raw.parent.takeIf { it.isNotEmpty() },
            children = children,
        )
    }

    private data class FolderRaw(val name: String, val parent: String)
    private class FolderRawHolder(val raw: FolderRaw) {
        val children: MutableList<Pair<String, FolderRawHolder>> = mutableListOf()
    }
}
