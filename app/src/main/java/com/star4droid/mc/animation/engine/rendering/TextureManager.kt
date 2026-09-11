package com.star4droid.mc.animation.engine.rendering

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import com.star4droid.mc.animation.assets.BuiltInAssets

class TextureManager {

    private val textureCache = mutableMapOf<String, Int>()

    fun getTexture(textureId: String): Int {
        return textureCache.getOrPut(textureId) {
            createTextureForId(textureId)
        }
    }

    private fun createTextureForId(textureId: String): Int {
        val def = BuiltInAssets.getTextureDef(textureId)
        val bitmap = BuiltInAssets.createProceduralBlockBitmap(def)
        return uploadBitmap(bitmap)
    }

    fun uploadBitmap(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

            // Iconic Minecraft NEAREST filtering for crisp pixel-art!
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }
        return textureHandle[0]
    }

    fun registerCustomTexture(id: String, bitmap: Bitmap) {
        val handle = uploadBitmap(bitmap)
        textureCache[id] = handle
    }

    fun clear() {
        for (handle in textureCache.values) {
            GLES20.glDeleteTextures(1, intArrayOf(handle), 0)
        }
        textureCache.clear()
    }
}
