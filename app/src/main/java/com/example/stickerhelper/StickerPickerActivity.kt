package com.example.stickerhelper

import android.content.Intent
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

/**
 * 表情选择弹窗。
 *
 * 以半透明 Dialog 形式展示一个表情网格。
 * 用户点击某个表情 → 立即分享到 QQ。
 */
class StickerPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                StickerPickerDialog(
                    onDismiss = { finish() },
                    onStickerClick = { sticker -> shareToQQ(sticker) }
                )
            }
        }
    }

    private fun shareToQQ(sticker: StickerRepository.Sticker) {
        try {
            val intent = QQShareHelper.createShareToFriendIntent(this, sticker.file)
            startActivity(intent)
            finish()
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "未找到 QQ，请确认已安装", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            // 精确跳转失败 → fallback 到包名方式（QQ 内部可选面板）
            Toast.makeText(this, "尝试备用方案…", Toast.LENGTH_SHORT).show()
            try {
                val fallbackIntent = QQShareHelper.createShareIntent(this, sticker.file)
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
    onDismiss: () -> Unit,
    onStickerClick: (StickerRepository.Sticker) -> Unit,
) {
    val context = LocalContext.current
    var stickers by remember { mutableStateOf<List<StickerRepository.Sticker>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }

    // 异步加载表情（在 Compose 启动时一次性生成）
    LaunchedEffect(Unit) {
        stickers = StickerRepository.ensureStickers(context)
        ready = true
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
                    text = "选择表情",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

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
private fun StickerCell(
    sticker: StickerRepository.Sticker,
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
            painter = rememberAsyncImagePainter(sticker.file),
            contentDescription = sticker.label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
