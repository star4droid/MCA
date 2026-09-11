package com.star4droid.mc.animation.assets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.UUID

enum class AssetCategory {
    TEXTURE,
    IMAGE,
    SOUND,
    MODEL,
    CHARACTER,
    TIMELINE
}

data class ProjectAsset(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val category: AssetCategory,
    var relativePath: String = "",
    val isBuiltIn: Boolean = false,
    val fileSizeBytes: Long = 0L
)

data class BlockTextureDef(
    val id: String,
    val displayName: String,
    val topColor: Int,
    val sideColor: Int,
    val bottomColor: Int
)

object BuiltInAssets {

    val BLOCK_TEXTURES = listOf(
        BlockTextureDef("grass", "Grass Block", 0xFF5C9A35.toInt(), 0xFF7D5A38.toInt(), 0xFF866043.toInt()),
        BlockTextureDef("dirt", "Dirt", 0xFF866043.toInt(), 0xFF866043.toInt(), 0xFF866043.toInt()),
        BlockTextureDef("stone", "Stone", 0xFF7F7F7F.toInt(), 0xFF7F7F7F.toInt(), 0xFF7F7F7F.toInt()),
        BlockTextureDef("cobblestone", "Cobblestone", 0xFF696969.toInt(), 0xFF696969.toInt(), 0xFF696969.toInt()),
        BlockTextureDef("oak_planks", "Oak Planks", 0xFFB8945F.toInt(), 0xFFB8945F.toInt(), 0xFFB8945F.toInt()),
        BlockTextureDef("brick", "Bricks", 0xFF9C493B.toInt(), 0xFF9C493B.toInt(), 0xFF9C493B.toInt()),
        BlockTextureDef("diamond", "Diamond Block", 0xFF4AEDD9.toInt(), 0xFF4AEDD9.toInt(), 0xFF4AEDD9.toInt()),
        BlockTextureDef("gold_block", "Gold Block", 0xFFFBE44D.toInt(), 0xFFFBE44D.toInt(), 0xFFFBE44D.toInt()),
        BlockTextureDef("iron_block", "Iron Block", 0xFFD8D8D8.toInt(), 0xFFD8D8D8.toInt(), 0xFFD8D8D8.toInt()),
        BlockTextureDef("crafting_table", "Crafting Table", 0xFF9A6E3F.toInt(), 0xFF795228.toInt(), 0xFFB8945F.toInt()),
        BlockTextureDef("tnt", "TNT", 0xFFD43525.toInt(), 0xFFD43525.toInt(), 0xFFD43525.toInt()),
        BlockTextureDef("bookshelf", "Bookshelf", 0xFFB8945F.toInt(), 0xFF6C4728.toInt(), 0xFFB8945F.toInt()),
        BlockTextureDef("obsidian", "Obsidian", 0xFF191028.toInt(), 0xFF191028.toInt(), 0xFF191028.toInt()),
        BlockTextureDef("sand", "Sand", 0xFFE0D890.toInt(), 0xFFE0D890.toInt(), 0xFFE0D890.toInt()),
        BlockTextureDef("glass", "Glass", 0x88C0E8F8.toInt(), 0x88C0E8F8.toInt(), 0x88C0E8F8.toInt())
    )

    fun getTextureDef(id: String): BlockTextureDef {
        return BLOCK_TEXTURES.firstOrNull { it.id == id } ?: BLOCK_TEXTURES.first()
    }

    /**
     * Generates a 16x16 pixel-art Minecraft bitmap texture with natural noise.
     */
    fun createProceduralBlockBitmap(def: BlockTextureDef): Bitmap {
        val size = 16
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val baseColor = def.sideColor
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        for (x in 0 until size) {
            for (y in 0 until size) {
                // Pixelated noise variation
                val noise = ((x * 17 + y * 23 + (x xor y) * 7) % 31) - 15
                val pr = (r + noise).coerceIn(0, 255)
                val pg = (g + noise).coerceIn(0, 255)
                val pb = (b + noise).coerceIn(0, 255)

                // Top grass trim
                val isGrassTop = def.id == "grass" && y < 4
                val pixelColor = if (isGrassTop) {
                    val gr = (Color.red(def.topColor) + noise).coerceIn(0, 255)
                    val gg = (Color.green(def.topColor) + noise).coerceIn(0, 255)
                    val gb = (Color.blue(def.topColor) + noise).coerceIn(0, 255)
                    Color.rgb(gr, gg, gb)
                } else {
                    Color.rgb(pr, pg, pb)
                }

                paint.color = pixelColor
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }
        return bitmap
    }
}
