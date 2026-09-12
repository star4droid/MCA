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
     * Generates a crisp, authentic 16x16 pixel-art Minecraft bitmap texture.
     * Guarantees 100% opacity (no unintended semi-transparency).
     */
    fun createProceduralBlockBitmap(def: BlockTextureDef): Bitmap {
        val size = 16
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)

        fun setPx(x: Int, y: Int, color: Int) {
            if (x in 0 until size && y in 0 until size) {
                pixels[y * size + x] = color
            }
        }

        fun rgb(r: Int, g: Int, b: Int, a: Int = 255): Int {
            return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }

        when (def.id) {
            "grass" -> {
                // Minecraft Grass side: Green grass overhang with dirt underneath
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val overhang = 3 + ((x * 7 + 3) % 4) // irregular grass fringe y: 3..5
                        if (y < overhang) {
                            val noise = ((x * 13 + y * 19) % 25) - 12
                            val col = rgb(92 + noise, 154 + noise, 53 + noise)
                            setPx(x, y, col)
                        } else {
                            val noise = ((x * 17 + y * 23) % 31) - 15
                            val col = rgb(134 + noise, 96 + noise, 67 + noise)
                            setPx(x, y, col)
                        }
                    }
                }
            }
            "dirt" -> {
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val noise = ((x * 17 + y * 29 + (x xor y) * 11) % 35) - 17
                        val col = rgb(134 + noise, 96 + noise, 67 + noise)
                        setPx(x, y, col)
                    }
                }
            }
            "stone" -> {
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val noise = ((x * 19 + y * 31 + (x * y) * 7) % 29) - 14
                        val col = rgb(127 + noise, 127 + noise, 127 + noise)
                        setPx(x, y, col)
                    }
                }
            }
            "cobblestone" -> {
                // Interlocking stone shapes with darker mortar crevices
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val isMortar = (x % 5 == 0 && y % 4 != 2) || (y % 4 == 0) || ((x + y) % 7 == 0 && (x % 3 == 0))
                        if (isMortar) {
                            setPx(x, y, rgb(70, 70, 70))
                        } else {
                            val noise = ((x * 11 + y * 17) % 25) - 12
                            setPx(x, y, rgb(118 + noise, 118 + noise, 118 + noise))
                        }
                    }
                }
            }
            "oak_planks" -> {
                // 4 horizontal wood planks separated by seam lines
                for (y in 0 until size) {
                    val isSeam = (y == 3 || y == 7 || y == 11 || y == 15)
                    for (x in 0 until size) {
                        if (isSeam) {
                            setPx(x, y, rgb(130, 95, 52))
                        } else {
                            val grain = ((x * 7 + (y % 4) * 19) % 21) - 10
                            setPx(x, y, rgb(184 + grain, 148 + grain, 95 + grain))
                        }
                    }
                }
            }
            "brick" -> {
                // Running bond brick pattern with cement lines
                for (y in 0 until size) {
                    val isHorizMortar = (y % 4 == 0)
                    for (x in 0 until size) {
                        val row = y / 4
                        val xOffset = if (row % 2 == 1) 4 else 0
                        val isVertMortar = ((x + xOffset) % 8 == 0)
                        if (isHorizMortar || isVertMortar) {
                            setPx(x, y, rgb(190, 180, 168)) // light mortar
                        } else {
                            val noise = ((x * 13 + y * 17) % 21) - 10
                            setPx(x, y, rgb(156 + noise, 73 + noise, 59 + noise))
                        }
                    }
                }
            }
            "diamond" -> {
                // Cyan jewel block with beveled border and sparkle
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val isBorder = (x == 0 || x == 15 || y == 0 || y == 15)
                        val isInnerBorder = (x == 1 || x == 14 || y == 1 || y == 14)
                        if (isBorder) {
                            setPx(x, y, rgb(50, 168, 152))
                        } else if (isInnerBorder) {
                            setPx(x, y, rgb(74, 210, 195))
                        } else {
                            val sparkle = if ((x in 4..6 && y in 4..6) || (x in 9..11 && y in 9..11)) 35 else 0
                            setPx(x, y, rgb(74 + sparkle, 237 + sparkle / 2, 217 + sparkle / 3))
                        }
                    }
                }
            }
            "gold_block" -> {
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val isBorder = (x == 0 || x == 15 || y == 0 || y == 15)
                        if (isBorder) {
                            setPx(x, y, rgb(215, 165, 30))
                        } else {
                            val noise = ((x * 11 + y * 13) % 21) - 10
                            val highlight = if (x in 3..6 && y in 3..6) 20 else 0
                            setPx(x, y, rgb(245 + noise + highlight, 215 + noise + highlight, 70 + noise))
                        }
                    }
                }
            }
            "iron_block" -> {
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val isBorder = (x == 0 || x == 15 || y == 0 || y == 15)
                        if (isBorder) {
                            setPx(x, y, rgb(175, 175, 175))
                        } else {
                            val noise = ((x * 17 + y * 19) % 17) - 8
                            setPx(x, y, rgb(216 + noise, 216 + noise, 216 + noise))
                        }
                    }
                }
            }
            "tnt" -> {
                // Red dynamite with white stripe and TNT letters
                for (y in 0 until size) {
                    val isStripe = (y in 6..9)
                    for (x in 0 until size) {
                        if (isStripe) {
                            // White stripe with dark "TNT" letters
                            val isT1 = (x in 2..4 && y == 6) || (x == 3 && y in 7..9)
                            val isN = (x == 6 || x == 9) && y in 6..9 || (x == 7 && y == 7) || (x == 8 && y == 8)
                            val isT2 = (x in 11..13 && y == 6) || (x == 12 && y in 7..9)
                            if (isT1 || isN || isT2) {
                                setPx(x, y, rgb(20, 20, 20))
                            } else {
                                setPx(x, y, rgb(245, 245, 245))
                            }
                        } else {
                            val noise = ((x * 13 + y * 17) % 21) - 10
                            setPx(x, y, rgb(212 + noise, 53 + noise, 37 + noise))
                        }
                    }
                }
            }
            "bookshelf" -> {
                // Oak frame with colorful book spines
                for (y in 0 until size) {
                    val isShelf = (y == 0 || y == 7 || y == 15)
                    for (x in 0 until size) {
                        val isFrame = isShelf || (x == 0 || x == 15)
                        if (isFrame) {
                            setPx(x, y, rgb(184, 148, 95))
                        } else {
                            // Book spines
                            val bookColors = listOf(
                                rgb(160, 40, 40),   // red
                                rgb(40, 90, 160),   // blue
                                rgb(40, 130, 60),   // green
                                rgb(180, 140, 40),  // gold
                                rgb(100, 60, 130)   // purple
                            )
                            val bookIdx = (x / 3 + (y / 8) * 3) % bookColors.size
                            val isTopEdge = (y == 1 || y == 8)
                            if (isTopEdge) {
                                setPx(x, y, rgb(80, 50, 30))
                            } else {
                                setPx(x, y, bookColors[bookIdx])
                            }
                        }
                    }
                }
            }
            "crafting_table" -> {
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val isBorder = (x == 0 || x == 15 || y == 0 || y == 15)
                        if (isBorder) {
                            setPx(x, y, rgb(120, 80, 40))
                        } else {
                            val isCross = (x == 7 || x == 8 || y == 7 || y == 8)
                            if (isCross) {
                                setPx(x, y, rgb(140, 95, 50))
                            } else {
                                val noise = ((x * 11 + y * 13) % 17) - 8
                                setPx(x, y, rgb(180 + noise, 130 + noise, 75 + noise))
                            }
                        }
                    }
                }
            }
            "obsidian" -> {
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val noise = ((x * 23 + y * 29) % 31)
                        val isCrystal = (noise > 27)
                        if (isCrystal) {
                            setPx(x, y, rgb(95, 45, 125)) // purple sheen
                        } else {
                            val v = 25 + (noise % 15)
                            setPx(x, y, rgb(v, v / 2, v * 2))
                        }
                    }
                }
            }
            "sand" -> {
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val noise = ((x * 13 + y * 19) % 21) - 10
                        setPx(x, y, rgb(224 + noise, 216 + noise, 144 + noise))
                    }
                }
            }
            "glass" -> {
                // Glass border with subtle diagonal specular reflections
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val isBorder = (x == 0 || x == 15 || y == 0 || y == 15)
                        val isStreak1 = (x - y == 2 && x in 3..6)
                        val isStreak2 = (x - y == -4 && x in 8..11)
                        if (isBorder) {
                            setPx(x, y, rgb(210, 230, 245, 255))
                        } else if (isStreak1 || isStreak2) {
                            setPx(x, y, rgb(240, 250, 255, 220))
                        } else {
                            setPx(x, y, rgb(180, 220, 245, 50)) // Semi-transparent body
                        }
                    }
                }
            }
            else -> {
                val base = def.sideColor
                val r = Color.red(base)
                val g = Color.green(base)
                val b = Color.blue(base)
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val noise = ((x * 17 + y * 23) % 25) - 12
                        setPx(x, y, rgb(r + noise, g + noise, b + noise))
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Generates an authentic procedural Minecraft character texture.
     * Head parts produce a 32x16 atlas matching Minecraft's UV mapping
     * so that the face is ONLY on the front, hair on top/back/sides, and neck on bottom.
     */
    fun createProceduralCharacterBitmap(partId: String): Bitmap {
        val isHead = partId.contains("_head")
        val width = if (isHead) 32 else 16
        val height = 16
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        fun setPx(x: Int, y: Int, color: Int) {
            if (x in 0 until width && y in 0 until height) {
                pixels[y * width + x] = color
            }
        }

        fun rgb(r: Int, g: Int, b: Int): Int {
            return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }

        // Color palettes
        val steveHair = rgb(74, 50, 30)
        val steveSkin = rgb(217, 160, 116)
        val steveSkinDark = rgb(186, 131, 88)
        val steveEyeBlue = rgb(43, 53, 120)
        val steveEyeWhite = rgb(250, 250, 250)
        val steveBeard = rgb(91, 55, 33)
        val steveShirt = rgb(0, 168, 168)
        val steveShirtDark = rgb(0, 136, 136)
        val steveJeans = rgb(44, 62, 138)

        val alexHair = rgb(180, 86, 36)
        val alexSkin = rgb(232, 185, 157)
        val alexEyeGreen = rgb(46, 117, 52)
        val alexTunic = rgb(94, 120, 65)
        val alexBelt = rgb(85, 56, 32)
        val alexPants = rgb(70, 53, 39)
        val alexBoots = rgb(43, 30, 20)

        when {
            partId.contains("steve_head") -> {
                // 1. Top of head hair: (x: 8..15, y: 0..7)
                for (y in 0..7) {
                    for (x in 8..15) {
                        val noise = ((x * 7 + y * 13) % 15) - 7
                        setPx(x, y, rgb(74 + noise, 50 + noise, 30 + noise))
                    }
                }
                // 2. Bottom of head / neck: (x: 16..23, y: 0..7)
                for (y in 0..7) {
                    for (x in 16..23) {
                        val noise = ((x * 11 + y * 13) % 11) - 5
                        setPx(x, y, rgb(217 + noise, 160 + noise, 116 + noise))
                    }
                }
                // 3. Right of head hair: (x: 0..7, y: 8..15)
                for (y in 8..15) {
                    for (x in 0..7) {
                        if (y in 12..13 && x in 3..4) {
                            setPx(x, y, steveSkin) // ear
                        } else {
                            val noise = ((x * 7 + y * 13) % 15) - 7
                            setPx(x, y, rgb(74 + noise, 50 + noise, 30 + noise))
                        }
                    }
                }
                // 4. Front Face: (x: 8..15, y: 8..15)
                for (y in 8..15) {
                    for (x in 8..15) {
                        val localX = x - 8
                        val localY = y - 8
                        when {
                            // Hair fringe on forehead
                            localY in 0..1 || (localY == 2 && (localX in 0..1 || localX in 6..7)) -> {
                                val noise = ((x * 7 + y * 13) % 15) - 7
                                setPx(x, y, rgb(74 + noise, 50 + noise, 30 + noise))
                            }
                            // Eyes
                            localY == 3 && (localX == 1 || localX == 6) -> setPx(x, y, steveEyeWhite)
                            localY == 3 && (localX == 2 || localX == 5) -> setPx(x, y, steveEyeBlue)
                            // Nose
                            localY == 4 && localX in 3..4 -> setPx(x, y, steveSkinDark)
                            // Mouth & Beard
                            localY == 5 && localX in 3..4 -> setPx(x, y, rgb(140, 70, 60))
                            localY in 5..7 && (localX in 2..5 || localY == 7) -> setPx(x, y, steveBeard)
                            // Face skin
                            else -> {
                                val noise = ((x * 11 + y * 17) % 13) - 6
                                setPx(x, y, rgb(217 + noise, 160 + noise, 116 + noise))
                            }
                        }
                    }
                }
                // 5. Left of head hair: (x: 16..23, y: 8..15)
                for (y in 8..15) {
                    for (x in 16..23) {
                        if (y in 12..13 && x in 19..20) {
                            setPx(x, y, steveSkin) // ear
                        } else {
                            val noise = ((x * 7 + y * 13) % 15) - 7
                            setPx(x, y, rgb(74 + noise, 50 + noise, 30 + noise))
                        }
                    }
                }
                // 6. Back of head hair: (x: 24..31, y: 8..15)
                for (y in 8..15) {
                    for (x in 24..31) {
                        val noise = ((x * 7 + y * 13) % 15) - 7
                        setPx(x, y, rgb(74 + noise, 50 + noise, 30 + noise))
                    }
                }
            }
            partId.contains("alex_head") -> {
                // Top
                for (y in 0..7) {
                    for (x in 8..15) {
                        val noise = ((x * 7 + y * 11) % 15) - 7
                        setPx(x, y, rgb(180 + noise, 86 + noise, 36 + noise))
                    }
                }
                // Bottom
                for (y in 0..7) {
                    for (x in 16..23) {
                        setPx(x, y, alexSkin)
                    }
                }
                // Right hair
                for (y in 8..15) {
                    for (x in 0..7) {
                        val noise = ((x * 7 + y * 11) % 15) - 7
                        setPx(x, y, rgb(180 + noise, 86 + noise, 36 + noise))
                    }
                }
                // Front face
                for (y in 8..15) {
                    for (x in 8..15) {
                        val localX = x - 8
                        val localY = y - 8
                        when {
                            localY in 0..1 -> {
                                val noise = ((x * 7 + y * 11) % 15) - 7
                                setPx(x, y, rgb(180 + noise, 86 + noise, 36 + noise))
                            }
                            localY == 3 && (localX == 1 || localX == 6) -> setPx(x, y, steveEyeWhite)
                            localY == 3 && (localX == 2 || localX == 5) -> setPx(x, y, alexEyeGreen)
                            localY == 5 && localX in 3..4 -> setPx(x, y, rgb(190, 110, 100))
                            else -> {
                                val noise = ((x * 11 + y * 13) % 11) - 5
                                setPx(x, y, rgb(232 + noise, 185 + noise, 157 + noise))
                            }
                        }
                    }
                }
                // Left hair & Back hair
                for (y in 8..15) {
                    for (x in 16..31) {
                        val noise = ((x * 7 + y * 11) % 15) - 7
                        setPx(x, y, rgb(180 + noise, 86 + noise, 36 + noise))
                    }
                }
            }
            partId.contains("zombie_head") -> {
                val zSkin = rgb(86, 138, 70)
                val zHair = rgb(45, 80, 40)
                for (y in 0..7) {
                    for (x in 8..15) setPx(x, y, zHair)
                    for (x in 16..23) setPx(x, y, zSkin)
                }
                for (y in 8..15) {
                    for (x in 0..7) setPx(x, y, zHair)
                    for (x in 24..31) setPx(x, y, zHair)
                    for (x in 16..23) setPx(x, y, zHair)
                    for (x in 8..15) {
                        val localX = x - 8
                        val localY = y - 8
                        if (localY == 3 && (localX in 1..2 || localX in 5..6)) {
                            setPx(x, y, rgb(20, 20, 20))
                        } else if (localY == 5 && localX in 3..4) {
                            setPx(x, y, rgb(30, 50, 25))
                        } else {
                            val noise = ((x * 7 + y * 13) % 15) - 7
                            setPx(x, y, rgb(86 + noise, 138 + noise, 70 + noise))
                        }
                    }
                }
            }
            partId.contains("steve_body") -> {
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        val isVNeck = (y in 0..2 && x in 6..9) || (y == 3 && x in 7..8)
                        if (isVNeck) {
                            setPx(x, y, steveSkin)
                        } else if (y >= 14) {
                            setPx(x, y, steveJeans)
                        } else {
                            val noise = ((x * 13 + y * 19) % 21) - 10
                            val isSideFold = (x == 0 || x == 15 || y == 13)
                            val base = if (isSideFold) steveShirtDark else steveShirt
                            val r = Color.red(base)
                            val g = Color.green(base)
                            val b = Color.blue(base)
                            setPx(x, y, rgb(r + noise, g + noise, b + noise))
                        }
                    }
                }
            }
            partId.contains("steve_arm") -> {
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        if (y in 0..3) {
                            val noise = ((x * 7 + y * 11) % 15) - 7
                            setPx(x, y, rgb(0, 168 + noise, 168 + noise))
                        } else {
                            val noise = ((x * 11 + y * 13) % 15) - 7
                            setPx(x, y, rgb(217 + noise, 160 + noise, 116 + noise))
                        }
                    }
                }
            }
            partId.contains("steve_leg") -> {
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        if (y >= 13) {
                            val noise = ((x * 7 + y * 13) % 11) - 5
                            setPx(x, y, rgb(74 + noise, 74 + noise, 74 + noise))
                        } else {
                            val noise = ((x * 11 + y * 17) % 17) - 8
                            setPx(x, y, rgb(44 + noise, 62 + noise, 138 + noise))
                        }
                    }
                }
            }
            partId.contains("alex_body") -> {
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        val isCollar = (y in 0..1 && x in 7..8)
                        val isBelt = (y in 10..11)
                        if (isCollar) {
                            setPx(x, y, alexSkin)
                        } else if (isBelt) {
                            if (x in 7..8) setPx(x, y, rgb(215, 175, 40)) else setPx(x, y, alexBelt)
                        } else if (y >= 14) {
                            setPx(x, y, alexPants)
                        } else {
                            val noise = ((x * 7 + y * 11) % 15) - 7
                            setPx(x, y, rgb(94 + noise, 120 + noise, 65 + noise))
                        }
                    }
                }
            }
            partId.contains("alex_arm") -> {
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        if (y in 0..3) {
                            setPx(x, y, alexTunic)
                        } else {
                            setPx(x, y, alexSkin)
                        }
                    }
                }
            }
            partId.contains("alex_leg") -> {
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        if (y >= 12) setPx(x, y, alexBoots) else setPx(x, y, alexPants)
                    }
                }
            }
            else -> {
                for (y in 0 until 16) {
                    for (x in 0 until 16) {
                        setPx(x, y, steveSkin)
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    val customBitmaps = mutableMapOf<String, Bitmap>()

    fun registerCustomTexture(id: String, bitmap: Bitmap) {
        customBitmaps[id] = bitmap
    }

    fun getCustomBitmap(id: String): Bitmap? = customBitmaps[id]
}
