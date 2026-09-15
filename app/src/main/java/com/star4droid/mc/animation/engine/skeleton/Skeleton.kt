package com.star4droid.mc.animation.engine.skeleton

import android.opengl.Matrix
import com.star4droid.mc.animation.engine.math.Vec3

class Skeleton(
    val bones: MutableList<Bone> = mutableListOf()
) {
    // Continuous uniform array for GPU upload (MAX 32 bones * 16 floats = 512 floats)
    val skinningPalette = FloatArray(MAX_BONES * 16) { if (it % 21 == 0) 1f else 0f }

    private val tempLocalMat = FloatArray(16)

    fun findBone(name: String): Bone? {
        return bones.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun findBoneByRole(role: StandardBoneRole): Bone? {
        return bones.firstOrNull { it.standardRole == role }
            ?: bones.firstOrNull { StandardBoneRole.fromString(it.name) == role }
    }

    fun getBoneIndex(name: String): Int {
        return bones.indexOfFirst { it.name.equals(name, ignoreCase = true) }
    }

    fun addBone(bone: Bone): Int {
        bones.add(bone)
        return bones.size - 1
    }

    fun updateWorldMatrices() {
        // Fast, zero-allocation topological evaluation loop
        val count = minOf(bones.size, MAX_BONES)
        for (i in 0 until count) {
            val bone = bones[i]
            val localT = bone.localTransform

            // Compute local matrix: T * R * S
            Matrix.setIdentityM(tempLocalMat, 0)
            Matrix.translateM(tempLocalMat, 0, localT.position.x, localT.position.y, localT.position.z)
            Matrix.rotateM(tempLocalMat, 0, localT.rotation.y, 0f, 1f, 0f)
            Matrix.rotateM(tempLocalMat, 0, localT.rotation.x, 1f, 0f, 0f)
            Matrix.rotateM(tempLocalMat, 0, localT.rotation.z, 0f, 0f, 1f)
            Matrix.scaleM(tempLocalMat, 0, localT.scale.x, localT.scale.y, localT.scale.z)

            val parent = bone.parentIndex
            if (parent >= 0 && parent < i) {
                Matrix.multiplyMM(bone.worldMatrix, 0, bones[parent].worldMatrix, 0, tempLocalMat, 0)
            } else {
                System.arraycopy(tempLocalMat, 0, bone.worldMatrix, 0, 16)
            }

            // Skinning matrix = WorldMatrix * InverseBindMatrix
            Matrix.multiplyMM(bone.skinningMatrix, 0, bone.worldMatrix, 0, bone.inverseBindMatrix, 0)

            // Copy into continuous palette buffer
            System.arraycopy(bone.skinningMatrix, 0, skinningPalette, i * 16, 16)
        }
    }

    fun computeCurrentBindPose() {
        // Sets inverseBindMatrix to the inverse of the current worldMatrix
        updateWorldMatrices()
        for (bone in bones) {
            Matrix.invertM(bone.inverseBindMatrix, 0, bone.worldMatrix, 0)
        }
    }

    fun copySkeleton(): Skeleton {
        val s = Skeleton()
        for (b in bones) {
            s.bones.add(b.copyBone())
        }
        System.arraycopy(skinningPalette, 0, s.skinningPalette, 0, skinningPalette.size)
        return s
    }

    companion object {
        const val MAX_BONES = 32

        fun createStandardHumanoidSkeleton(): Skeleton {
            val s = Skeleton()
            // 0: Hips
            val hips = Bone(name = "hips", parentIndex = -1, standardRole = StandardBoneRole.HIPS)
            hips.localTransform = hips.localTransform.copy(position = Vec3(0f, 1.0f, 0f))
            s.addBone(hips)

            // 1: Spine
            val spine = Bone(name = "spine", parentIndex = 0, standardRole = StandardBoneRole.SPINE)
            spine.localTransform = spine.localTransform.copy(position = Vec3(0f, 0.35f, 0f))
            s.addBone(spine)

            // 2: Chest
            val chest = Bone(name = "chest", parentIndex = 1, standardRole = StandardBoneRole.CHEST)
            chest.localTransform = chest.localTransform.copy(position = Vec3(0f, 0.35f, 0f))
            s.addBone(chest)

            // 3: Neck
            val neck = Bone(name = "neck", parentIndex = 2, standardRole = StandardBoneRole.NECK)
            neck.localTransform = neck.localTransform.copy(position = Vec3(0f, 0.2f, 0f))
            s.addBone(neck)

            // 4: Head
            val head = Bone(name = "head", parentIndex = 3, standardRole = StandardBoneRole.HEAD)
            head.localTransform = head.localTransform.copy(position = Vec3(0f, 0.2f, 0f))
            s.addBone(head)

            // 5: Right Upper Arm
            val armR = Bone(name = "arm_r", parentIndex = 2, standardRole = StandardBoneRole.ARM_R)
            armR.localTransform = armR.localTransform.copy(position = Vec3(-0.35f, 0.05f, 0f))
            s.addBone(armR)

            // 6: Right Forearm
            val forearmR = Bone(name = "forearm_r", parentIndex = 5, standardRole = StandardBoneRole.FOREARM_R)
            forearmR.localTransform = forearmR.localTransform.copy(position = Vec3(0f, -0.35f, 0f))
            s.addBone(forearmR)

            // 7: Left Upper Arm
            val armL = Bone(name = "arm_l", parentIndex = 2, standardRole = StandardBoneRole.ARM_L)
            armL.localTransform = armL.localTransform.copy(position = Vec3(0.35f, 0.05f, 0f))
            s.addBone(armL)

            // 8: Left Forearm
            val forearmL = Bone(name = "forearm_l", parentIndex = 7, standardRole = StandardBoneRole.FOREARM_L)
            forearmL.localTransform = forearmL.localTransform.copy(position = Vec3(0f, -0.35f, 0f))
            s.addBone(forearmL)

            // 9: Right Thigh
            val thighR = Bone(name = "thigh_r", parentIndex = 0, standardRole = StandardBoneRole.THIGH_R)
            thighR.localTransform = thighR.localTransform.copy(position = Vec3(-0.18f, -0.1f, 0f))
            s.addBone(thighR)

            // 10: Right Calf
            val calfR = Bone(name = "calf_r", parentIndex = 9, standardRole = StandardBoneRole.CALF_R)
            calfR.localTransform = calfR.localTransform.copy(position = Vec3(0f, -0.45f, 0f))
            s.addBone(calfR)

            // 11: Left Thigh
            val thighL = Bone(name = "thigh_l", parentIndex = 0, standardRole = StandardBoneRole.THIGH_L)
            thighL.localTransform = thighL.localTransform.copy(position = Vec3(0.18f, -0.1f, 0f))
            s.addBone(thighL)

            // 12: Left Calf
            val calfL = Bone(name = "calf_l", parentIndex = 11, standardRole = StandardBoneRole.CALF_L)
            calfL.localTransform = calfL.localTransform.copy(position = Vec3(0f, -0.45f, 0f))
            s.addBone(calfL)

            s.computeCurrentBindPose()
            return s
        }
    }
}
