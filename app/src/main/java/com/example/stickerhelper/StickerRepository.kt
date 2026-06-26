package com.example.stickerhelper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

/**
 * 本地表情数据源（原型阶段：生成占位表情图片）
 *
 * 后续阶段会替换为读取 Eagle 图库的 SQLite 数据库 + 图片文件。
 */
object StickerRepository {

    private const val STICKER_DIR = "stickers"
    private const val STICKER_SIZE = 200 // px
    private const val STICKER_COUNT = 8

    /** 占位表情配置：emoji + 背景色 */
    private val PLACEHOLDER_EMOJIS = listOf(
        Pair("😀", 0xFFE91E63.toInt()),
        Pair("😂", 0xFF2196F3.toInt()),
        Pair("😍", 0xFF4CAF50.toInt()),
        Pair("🤔", 0xFFFF9800.toInt()),
        Pair("😭", 0xFF9C27B0.toInt()),
        Pair("🥺", 0xFF00BCD4.toInt()),
        Pair("🔥", 0xFFFF5722.toInt()),
        Pair("💩", 0xFF795548.toInt()),
    )

    data class Sticker(
        val id: Int,
        val file: File,
        val label: String,
    )

    /** 确保占位表情已生成，返回表情列表 */
    fun ensureStickers(context: Context): List<Sticker> {
        val dir = getStickerDir(context)
        if (!dir.exists()) dir.mkdirs()

        val existing = dir.listFiles()
            ?.filter { it.extension == "png" }
            ?.sortedBy { it.name }

        // 如果已经有足够的表情文件，直接返回
        if (existing != null && existing.size >= STICKER_COUNT) {
            return existing.mapIndexed { index, file ->
                Sticker(
                    id = index,
                    file = file,
                    label = PLACEHOLDER_EMOJIS.getOrElse(index) { Pair("?", Color.GRAY) }.first,
                )
            }
        }

        // 否则重新生成
        dir.listFiles()?.forEach { it.delete() }
        val stickers = mutableListOf<Sticker>()

        PLACEHOLDER_EMOJIS.forEachIndexed { index, (emoji, bgColor) ->
            val file = File(dir, "sticker_${index}.png")
            generateStickerImage(file, emoji, bgColor)
            stickers.add(Sticker(id = index, file = file, label = emoji))
        }

        return stickers
    }

    /** 获取表情存储目录 */
    private fun getStickerDir(context: Context): File {
        // 优先外部存储，失败则回退内部存储
        return context.getExternalFilesDir(STICKER_DIR)
            ?: File(context.filesDir, STICKER_DIR)
    }

    /** 生成一张带 emoji 的纯色方形占位图 */
    private fun generateStickerImage(file: File, emoji: String, bgColor: Int) {
        val bitmap = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 画圆角矩形背景
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
        }
        val rect = RectF(0f, 0f, STICKER_SIZE.toFloat(), STICKER_SIZE.toFloat())
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        // 画 emoji 文字
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }
        val x = STICKER_SIZE / 2f
        val y = STICKER_SIZE / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(emoji, x, y, textPaint)

        // 写入文件
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()
    }
}
