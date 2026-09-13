package com.star4droid.mc.animation.ui.blocks

import android.content.Context
import com.star4droid.mc.animation.project.ProjectRepository
import org.json.JSONObject
import java.io.File

object CustomBlocksRepository {

    fun getCustomBlocksDir(context: Context, projectId: String? = null, projectName: String = ""): File {
        val baseDir = if (!projectId.isNullOrEmpty()) {
            ProjectRepository(context).getProjectDir(projectId, projectName)
        } else {
            context.getExternalFilesDir(null) ?: context.filesDir
        }
        val dir = File(baseDir, "custom_blocks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadCustomPresets(context: Context, projectId: String? = null, projectName: String = ""): List<CustomBlockPreset> {
        val projectDir = getCustomBlocksDir(context, projectId, projectName)
        val globalDir = getCustomBlocksDir(context, null)

        val list = mutableListOf<CustomBlockPreset>()
        val seenNames = mutableSetOf<String>()

        fun loadFromDir(dir: File) {
            dir.listFiles()?.filter { it.extension.lowercase() == "json" }?.forEach { f ->
                if (seenNames.add(f.name)) {
                    try {
                        val json = f.readText()
                        val jsonObj = JSONObject(json)
                        val id = jsonObj.optString("id", f.nameWithoutExtension)
                        val name = jsonObj.optString("name", f.nameWithoutExtension)
                        val desc = jsonObj.optString("description", "Custom AI Animation Block")
                        val cat = jsonObj.optString("category", "CHARACTER")
                        val dur = jsonObj.optDouble("duration", 2.0).toFloat()
                        list.add(CustomBlockPreset(id, name, desc, cat, dur, json, f))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        loadFromDir(projectDir)
        if (projectDir.absolutePath != globalDir.absolutePath) {
            loadFromDir(globalDir)
        }
        return list.sortedBy { it.name }
    }

    fun savePreset(context: Context, name: String, description: String, category: String, jsonContent: String, projectId: String? = null, projectName: String = ""): File {
        val dir = getCustomBlocksDir(context, projectId, projectName)
        val safeName = name.replace("[^a-zA-Z0-9_]".toRegex(), "_")
        val file = File(dir, "${safeName}_${System.currentTimeMillis() % 10000}.json")
        file.writeText(jsonContent)
        return file
    }

    fun renamePreset(context: Context, preset: CustomBlockPreset, newName: String, projectId: String? = null, projectName: String = ""): Boolean {
        val file = preset.file ?: return false
        if (!file.exists()) return false
        try {
            val json = JSONObject(preset.jsonContent)
            json.put("name", newName)
            val updatedJsonText = json.toString(2)
            val dir = getCustomBlocksDir(context, projectId, projectName)
            val safeName = newName.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val newFile = File(dir, "${safeName}_${System.currentTimeMillis() % 10000}.json")
            newFile.writeText(updatedJsonText)
            file.delete()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun deletePreset(preset: CustomBlockPreset): Boolean {
        return preset.file?.delete() ?: false
    }
}
