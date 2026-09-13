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

        val rawSpanX = if (maxX >= minX) (maxX - minX).coerceAtLeast(0.01f) else 1.0f
        val rawSpanY = if (maxY >= minY) (maxY - minY).coerceAtLeast(0.01f) else 1.0f
        val rawSpanZ = if (maxZ >= minZ) (maxZ - minZ).coerceAtLeast(0.01f) else 1.0f

        // Auto-scale large models down to standard scene scale (keeping exact aspect ratio)
        val maxSpan = maxOf(rawSpanX, rawSpanY, rawSpanZ)
        val targetMax = 2.0f // Standard character/object height in scene (2 blocks)
        val autoScale = if (maxSpan > targetMax) (targetMax / maxSpan) else 1.0f

        val finalSpanX = rawSpanX * autoScale
        val finalSpanY = rawSpanY * autoScale
        val finalSpanZ = rawSpanZ * autoScale

        // Center vertices around local origin (0,0,0) and apply autoScale
        val centerVec = Vec3(centerX, centerY, centerZ)
        val centeredTriangles = triangles.map { t ->
            t.copy(
                v1 = (t.v1 - centerVec) * autoScale,
                v2 = (t.v2 - centerVec) * autoScale,
                v3 = (t.v3 - centerVec) * autoScale
            )
        }

        // Texture and Material discovery
        if (parentFolder != null) {
            val imgExtensions = listOf("png", "jpg", "jpeg", "webp")
            var textureFile: File? = null

            // 1. Check MTL specified in obj file or fallback to <defaultName>.mtl
            val mtlCandidates = mutableListOf<File>()
            if (!mtlFileName.isNullOrBlank()) {
                mtlCandidates.add(File(parentFolder, mtlFileName!!))
            }
            mtlCandidates.add(File(parentFolder, "$defaultName.mtl"))
            val candidateMtl = mtlCandidates.firstOrNull { it.exists() }

            if (candidateMtl != null) {
                try {
                    val mtlTextures = parseMtlFile(candidateMtl.inputStream())
                    for (texName in mtlTextures) {
                        // Check direct parent folder, subfolders textures/, images/, etc.
                        val direct = File(parentFolder, texName)
                        if (direct.exists()) {
                            textureFile = direct
                            break
                        }
                        val inTexturesSub = File(parentFolder, "textures/$texName")
                        if (inTexturesSub.exists()) {
                            textureFile = inTexturesSub
                            break
                        }
                        val inImagesSub = File(parentFolder, "images/$texName")
                        if (inImagesSub.exists()) {
                            textureFile = inImagesSub
                            break
                        }
                        // Case-insensitive match in folder
                        val caseMatch = parentFolder.listFiles()?.firstOrNull { it.name.equals(texName, ignoreCase = true) }
                        if (caseMatch != null) {
                            textureFile = caseMatch
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. If not found via MTL, check if an image with the model's name exists
            if (textureFile == null || !textureFile.exists()) {
                textureFile = parentFolder.listFiles()?.firstOrNull {
                    it.nameWithoutExtension.equals(defaultName, ignoreCase = true) && it.extension.lowercase() in imgExtensions
                }
            }

            // 3. Fallback: check any image inside parent folder
            if (textureFile == null || !textureFile.exists()) {
                textureFile = parentFolder.listFiles()?.firstOrNull {
                    it.isFile && it.extension.lowercase() in imgExtensions
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

        // Spawn position: placed nicely on ground at center
        val spawnPos = Vec3(0f, finalSpanY * 0.5f, 0f)

        return SceneNode(
            id = UUID.randomUUID().toString(),
            name = defaultName,
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = spawnPos),
            animatedTransform = Transform(position = spawnPos),
            material = Material(textureAssetId = textureAssetId),
            boxDimensions = Vec3(finalSpanX, finalSpanY, finalSpanZ),
            objModelData = objData
        )
    }

    fun parseMtlFile(inputStream: InputStream): List<String> {
        val textures = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line?.trim() ?: continue
            if (l.startsWith("map_Kd", ignoreCase = true) ||
                l.startsWith("map_Ka", ignoreCase = true) ||
                l.startsWith("map_bump", ignoreCase = true) ||
                l.startsWith("bump", ignoreCase = true)) {
                val tokens = l.split("\\s+".toRegex())
                val imgToken = tokens.findLast {
                    it.contains(".png", ignoreCase = true) ||
                    it.contains(".jpg", ignoreCase = true) ||
                    it.contains(".jpeg", ignoreCase = true) ||
                    it.contains(".webp", ignoreCase = true)
                } ?: tokens.lastOrNull()?.takeIf { it != tokens[0] }
                if (imgToken != null) {
                    val clean = File(imgToken.replace('\\', '/')).name.removeSurrounding("\"").trim()
                    if (clean.isNotEmpty() && !textures.contains(clean)) {
                        textures.add(clean)
                    }
                }
            }
        }
        return textures
    }
}
