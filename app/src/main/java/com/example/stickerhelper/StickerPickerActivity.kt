package com.example.stickerhelper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import coil.compose.rememberAsyncImagePainter
import com.example.stickerhelper.data.EagleSource
import com.example.stickerhelper.data.Folder
import com.example.stickerhelper.data.StickerItem

/**
 * 表情选择弹窗。
 *
 * 以半透明 Dialog 形式展示一个表情网格。
 * 用户点击某个表情 → 立即分享到 QQ。
 *
 * v0.2：数据来自 EagleSource（真实图库），支持分类/标签筛选。
 */
class StickerPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val libraryUri = LibraryConfig.getLibraryUri(this)
            ?.let { Uri.parse(it) }
        if (libraryUri == null) {
            Toast.makeText(this, "请先在主界面选择 Eagle 图库目录", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val repository = StickerRepository(EagleSource(this, libraryUri))

        setContent {
            MaterialTheme {
                StickerPickerDialog(
                    repository = repository,
                    onDismiss = { finish() },
                    onStickerClick = { sticker -> shareToQQ(sticker) }
                )
            }
        }
    }

    private fun shareToQQ(sticker: StickerItem) {
        val imageUri = Uri.parse(sticker.filePath)
        try {
            val intent = QQShareHelper.createShareToFriendIntent(this, imageUri)
            startActivity(intent)
            finish()
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "未找到 QQ，请确认已安装", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Toast.makeText(this, "尝试备用方案…", Toast.LENGTH_SHORT).show()
            try {
                val fallbackIntent = QQShareHelper.createShareIntent(this, imageUri)
                startActivity(Intent.createChooser(fallbackIntent, "发送到 QQ"))
                finish()
            } catch (e2: Exception) {
                Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "发送失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun StickerPickerDialog(
    repository: StickerRepository,
    onDismiss: () -> Unit,
    onStickerClick: (StickerItem) -> Unit,
) {
    val context = LocalContext.current

    var stickers by remember { mutableStateOf<List<StickerItem>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }

    // 筛选状态
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 初始加载：分类、标签、全部表情
    LaunchedEffect(Unit) {
        folders = repository.getFolders()
        tags = repository.getTags()
        stickers = repository.getAll()
        ready = true
    }

    // 筛选条件变化时重新过滤
    LaunchedEffect(selectedFolderId, selectedTags) {
        if (!ready) return@LaunchedEffect
        val tagList = selectedTags.takeIf { it.isNotEmpty() }?.toList()
        stickers = repository.filter(selectedFolderId, tagList)
    }

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
                    text = "选择表情 · ${repository.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 搜索框（下个迭代启用，当前禁用）
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    enabled = false,
                    placeholder = { Text("搜索（下个迭代开放）") },
                    singleLine = true,
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

                if (!ready) {
                    Text(
                        text = "加载中…",
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
                                onClick = { onStickerClick(sticker) }
                            )
                        }
                    }
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
private fun StickerCell(
    sticker: StickerItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
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
