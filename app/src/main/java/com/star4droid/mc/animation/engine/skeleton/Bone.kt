package com.star4droid.mc.animation.engine.skeleton

import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.Transform
import java.util.UUID

data class Bone(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var parentIndex: Int = -1, // -1 = Root bone
    var localTransform: Transform = Transform(),
    val inverseBindMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f },
    val worldMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f },
    val skinningMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f },
    var length: Float = 0.5f,
    var standardRole: StandardBoneRole? = null
) {
    fun copyBone(): Bone {
        val b = Bone(
            id = id,
            name = name,
            parentIndex = parentIndex,
            localTransform = localTransform.copyTransform(),
            length = length,
            standardRole = standardRole
        )
        System.arraycopy(inverseBindMatrix, 0, b.inverseBindMatrix, 0, 16)
        System.arraycopy(worldMatrix, 0, b.worldMatrix, 0, 16)
        System.arraycopy(skinningMatrix, 0, b.skinningMatrix, 0, 16)
        return b
    }
}

enum class StandardBoneRole(val idName: String, val displayName: String) {
    HIPS("hips", "Hips / Pelvis"),
    SPINE("spine", "Spine"),
    CHEST("chest", "Chest"),
    NECK("neck", "Neck"),
    HEAD("head", "Head"),
    ARM_R("arm_r", "Right Upper Arm"),
    FOREARM_R("forearm_r", "Right Forearm"),
    HAND_R("hand_r", "Right Hand"),
    ARM_L("arm_l", "Left Upper Arm"),
    FOREARM_L("forearm_l", "Left Forearm"),
    HAND_L("hand_l", "Left Hand"),
    THIGH_R("thigh_r", "Right Thigh"),
    CALF_R("calf_r", "Right Calf"),
    FOOT_R("foot_r", "Right Foot"),
    THIGH_L("thigh_l", "Left Thigh"),
    CALF_L("calf_l", "Left Calf"),
    FOOT_L("foot_l", "Left Foot");

    companion object {
        fun fromString(s: String?): StandardBoneRole? {
            if (s.isNullOrBlank()) return null
            val clean = s.replace("-", "_").replace(" ", "_").lowercase()
            return values().firstOrNull {
                it.idName.equals(clean, ignoreCase = true) ||
                clean.contains(it.idName) ||
                clean.contains(it.name.lowercase())
            }
        }
    }
}
