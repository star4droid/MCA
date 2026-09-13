package com.star4droid.mc.animation.utils

import android.graphics.BitmapFactory
import com.star4droid.mc.animation.assets.BuiltInAssets
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

data class ObjTriangle(
    val v1: Vec3, val v2: Vec3, val v3: Vec3,
    val n1: Vec3 = Vec3(0f, 1f, 0f), val n2: Vec3 = Vec3(0f, 1f, 0f), val n3: Vec3 = Vec3(0f, 1f, 0f),
    val uv1: Pair<Float, Float> = Pair(0f, 0f), val uv2: Pair<Float, Float> = Pair(1f, 0f), val uv3: Pair<Float, Float> = Pair(0f, 1f)
)

data class ObjModelData(
    val vertices: List<Vec3>,
    val normals: List<Vec3>,
    val uvs: List<Pair<Float, Float>>,
    val triangles: List<ObjTriangle> = emptyList(),
    val materialName: String? = null,
    val texturePath: String? = null
)

object ObjImporter {

    fun parseObjFile(file: File): SceneNode {
        return parseObjFile(file.inputStream(), file.nameWithoutExtension, file.parentFile)
    }

    fun parseObjFile(inputStream: InputStream, defaultName: String = "Imported OBJ Model", parentFolder: File? = null): SceneNode {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val rawVertices = mutableListOf<Vec3>()
        val normals = mutableListOf<Vec3>()
        val uvs = mutableListOf<Pair<Float, Float>>()
        val triangles = mutableListOf<ObjTriangle>()
        var textureAssetId = "stone"

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        var mtlFileName: String? = null

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.startsWith("#") || l.isEmpty()) continue

            val tokens = l.split("\\s+".toRegex())
            if (tokens.isEmpty()) continue

            when (tokens[0]) {
                "mtllib" -> {
                    if (tokens.size >= 2) {
                        mtlFileName = tokens[1]
                    }
                }
                "v" -> {
                    if (tokens.size >= 4) {
                        val x = tokens[1].toFloatOrNull() ?: 0f
                        val y = tokens[2].toFloatOrNull() ?: 0f
                        val z = tokens[3].toFloatOrNull() ?: 0f
                        rawVertices.add(Vec3(x, y, z))
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
                        uvs.add(Pair(u, 1f - v)) // Flip V for OpenGL coordinate space
                    }
                }
                "f" -> {
                    if (tokens.size >= 4) {
                        val faceIndices = mutableListOf<Triple<Int, Int?, Int?>>()
                        for (i in 1 until tokens.size) {
                            val parts = tokens[i].split("/")
                            val vIdx = parts[0].toIntOrNull()?.let { if (it < 0) rawVertices.size + it else it - 1 } ?: continue
                            val vtIdx = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { if (it < 0) uvs.size + it else it - 1 }
                            val vnIdx = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()?.let { if (it < 0) normals.size + it else it - 1 }
                            faceIndices.add(Triple(vIdx, vtIdx, vnIdx))
                        }

                        // Triangulate quads and n-gons using triangle fan
                        for (i in 1 until faceIndices.size - 1) {
                            val f0 = faceIndices[0]
                            val f1 = faceIndices[i]
                            val f2 = faceIndices[i + 1]

                            val rawV1 = rawVertices.getOrElse(f0.first) { Vec3.ZERO }
                            val rawV2 = rawVertices.getOrElse(f1.first) { Vec3.ZERO }
                            val rawV3 = rawVertices.getOrElse(f2.first) { Vec3.ZERO }

                            val n1 = f0.third?.let { normals.getOrNull(it) } ?: Vec3(0f, 1f, 0f)
                            val n2 = f1.third?.let { normals.getOrNull(it) } ?: Vec3(0f, 1f, 0f)
                            val n3 = f2.third?.let { normals.getOrNull(it) } ?: Vec3(0f, 1f, 0f)

                            val uv1 = f0.second?.let { uvs.getOrNull(it) } ?: Pair(0f, 0f)
                            val uv2 = f1.second?.let { uvs.getOrNull(it) } ?: Pair(1f, 0f)
                            val uv3 = f2.second?.let { uvs.getOrNull(it) } ?: Pair(0f, 1f)

                            triangles.add(ObjTriangle(rawV1, rawV2, rawV3, n1, n2, n3, uv1, uv2, uv3))
                        }
                    }
                }
            }
        }

        val centerX = if (maxX >= minX) (minX + maxX) * 0.5f else 0f
        val centerY = if (maxY >= minY) (minY + maxY) * 0.5f else 0.5f
        val centerZ = if (maxZ >= minZ) (minZ + maxZ) * 0.5f else 0f

        val spanX = if (maxX >= minX) (maxX - minX).coerceAtLeast(0.2f) else 1.0f
        val spanY = if (maxY >= minY) (maxY - minY).coerceAtLeast(0.2f) else 1.0f
        val spanZ = if (maxZ >= minZ) (maxZ - minZ).coerceAtLeast(0.2f) else 1.0f

        // Center vertices around local origin (0,0,0)
        val centerVec = Vec3(centerX, centerY, centerZ)
        val centeredTriangles = triangles.map { t ->
            t.copy(
                v1 = t.v1 - centerVec,
                v2 = t.v2 - centerVec,
                v3 = t.v3 - centerVec
            )
        }

        // Texture discovery
        if (parentFolder != null) {
            val imgExtensions = listOf("png", "jpg", "jpeg", "webp")
            var textureFile: File? = null

            if (mtlFileName != null) {
                val mtlFile = File(parentFolder, mtlFileName)
                if (mtlFile.exists()) {
                    val texNameFromMtl = parseMtlFile(mtlFile.inputStream())
                    if (texNameFromMtl != null) {
                        textureFile = File(parentFolder, texNameFromMtl)
                    }
                }
            }

            if (textureFile == null || !textureFile.exists()) {
                textureFile = parentFolder.listFiles()?.firstOrNull {
                    it.nameWithoutExtension.equals(defaultName, ignoreCase = true) && it.extension.lowercase() in imgExtensions
                }
            }

            if (textureFile != null && textureFile.exists()) {
                val bitmap = try { BitmapFactory.decodeFile(textureFile.path) } catch (e: Exception) { null }
                if (bitmap != null) {
                    textureAssetId = textureFile.nameWithoutExtension
                    BuiltInAssets.registerCustomTexture(textureAssetId, bitmap)
                }
            }
        }

        val objData = ObjModelData(rawVertices, normals, uvs, centeredTriangles)

        return SceneNode(
            id = UUID.randomUUID().toString(),
            name = defaultName,
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(centerX, centerY, centerZ)),
            animatedTransform = Transform(position = Vec3(centerX, centerY, centerZ)),
            material = Material(textureAssetId = textureAssetId),
            boxDimensions = Vec3(spanX, spanY, spanZ),
            objModelData = objData
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
