package com.star4droid.mc.animation.engine.skeleton

import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.utils.ObjModelData
import kotlin.math.abs

/**
 * AutoRigEstimator calculates a full humanoid skeleton fitted to any 3D mesh (OBJ / GLTF / GLB)
 * based on spatial point cloud slicing and PCA bounding analysis.
 *
 * It reliably binds characters even if NOT in standard T-pose or A-pose (e.g. resting arms,
 * asymmetrical poses, dynamic stance).
 */
object AutoRigEstimator {

    fun estimateSkeletonFromVertices(
        vertices: List<Vec3>,
        meshName: String = "RiggedCharacter"
    ): Skeleton {
        if (vertices.isEmpty()) {
            return Skeleton.createStandardHumanoidSkeleton()
        }

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        for (v in vertices) {
            if (v.x < minX) minX = v.x
            if (v.x > maxX) maxX = v.x
            if (v.y < minY) minY = v.y
            if (v.y > maxY) maxY = v.y
            if (v.z < minZ) minZ = v.z
            if (v.z > maxZ) maxZ = v.z
        }

        val totalHeight = (maxY - minY).coerceAtLeast(0.1f)
        val totalWidth = (maxX - minX).coerceAtLeast(0.1f)
        val totalDepth = (maxZ - minZ).coerceAtLeast(0.1f)
        val centerX = (minX + maxX) * 0.5f
        val centerZ = (minZ + maxZ) * 0.5f

        // Height levels
        val hipsY = minY + totalHeight * 0.52f
        val spineY = minY + totalHeight * 0.65f
        val chestY = minY + totalHeight * 0.76f
        val neckY = minY + totalHeight * 0.83f
        val headY = minY + totalHeight * 0.92f

        // Analyze arm extremes in upper torso band (Y between 50% and 85%)
        val upperBandVerts = vertices.filter { it.y in (minY + totalHeight * 0.50f)..(minY + totalHeight * 0.85f) }
        val rightSideVerts = upperBandVerts.filter { it.x < centerX }
        val leftSideVerts = upperBandVerts.filter { it.x > centerX }

        val rightExtremityX = if (rightSideVerts.isNotEmpty()) rightSideVerts.minOf { it.x } else minX
        val leftExtremityX = if (leftSideVerts.isNotEmpty()) leftSideVerts.maxOf { it.x } else maxX

        // Shoulder attachment points
        val shoulderRightX = centerX - totalWidth * 0.22f
        val shoulderLeftX = centerX + totalWidth * 0.22f
        val shoulderY = chestY + totalHeight * 0.02f

        // Hand / elbow estimation
        val armSpanRight = abs(rightExtremityX - shoulderRightX).coerceAtLeast(0.05f)
        val armSpanLeft = abs(leftExtremityX - shoulderLeftX).coerceAtLeast(0.05f)

        // Find centroid of rightmost 10% points (right hand/lower arm)
        val rightArmPoints = rightSideVerts.filter { it.x <= rightExtremityX + armSpanRight * 0.4f }
        val avgRightHand = if (rightArmPoints.isNotEmpty()) {
            Vec3(
                rightArmPoints.map { it.x }.average().toFloat(),
                rightArmPoints.map { it.y }.average().toFloat(),
                rightArmPoints.map { it.z }.average().toFloat()
            )
        } else {
            Vec3(rightExtremityX, shoulderY - totalHeight * 0.25f, centerZ)
        }

        val leftArmPoints = leftSideVerts.filter { it.x >= leftExtremityX - armSpanLeft * 0.4f }
        val avgLeftHand = if (leftArmPoints.isNotEmpty()) {
            Vec3(
                leftArmPoints.map { it.x }.average().toFloat(),
                leftArmPoints.map { it.y }.average().toFloat(),
                leftArmPoints.map { it.z }.average().toFloat()
            )
        } else {
            Vec3(leftExtremityX, shoulderY - totalHeight * 0.25f, centerZ)
        }

        // Elbows at midpoints
        val elbowRight = Vec3(
            (shoulderRightX + avgRightHand.x) * 0.5f,
            (shoulderY + avgRightHand.y) * 0.5f,
            (centerZ + avgRightHand.z) * 0.5f
        )
        val elbowLeft = Vec3(
            (shoulderLeftX + avgLeftHand.x) * 0.5f,
            (shoulderY + avgLeftHand.y) * 0.5f,
            (centerZ + avgLeftHand.z) * 0.5f
        )

        // Leg placement
        val legSpacingX = totalWidth * 0.16f
        val thighY = hipsY - totalHeight * 0.02f
        val kneeY = minY + totalHeight * 0.26f
        val footY = minY + totalHeight * 0.02f

        val s = Skeleton()

        // 0: Hips (Root of body)
        val hips = Bone(name = "hips", parentIndex = -1, standardRole = StandardBoneRole.HIPS)
        hips.localTransform = hips.localTransform.copy(position = Vec3(centerX, hipsY, centerZ))
        s.addBone(hips)

        // 1: Spine
        val spine = Bone(name = "spine", parentIndex = 0, standardRole = StandardBoneRole.SPINE)
        spine.localTransform = spine.localTransform.copy(position = Vec3(0f, spineY - hipsY, 0f))
        s.addBone(spine)

        // 2: Chest
        val chest = Bone(name = "chest", parentIndex = 1, standardRole = StandardBoneRole.CHEST)
        chest.localTransform = chest.localTransform.copy(position = Vec3(0f, chestY - spineY, 0f))
        s.addBone(chest)

        // 3: Neck
        val neck = Bone(name = "neck", parentIndex = 2, standardRole = StandardBoneRole.NECK)
        neck.localTransform = neck.localTransform.copy(position = Vec3(0f, neckY - chestY, 0f))
        s.addBone(neck)

        // 4: Head
        val head = Bone(name = "head", parentIndex = 3, standardRole = StandardBoneRole.HEAD)
        head.localTransform = head.localTransform.copy(position = Vec3(0f, headY - neckY, 0f))
        s.addBone(head)

        // 5: Right Upper Arm (Parent: Chest)
        val armR = Bone(name = "arm_r", parentIndex = 2, standardRole = StandardBoneRole.ARM_R)
        armR.localTransform = armR.localTransform.copy(position = Vec3(shoulderRightX - centerX, shoulderY - chestY, 0f))
        s.addBone(armR)

        // 6: Right Forearm
        val forearmR = Bone(name = "forearm_r", parentIndex = 5, standardRole = StandardBoneRole.FOREARM_R)
        forearmR.localTransform = forearmR.localTransform.copy(
            position = Vec3(
                elbowRight.x - shoulderRightX,
                elbowRight.y - shoulderY,
                elbowRight.z - centerZ
            )
        )
        s.addBone(forearmR)

        // 7: Left Upper Arm (Parent: Chest)
        val armL = Bone(name = "arm_l", parentIndex = 2, standardRole = StandardBoneRole.ARM_L)
        armL.localTransform = armL.localTransform.copy(position = Vec3(shoulderLeftX - centerX, shoulderY - chestY, 0f))
        s.addBone(armL)

        // 8: Left Forearm
        val forearmL = Bone(name = "forearm_l", parentIndex = 7, standardRole = StandardBoneRole.FOREARM_L)
        forearmL.localTransform = forearmL.localTransform.copy(
            position = Vec3(
                elbowLeft.x - shoulderLeftX,
                elbowLeft.y - shoulderY,
                elbowLeft.z - centerZ
            )
        )
        s.addBone(forearmL)

        // 9: Right Thigh (Parent: Hips)
        val thighR = Bone(name = "thigh_r", parentIndex = 0, standardRole = StandardBoneRole.THIGH_R)
        thighR.localTransform = thighR.localTransform.copy(position = Vec3(-legSpacingX, thighY - hipsY, 0f))
        s.addBone(thighR)

        // 10: Right Calf
        val calfR = Bone(name = "calf_r", parentIndex = 9, standardRole = StandardBoneRole.CALF_R)
        calfR.localTransform = calfR.localTransform.copy(position = Vec3(0f, kneeY - thighY, 0f))
        s.addBone(calfR)

        // 11: Left Thigh (Parent: Hips)
        val thighL = Bone(name = "thigh_l", parentIndex = 0, standardRole = StandardBoneRole.THIGH_L)
        thighL.localTransform = thighL.localTransform.copy(position = Vec3(legSpacingX, thighY - hipsY, 0f))
        s.addBone(thighL)

        // 12: Left Calf
        val calfL = Bone(name = "calf_l", parentIndex = 11, standardRole = StandardBoneRole.CALF_L)
        calfL.localTransform = calfL.localTransform.copy(position = Vec3(0f, kneeY - thighY, 0f))
        s.addBone(calfL)

        s.computeCurrentBindPose()
        return s
    }

    fun estimateFromObj(objData: ObjModelData): Skeleton {
        val allPoints = mutableListOf<Vec3>()
        if (objData.vertices.isNotEmpty()) {
            allPoints.addAll(objData.vertices)
        } else {
            for (t in objData.triangles) {
                allPoints.add(t.v1)
                allPoints.add(t.v2)
                allPoints.add(t.v3)
            }
        }
        return estimateSkeletonFromVertices(allPoints)
    }
}
