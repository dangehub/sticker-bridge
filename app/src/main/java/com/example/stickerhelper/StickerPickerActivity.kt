package com.example.stickerhelper

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.example.stickerhelper.data.EagleSource
import com.example.stickerhelper.data.Folder
import com.example.stickerhelper.data.StickerItem
import com.example.stickerhelper.plugin.PluginManager
import com.example.stickerhelper.share.ShareResult
import com.example.stickerhelper.share.ShareTarget
import com.example.stickerhelper.share.ShareTargetLoader
import kotlinx.coroutines.launch

/**
 * 表情选择弹窗。
 *
 * 以半透明 Dialog 形式展示一个表情网格。
 *
 * v0.3：搜索 + 排序（最新/最常用/名称）+ 发送次数追踪。
 * v0.4：输出侧配置化 —— 发送目标由 JSON 加载，不再硬编码到 QQ。
 *       顶部目标切换行选默认目标，短按即发到该目标；
 *       长按表情弹出目标选择器，临时指定发送目标。
 */
class StickerPickerActivity : ComponentActivity() {

    private lateinit var sendTracker: SendCountTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sendTracker = SendCountTracker(this)

        val libraryUri = LibraryConfig.getLibraryUri(this)
            ?.let { Uri.parse(it) }
        if (libraryUri == null) {
            Toast.makeText(this, "请先在主界面选择 Eagle 图库目录", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MaterialTheme {
                StickerPickerDialog(
                    context = this,
                    libraryUri = libraryUri,
                    libraryPath = LibraryConfig.getLibraryPath(this) ?: "",
                    sendTracker = sendTracker,
                    onDismiss = { finish() },
                    onSend = { sticker, target -> onSend(sticker, target) },
                )
            }
        }
    }

    /**
     * 通过选中的 [ShareTarget] 发送表情。
     *
     * 配置驱动：所有发送方式（QQ、微信、剪贴板…）走同一条路径，
     * 成功才记一次发送次数并关闭弹窗，失败 Toast 提示。
     */
    private fun onSend(sticker: StickerItem, target: ShareTarget) {
        lifecycleScope.launch {
            when (val result = target.share(this@StickerPickerActivity, sticker)) {
                is ShareResult.Success -> {
                    sendTracker.increment(sticker.id)
                    finish()
                }
                is ShareResult.Failed -> {
                    Toast.makeText(this@StickerPickerActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * 排序方式。
 *
 * 排序是 UI 层职责，不影响数据源。
 */
enum class SortMode {
    NEWEST,     // modificationTime 降序
    MOST_USED,  // sendCount 降序
    NAME,       // name 升序（忽略大小写）
}

@Composable
private fun StickerPickerDialog(
    context: Context,
    libraryUri: Uri,
    libraryPath: String,
    sendTracker: SendCountTracker,
    onDismiss: () -> Unit,
    onSend: (StickerItem, ShareTarget) -> Unit,
) {
    var repository by remember { mutableStateOf<StickerRepository?>(null) }
    var loadingStatus by remember { mutableStateOf("正在连接插件…") }

    // 基础数据（已填充 sendCount），后续筛选/搜索/排序都基于此
    var allStickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }

    // 筛选状态
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 搜索 & 排序状态
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NEWEST) }

    // 当前展示的（已筛选 + 搜索 + 排序）列表
    var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }

    // 输出目标状态（配置驱动）
    var shareTargets by remember { mutableStateOf<List<ShareTarget>>(emptyList()) }
    var selectedTargetId by remember { mutableStateOf<String?>(null) }
    // 长按弹出的目标选择器：非空表示正在为该表情选目标
    var pickerForSticker by remember { mutableStateOf<StickerItem?>(null) }

    // 初始加载：先尝试插件，fallback 到内置 EagleSource；同时加载发送目标
    LaunchedEffect(Unit) {
        // 尝试插件
        val plugin = PluginManager.discoverAndBindFirst(context)
        if (plugin != null) {
            loadingStatus = "插件已连接，初始化…"
            val connected = plugin.awaitConnected()
            if (connected) {
                plugin.init(libraryPath)
                repository = StickerRepository(plugin)
            }
        }

        // fallback：插件不可用时用内置 EagleSource
        if (repository == null) {
            loadingStatus = "使用内置 EagleSource…"
            repository = StickerRepository(EagleSource(context, libraryUri))
        }

        // 加载数据
        val repo = repository ?: return@LaunchedEffect
        folders = repo.getFolders()
        tags = repo.getTags()
        // 填充 sendCount：数据源不感知发送次数，由 UI 层从本地存储补齐
        val counts = sendTracker.getAllCounts()
        allStickers = repo.getAll().map { it.copy(sendCount = counts[it.id] ?: 0) }
        ready = true

        // 加载发送目标配置
        val targets = ShareTargetLoader.load(context)
        shareTargets = targets
        if (selectedTargetId == null && targets.isNotEmpty()) {
            selectedTargetId = targets.first().id
        }
    }

    // 筛选 / 搜索 / 排序变化时重新计算展示列表
    // 流程：folder + tags 筛选 → 搜索过滤 → 排序
    // 全部在 UI 层对 allStickers（含 sendCount）做，保证三者可同时生效且不丢 sendCount
    LaunchedEffect(allStickers, selectedFolderId, selectedTags, searchQuery, sortMode) {
        if (!ready) return@LaunchedEffect

        // folder id → name（数据源按名称匹配，这里复刻同样的规则）
        val folderName = selectedFolderId?.let { fid -> folders.flattenIds().firstOrNull { it.first == fid }?.second }
        val wantedTags = selectedTags.takeIf { it.isNotEmpty() }?.toList()
        val q = searchQuery.trim()

        stickers = allStickers
            .asSequence()
            // 1. folder + tags 筛选
            .filter { item ->
                val folderOk = folderName == null || item.folders.contains(folderName)
                val tagsOk = wantedTags == null || wantedTags.all { tag -> item.tags.contains(tag) }
                folderOk && tagsOk
            }
            // 2. 搜索过滤（匹配 name / annotation / tags，忽略大小写）
            .filter { item ->
                if (q.isEmpty()) return@filter true
                item.name.contains(q, ignoreCase = true) ||
                    (item.annotation?.contains(q, ignoreCase = true) == true) ||
                    item.tags.any { it.contains(q, ignoreCase = true) }
            }
            .toList()
            // 3. 排序
            .let { items ->
                when (sortMode) {
                    SortMode.NEWEST -> items.sortedByDescending { it.modificationTime }
                    SortMode.MOST_USED -> items.sortedByDescending { it.sendCount }
                    SortMode.NAME -> items.sortedBy { it.name.lowercase() }
                }
            }
    }

    val selectedTarget = shareTargets.firstOrNull { it.id == selectedTargetId }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF5F5F5),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题
                Text(
                    text = "选择表情 · ${repository?.displayName ?: "…"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 发送目标切换行（短按发到此目标，长按表情另选）
                if (shareTargets.isNotEmpty()) {
                    TargetRow(
                        targets = shareTargets,
                        selectedId = selectedTargetId,
                        onSelect = { selectedTargetId = it },
                    )
                }

                // 搜索框（实时筛选 name / annotation / tags）
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text("搜索表情（名称/标签/备注）") },
                )

                // 分类筛选（横向滚动）
                if (folders.isNotEmpty()) {
                    FolderFilterRow(
                        folders = folders,
                        selectedId = selectedFolderId,
                        onSelect = { id ->
                            selectedFolderId = if (selectedFolderId == id) null else id
                        },
                    )
                }

                // 标签筛选（横向滚动，可多选）
                if (tags.isNotEmpty()) {
                    TagFilterRow(
                        tags = tags,
                        selectedTags = selectedTags,
                        onToggle = { tag ->
                            selectedTags = if (tag in selectedTags) {
                                selectedTags - tag
                            } else {
                                selectedTags + tag
                            }
                        },
                    )
                }

                // 排序切换（单选，默认最新）
                SortRow(
                    sortMode = sortMode,
                    onSelect = { sortMode = it },
                )

                if (!ready) {
                    Text(
                        text = loadingStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        color = Color.Gray
                    )
                } else if (stickers.isEmpty()) {
                    Text(
                        text = context.getString(R.string.label_no_stickers),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        color = Color.Gray
                    )
                } else {
                    // 表情网格
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(stickers) { sticker ->
                            StickerCell(
                                sticker = sticker,
                                onClick = {
                                    // 短按：发到当前选中的默认目标
                                    val target = selectedTarget
                                    if (target != null) {
                                        onSend(sticker, target)
                                    } else {
                                        Toast.makeText(context, "未配置发送目标", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLongClick = {
                                    // 长按：弹出目标选择器
                                    pickerForSticker = sticker
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // 长按目标选择器
    pickerForSticker?.let { sticker ->
        ShareTargetPickerDialog(
            targets = shareTargets,
            onDismiss = { pickerForSticker = null },
            onSelect = { target ->
                pickerForSticker = null
                onSend(sticker, target)
            },
        )
    }
}

/** 递归展开文件夹树为 (id, name) 列表，用于把选中的 folderId 解析成名称。 */
private fun List<Folder>.flattenIds(): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    fun walk(items: List<Folder>) {
        for (f in items) {
            out.add(f.id to f.name)
            walk(f.children)
        }
    }
    walk(this)
    return out
}

@Composable
private fun TargetRow(
    targets: List<ShareTarget>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        targets.forEach { target ->
            FilterChip(
                selected = target.id == selectedId,
                onClick = { onSelect(target.id) },
                label = { Text("${target.icon} ${target.displayName}") },
            )
        }
    }
}

@Composable
private fun ShareTargetPickerDialog(
    targets: List<ShareTarget>,
    onDismiss: () -> Unit,
    onSelect: (ShareTarget) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "发送到",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp),
                )
                targets.forEach { target ->
                    val label = "${target.icon} ${target.displayName}"
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(target) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderFilterRow(
    folders: List<Folder>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        folders.forEach { folder ->
            val id = folder.id
            FilterChip(
                selected = selectedId == id,
                onClick = { onSelect(id) },
                label = { Text(folder.name) },
            )
        }
    }
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selectedTags: Set<String>,
    onToggle: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = tag in selectedTags,
                onClick = { onToggle(tag) },
                label = { Text(tag) },
            )
        }
    }
}

@Composable
private fun SortRow(
    sortMode: SortMode,
    onSelect: (SortMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortRowEntry(SortMode.NEWEST, "最新", sortMode, onSelect)
        SortRowEntry(SortMode.MOST_USED, "最常用", sortMode, onSelect)
        SortRowEntry(SortMode.NAME, "名称", sortMode, onSelect)
    }
}

@Composable
private fun SortRowEntry(
    mode: SortMode,
    label: String,
    current: SortMode,
    onSelect: (SortMode) -> Unit,
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(
    sticker: StickerItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(Uri.parse(sticker.filePath)),
            contentDescription = sticker.name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
