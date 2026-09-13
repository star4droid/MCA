package com.star4droid.mc.animation.ui.blocks

import java.io.File

data class CustomBlockPreset(
    val id: String,
    val name: String,
    val description: String,
    val category: String, // "CHARACTER" or "BLOCK"
    val duration: Float,
    val jsonContent: String,
    val file: File? = null
)
