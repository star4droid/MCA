package com.star4droid.mc.animation.utils

import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.Material
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.Transform
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

data class ObjModelData(
    val vertices: List<Vec3>,
    val normals: List<Vec3>,
    val uvs: List<Pair<Float, Float>>,
    val materialName: String? = null,
    val texturePath: String? = null
)

object ObjImporter {

    fun parseObjFile(inputStream: InputStream): SceneNode {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val vertices = mutableListOf<Vec3>()
        val normals = mutableListOf<Vec3>()
        val uvs = mutableListOf<Pair<Float, Float>>()

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.startsWith("#") || l.isEmpty()) continue

            val tokens = l.split("\\s+".toRegex())
            when (tokens[0]) {
                "v" -> {
                    if (tokens.size >= 4) {
                        val x = tokens[1].toFloatOrNull() ?: 0f
                        val y = tokens[2].toFloatOrNull() ?: 0f
                        val z = tokens[3].toFloatOrNull() ?: 0f
                        vertices.add(Vec3(x, y, z))
                        if (x < minX) minX = x; if (x > maxX) maxX = x
                        if (y < minY) minY = y; if (y > maxY) maxY = y
                        if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
                    }
                }
                "vn" -> {
                    if (tokens.size >= 4) {
                        val x = tokens[1].toFloatOrNull() ?: 0f
                        val y = tokens[2].toFloatOrNull() ?: 0f
                        val z = tokens[3].toFloatOrNull() ?: 0f
                        normals.add(Vec3(x, y, z))
                    }
                }
                "vt" -> {
                    if (tokens.size >= 3) {
                        val u = tokens[1].toFloatOrNull() ?: 0f
                        val v = tokens[2].toFloatOrNull() ?: 0f
                        uvs.add(Pair(u, v))
                    }
                }
            }
        }

        val spanX = if (maxX >= minX) (maxX - minX).coerceAtLeast(0.5f) else 1.0f
        val spanY = if (maxY >= minY) (maxY - minY).coerceAtLeast(0.5f) else 1.0f
        val spanZ = if (maxZ >= minZ) (maxZ - minZ).coerceAtLeast(0.5f) else 1.0f

        val centerX = if (maxX >= minX) (minX + maxX) * 0.5f else 0f
        val centerY = if (maxY >= minY) (minY + maxY) * 0.5f else 0.5f
        val centerZ = if (maxZ >= minZ) (minZ + maxZ) * 0.5f else 0f

        return SceneNode(
            id = UUID.randomUUID().toString(),
            name = "Imported OBJ Model",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(centerX, centerY, centerZ)),
            animatedTransform = Transform(position = Vec3(centerX, centerY, centerZ)),
            material = Material(textureAssetId = "stone"),
            boxDimensions = Vec3(spanX, spanY, spanZ)
        )
    }

    fun parseMtlFile(inputStream: InputStream): String? {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.startsWith("map_Kd")) {
                val parts = l.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    return parts[1]
                }
            }
        }
        return null
    }
}
