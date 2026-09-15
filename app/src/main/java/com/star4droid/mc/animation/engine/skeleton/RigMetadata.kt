package com.star4droid.mc.animation.engine.skeleton

import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.Transform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BoneMeta(
    val name: String,
    val parentIndex: Int,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val length: Float,
    val standardRole: String? = null
)

data class RigMetadata(
    val modelFileName: String,
    val bones: List<BoneMeta> = emptyList(),
    val boneRemappings: Map<String, String> = emptyMap(),
    val transparency: Float = 0.6f
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("modelFileName", modelFileName)
        root.put("transparency", transparency.toDouble())

        val bonesArr = JSONArray()
        for (b in bones) {
            val bObj = JSONObject().apply {
                put("name", b.name)
                put("parentIndex", b.parentIndex)
                put("posX", b.posX.toDouble())
                put("posY", b.posY.toDouble())
                put("posZ", b.posZ.toDouble())
                put("rotX", b.rotX.toDouble())
                put("rotY", b.rotY.toDouble())
                put("rotZ", b.rotZ.toDouble())
                put("length", b.length.toDouble())
                b.standardRole?.let { put("standardRole", it) }
            }
            bonesArr.put(bObj)
        }
        root.put("bones", bonesArr)

        val remapObj = JSONObject()
        for ((k, v) in boneRemappings) {
            remapObj.put(k, v)
        }
        root.put("boneRemappings", remapObj)

        return root.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): RigMetadata {
            val root = JSONObject(jsonStr)
            val modelFileName = root.optString("modelFileName", "")
            val transparency = root.optDouble("transparency", 0.6).toFloat()

            val bonesList = mutableListOf<BoneMeta>()
            val bonesArr = root.optJSONArray("bones")
            if (bonesArr != null) {
                for (i in 0 until bonesArr.length()) {
                    val bObj = bonesArr.getJSONObject(i)
                    bonesList.add(
                        BoneMeta(
                            name = bObj.optString("name", "bone_$i"),
                            parentIndex = bObj.optInt("parentIndex", -1),
                            posX = bObj.optDouble("posX", 0.0).toFloat(),
                            posY = bObj.optDouble("posY", 0.0).toFloat(),
                            posZ = bObj.optDouble("posZ", 0.0).toFloat(),
                            rotX = bObj.optDouble("rotX", 0.0).toFloat(),
                            rotY = bObj.optDouble("rotY", 0.0).toFloat(),
                            rotZ = bObj.optDouble("rotZ", 0.0).toFloat(),
                            length = bObj.optDouble("length", 0.5).toFloat(),
                            standardRole = bObj.optString("standardRole", "").ifBlank { null }
                        )
                    )
                }
            }

            val remappings = mutableMapOf<String, String>()
            val remapObj = root.optJSONObject("boneRemappings")
            if (remapObj != null) {
                val keys = remapObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    remappings[key] = remapObj.getString(key)
                }
            }

            return RigMetadata(
                modelFileName = modelFileName,
                bones = bonesList,
                boneRemappings = remappings,
                transparency = transparency
            )
        }

        fun getMetaFile(modelFile: File): File {
            return File(modelFile.parentFile, "${modelFile.name}.meta")
        }

        fun saveForModel(modelFile: File, meta: RigMetadata): Boolean {
            return try {
                val file = getMetaFile(modelFile)
                file.writeText(meta.toJson())
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun loadForModel(modelFile: File): RigMetadata? {
            return try {
                val file = getMetaFile(modelFile)
                if (file.exists()) {
                    fromJson(file.readText())
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun skeletonToMetadata(skeleton: Skeleton, modelFileName: String, transparency: Float = 0.6f, remappings: Map<String, String> = emptyMap()): RigMetadata {
            val list = skeleton.bones.map { b ->
                BoneMeta(
                    name = b.name,
                    parentIndex = b.parentIndex,
                    posX = b.localTransform.position.x,
                    posY = b.localTransform.position.y,
                    posZ = b.localTransform.position.z,
                    rotX = b.localTransform.rotation.x,
                    rotY = b.localTransform.rotation.y,
                    rotZ = b.localTransform.rotation.z,
                    length = b.length,
                    standardRole = b.standardRole?.idName
                )
            }
            return RigMetadata(modelFileName, list, remappings, transparency)
        }

        fun metadataToSkeleton(meta: RigMetadata): Skeleton {
            val s = Skeleton()
            for (bm in meta.bones) {
                val b = Bone(
                    name = bm.name,
                    parentIndex = bm.parentIndex,
                    length = bm.length,
                    standardRole = StandardBoneRole.fromString(bm.standardRole)
                )
                b.localTransform = b.localTransform.copy(
                    position = Vec3(bm.posX, bm.posY, bm.posZ),
                    rotation = Vec3(bm.rotX, bm.rotY, bm.rotZ)
                )
                s.addBone(b)
            }
            s.computeCurrentBindPose()
            return s
        }
    }
}
