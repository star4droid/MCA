package com.star4droid.mc.animation.objects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.star4droid.mc.animation.assets.BuiltInAssets
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.Material
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.Transform
import com.star4droid.mc.animation.utils.ObjImporter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SavedObjectItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val objFilePath: String,
    val texturePath: String? = null,
    val colorHex: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object SavedObjectsRepository {

    private fun getStorageDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "saved_objects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getIndexFile(context: Context): File {
        return File(getStorageDir(context), "index.json")
    }

    @Synchronized
    fun getSavedObjects(context: Context): List<SavedObjectItem> {
        val indexFile = getIndexFile(context)
        if (!indexFile.exists()) {
            val defaults = createDefaultStarterObjectsDirectly(context)
            saveIndex(context, defaults)
            return defaults
        }

        return try {
            val json = JSONObject(indexFile.readText())
            val arr = json.optJSONArray("objects") ?: JSONArray()
            val list = mutableListOf<SavedObjectItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedObjectItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Object"),
                        objFilePath = obj.optString("objFilePath", ""),
                        texturePath = obj.optString("texturePath").ifEmpty { null },
                        colorHex = obj.optString("colorHex").ifEmpty { null },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    private fun saveIndex(context: Context, list: List<SavedObjectItem>) {
        try {
            val json = JSONObject()
            val arr = JSONArray()
            for (item in list) {
                arr.put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("objFilePath", item.objFilePath)
                    put("texturePath", item.texturePath ?: "")
                    put("colorHex", item.colorHex ?: "")
                    put("createdAt", item.createdAt)
                })
            }
            json.put("objects", arr)
            getIndexFile(context).writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun saveObject(
        context: Context,
        name: String,
        objContent: String,
        colorHex: String? = null,
        textureBitmap: Bitmap? = null,
        textureAssetId: String? = null
    ): SavedObjectItem {
        val indexFile = getIndexFile(context)
        if (!indexFile.exists()) {
            val defaults = createDefaultStarterObjectsDirectly(context)
            saveIndex(context, defaults)
        }

        val id = UUID.randomUUID().toString()
        val itemDir = File(getStorageDir(context), id).apply { if (!exists()) mkdirs() }
        val objFile = File(itemDir, "model.obj")
        objFile.writeText(objContent)

        var texturePath: String? = null
        if (textureBitmap != null) {
            val texFile = File(itemDir, "texture.png")
            FileOutputStream(texFile).use { out ->
                textureBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            texturePath = texFile.absolutePath
            BuiltInAssets.registerCustomTexture("saved_tex_$id", textureBitmap)
        }

        val newItem = SavedObjectItem(
            id = id,
            name = name,
            objFilePath = objFile.absolutePath,
            texturePath = texturePath,
            colorHex = colorHex
        )

        val current = getSavedObjects(context).toMutableList()
        current.add(0, newItem)
        saveIndex(context, current)
        return newItem
    }

    @Synchronized
    fun saveNodeAsObject(context: Context, node: SceneNode, customName: String? = null): SavedObjectItem? {
        val name = customName ?: node.name
        val id = UUID.randomUUID().toString()
        val itemDir = File(getStorageDir(context), id).apply { if (!exists()) mkdirs() }
        val objFile = File(itemDir, "model.obj")

        val objData = node.objModelData
        if (objData != null && objData.triangles.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("# MCA Saved Object: $name\n")
            var vIdx = 1
            for (t in objData.triangles) {
                sb.append("v ${t.v1.x} ${t.v1.y} ${t.v1.z}\n")
                sb.append("v ${t.v2.x} ${t.v2.y} ${t.v2.z}\n")
                sb.append("v ${t.v3.x} ${t.v3.y} ${t.v3.z}\n")
                sb.append("vn ${t.n1.x} ${t.n1.y} ${t.n1.z}\n")
                sb.append("vn ${t.n2.x} ${t.n2.y} ${t.n2.z}\n")
                sb.append("vn ${t.n3.x} ${t.n3.y} ${t.n3.z}\n")
                sb.append("vt ${t.uv1.first} ${1f - t.uv1.second}\n")
                sb.append("vt ${t.uv2.first} ${1f - t.uv2.second}\n")
                sb.append("vt ${t.uv3.first} ${1f - t.uv3.second}\n")
                sb.append("f $vIdx/$vIdx/$vIdx ${vIdx + 1}/${vIdx + 1}/${vIdx + 1} ${vIdx + 2}/${vIdx + 2}/${vIdx + 2}\n")
                vIdx += 3
            }
            objFile.writeText(sb.toString())
        } else {
            // Generate standard cuboid OBJ matching boxDimensions
            val dim = node.boxDimensions
            val hx = dim.x * 0.5f; val hy = dim.y * 0.5f; val hz = dim.z * 0.5f
            val sb = StringBuilder()
            sb.append("# MCA Saved Block: $name\n")
            sb.append("v -$hx -$hy  $hz\n")
            sb.append("v  $hx -$hy  $hz\n")
            sb.append("v  $hx  $hy  $hz\n")
            sb.append("v -$hx  $hy  $hz\n")
            sb.append("v -$hx -$hy -$hz\n")
            sb.append("v  $hx -$hy -$hz\n")
            sb.append("v  $hx  $hy -$hz\n")
            sb.append("v -$hx  $hy -$hz\n")
            // 6 faces
            sb.append("f 1 2 3 4\n") // Front
            sb.append("f 6 5 8 7\n") // Back
            sb.append("f 4 3 7 8\n") // Top
            sb.append("f 5 6 2 1\n") // Bottom
            sb.append("f 5 1 4 8\n") // Left
            sb.append("f 2 6 7 3\n") // Right
            objFile.writeText(sb.toString())
        }

        val colorHex = String.format("#%06X", (0xFFFFFF and node.material.color))
        val newItem = SavedObjectItem(
            id = id,
            name = name,
            objFilePath = objFile.absolutePath,
            colorHex = colorHex
        )

        val current = getSavedObjects(context).toMutableList()
        current.add(0, newItem)
        saveIndex(context, current)
        return newItem
    }

    @Synchronized
    fun renameObject(context: Context, id: String, newName: String) {
        val current = getSavedObjects(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(name = newName.trim())
            saveIndex(context, current)
        }
    }

    @Synchronized
    fun deleteObject(context: Context, id: String) {
        val current = getSavedObjects(context).toMutableList()
        current.removeAll { it.id == id }
        saveIndex(context, current)
        try {
            val itemDir = File(getStorageDir(context), id)
            if (itemDir.exists()) itemDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun findObjFileByName(context: Context, fileName: String): File? {
        val storage = getStorageDir(context)
        return storage.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
    }

    private fun createDefaultStarterObjectsDirectly(context: Context): List<SavedObjectItem> {
        val list = mutableListOf<SavedObjectItem>()

        fun writeStarterItem(name: String, objText: String, colorHex: String): SavedObjectItem {
            val id = UUID.randomUUID().toString()
            val itemDir = File(getStorageDir(context), id).apply { if (!exists()) mkdirs() }
            val objFile = File(itemDir, "model.obj")
            objFile.writeText(objText)
            return SavedObjectItem(
                id = id,
                name = name,
                objFilePath = objFile.absolutePath,
                texturePath = null,
                colorHex = colorHex
            )
        }

        // 1. Wooden Chair
        val chairObj = """
            # Wooden Chair
            v -0.35 0.0 -0.35
            v -0.25 0.0 -0.35
            v -0.25 0.5 -0.35
            v -0.35 0.5 -0.35
            v  0.25 0.0 -0.35
            v  0.35 0.0 -0.35
            v  0.35 0.5 -0.35
            v  0.25 0.5 -0.35
            v -0.35 0.0  0.25
            v -0.25 0.0  0.25
            v -0.25 0.5  0.25
            v -0.35 0.5  0.25
            v  0.25 0.0  0.25
            v  0.35 0.0  0.25
            v  0.35 0.5  0.25
            v  0.25 0.5  0.25
            v -0.40 0.5 -0.40
            v  0.40 0.5 -0.40
            v  0.40 0.6 -0.40
            v -0.40 0.6 -0.40
            v -0.40 0.5  0.35
            v  0.40 0.5  0.35
            v  0.40 0.6  0.35
            v -0.40 0.6  0.35
            v -0.40 0.6 -0.40
            v  0.40 0.6 -0.40
            v  0.40 1.2 -0.40
            v -0.40 1.2 -0.40
            f 1 2 3 4
            f 5 6 7 8
            f 9 10 11 12
            f 13 14 15 16
            f 17 18 19 20
            f 21 22 23 24
            f 20 19 23 24
            f 17 18 22 21
            f 25 26 27 28
        """.trimIndent()
        list.add(writeStarterItem("Wooden Chair", chairObj, "#8B5A2B"))

        // 2. Stone Table
        val tableObj = """
            # Stone Table
            v -0.15 0.0 -0.15
            v  0.15 0.0 -0.15
            v  0.15 0.7 -0.15
            v -0.15 0.7 -0.15
            v -0.15 0.0  0.15
            v  0.15 0.0  0.15
            v  0.15 0.7  0.15
            v -0.15 0.7  0.15
            v -0.60 0.7 -0.60
            v  0.60 0.7 -0.60
            v  0.60 0.85 -0.60
            v -0.60 0.85 -0.60
            v -0.60 0.7  0.60
            v  0.60 0.7  0.60
            v  0.60 0.85  0.60
            v -0.60 0.85  0.60
            f 1 2 3 4
            f 6 5 8 7
            f 4 3 7 8
            f 9 10 11 12
            f 14 13 16 15
            f 12 11 15 16
            f 9 10 14 13
        """.trimIndent()
        list.add(writeStarterItem("Stone Table", tableObj, "#808080"))

        // 3. Golden Sword
        val swordObj = """
            # Golden Sword
            v -0.05 0.0 -0.05
            v  0.05 0.0 -0.05
            v  0.05 0.3 -0.05
            v -0.05 0.3 -0.05
            v -0.25 0.3 -0.08
            v  0.25 0.3 -0.08
            v  0.25 0.38 -0.08
            v -0.25 0.38 -0.08
            v -0.08 0.38 -0.03
            v  0.08 0.38 -0.03
            v  0.08 1.4 -0.03
            v  0.0 1.6 0.0
            v -0.08 1.4 -0.03
            f 1 2 3 4
            f 5 6 7 8
            f 9 10 11 12 13
        """.trimIndent()
        list.add(writeStarterItem("Golden Sword", swordObj, "#FFD700"))

        return list
    }
}
