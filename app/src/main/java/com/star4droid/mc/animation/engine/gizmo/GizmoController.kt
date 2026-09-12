package com.star4droid.mc.animation.engine.gizmo

import com.star4droid.mc.animation.engine.history.HistoryManager
import com.star4droid.mc.animation.engine.history.TransformCommand
import com.star4droid.mc.animation.engine.math.Mat4
import com.star4droid.mc.animation.engine.math.Ray
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.CharacterPartType
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.Transform
import kotlin.math.abs

enum class EditorMode {
    SELECT,
    MOVE,
    ROTATE,
    SCALE,
    CAMERA
}

enum class GizmoAxis {
    NONE,
    X,
    Y,
    Z,
    CENTER
}

class GizmoController(
    private val sceneGraph: SceneGraph,
    private val historyManager: HistoryManager
) {
    var currentMode: EditorMode = EditorMode.MOVE
    var activeAxis: GizmoAxis = GizmoAxis.NONE
    private var initialTransform: Transform? = null
    private var dragStartPlanePoint: Vec3? = null

    fun checkAxisHit(ray: Ray, gizmoPosition: Vec3, gizmoScale: Float = 1.0f): GizmoAxis {
        val axisLength = 1.5f * gizmoScale
        val threshold = 0.35f * gizmoScale

        // Center sphere
        if (ray.distanceToPoint(gizmoPosition) < threshold) {
            return GizmoAxis.CENTER
        }

        // X axis (along (1, 0, 0))
        val xDist = ray.distanceToSegment(gizmoPosition, gizmoPosition + Vec3(axisLength, 0f, 0f))
        if (xDist < threshold) return GizmoAxis.X

        // Y axis (along (0, 1, 0))
        val yDist = ray.distanceToSegment(gizmoPosition, gizmoPosition + Vec3(0f, axisLength, 0f))
        if (yDist < threshold) return GizmoAxis.Y

        // Z axis (along (0, 0, 1))
        val zDist = ray.distanceToSegment(gizmoPosition, gizmoPosition + Vec3(0f, 0f, axisLength))
        if (zDist < threshold) return GizmoAxis.Z

        return GizmoAxis.NONE
    }

    private var targetScaleNode: SceneNode? = null

    private fun findCharacterRoot(node: SceneNode): SceneNode {
        var cur = node
        while (cur.parentId != null && cur.type == SceneNodeType.CHARACTER_PART) {
            val parent = sceneGraph.getNode(cur.parentId!!) ?: break
            cur = parent
        }
        return cur
    }

    private fun clampSkeletalRotation(partType: com.star4droid.mc.animation.engine.scene.CharacterPartType?, rot: Vec3): Vec3 {
        return when (partType) {
            com.star4droid.mc.animation.engine.scene.CharacterPartType.HEAD -> Vec3(
                rot.x.coerceIn(-60f, 50f),
                rot.y.coerceIn(-85f, 85f),
                rot.z.coerceIn(-35f, 35f)
            )
            com.star4droid.mc.animation.engine.scene.CharacterPartType.RIGHT_ARM -> Vec3(
                rot.x.coerceIn(-180f, 90f),
                rot.y.coerceIn(-45f, 45f),
                rot.z.coerceIn(-135f, 15f)
            )
            com.star4droid.mc.animation.engine.scene.CharacterPartType.LEFT_ARM -> Vec3(
                rot.x.coerceIn(-180f, 90f),
                rot.y.coerceIn(-45f, 45f),
                rot.z.coerceIn(-15f, 135f)
            )
            com.star4droid.mc.animation.engine.scene.CharacterPartType.RIGHT_LEG -> Vec3(
                rot.x.coerceIn(-85f, 80f),
                rot.y.coerceIn(-25f, 25f),
                rot.z.coerceIn(-30f, 20f)
            )
            com.star4droid.mc.animation.engine.scene.CharacterPartType.LEFT_LEG -> Vec3(
                rot.x.coerceIn(-85f, 80f),
                rot.y.coerceIn(-25f, 25f),
                rot.z.coerceIn(-20f, 30f)
            )
            com.star4droid.mc.animation.engine.scene.CharacterPartType.BODY -> Vec3(
                rot.x.coerceIn(-35f, 35f),
                rot.y.coerceIn(-45f, 45f),
                rot.z.coerceIn(-25f, 25f)
            )
            else -> rot
        }
    }

    fun startDrag(selectedNode: SceneNode, axis: GizmoAxis, initialRay: Ray) {
        activeAxis = axis
        if (currentMode == EditorMode.SCALE && selectedNode.type == SceneNodeType.CHARACTER_PART) {
            val root = findCharacterRoot(selectedNode)
            targetScaleNode = root
            initialTransform = root.baseTransform.copyTransform()
        } else {
            targetScaleNode = null
            initialTransform = selectedNode.baseTransform.copyTransform()
        }
        val nodePos = selectedNode.getWorldPosition()

        // Choose plane normal for raycasting
        val planeNormal = when (axis) {
            GizmoAxis.Y -> Vec3(initialRay.direction.x, 0f, initialRay.direction.z).normalized()
            GizmoAxis.X -> Vec3(0f, 1f, 0f)
            GizmoAxis.Z -> Vec3(0f, 1f, 0f)
            else -> -initialRay.direction
        }
        val t = initialRay.intersectPlane(nodePos, planeNormal)
        dragStartPlanePoint = if (t != null) initialRay.getPoint(t) else nodePos
    }

    fun updateDrag(selectedNode: SceneNode, currentRay: Ray) {
        val startPt = dragStartPlanePoint ?: return
        val initT = initialTransform ?: return
        val nodePos = selectedNode.getWorldPosition()

        val planeNormal = when (activeAxis) {
            GizmoAxis.Y -> Vec3(currentRay.direction.x, 0f, currentRay.direction.z).normalized()
            GizmoAxis.X, GizmoAxis.Z -> Vec3(0f, 1f, 0f)
            else -> -currentRay.direction
        }

        val t = currentRay.intersectPlane(nodePos, planeNormal) ?: return
        val curPt = currentRay.getPoint(t)
        val delta = curPt - startPt

        when (currentMode) {
            EditorMode.MOVE -> {
                // Skeleton constraints: Limbs are anchored to joints; only character root can translate
                if (selectedNode.type == SceneNodeType.CHARACTER_PART) {
                    // Locked to joint: body parts cannot be detached from the skeleton
                    return
                }
                var posDelta = Vec3.ZERO
                when (activeAxis) {
                    GizmoAxis.X -> posDelta = Vec3(delta.x, 0f, 0f)
                    GizmoAxis.Y -> posDelta = Vec3(0f, delta.y, 0f)
                    GizmoAxis.Z -> posDelta = Vec3(0f, 0f, delta.z)
                    GizmoAxis.CENTER -> posDelta = delta
                    GizmoAxis.NONE -> {}
                }
                selectedNode.baseTransform = initT.copy(position = initT.position + posDelta)
                selectedNode.animatedTransform = selectedNode.baseTransform.copyTransform()
            }
            EditorMode.ROTATE -> {
                val rotFactor = 90.0f
                var rotDelta = Vec3.ZERO
                when (activeAxis) {
                    GizmoAxis.X -> rotDelta = Vec3(delta.y * rotFactor, 0f, 0f)
                    GizmoAxis.Y -> rotDelta = Vec3(0f, -delta.x * rotFactor, 0f)
                    GizmoAxis.Z -> rotDelta = Vec3(0f, 0f, delta.x * rotFactor)
                    GizmoAxis.CENTER -> rotDelta = Vec3(delta.y * rotFactor, delta.x * rotFactor, 0f)
                    GizmoAxis.NONE -> {}
                }
                val rawRot = initT.rotation + rotDelta
                val finalRot = if (selectedNode.type == SceneNodeType.CHARACTER_PART) {
                    clampSkeletalRotation(selectedNode.characterPartType, rawRot)
                } else {
                    rawRot
                }
                selectedNode.baseTransform = initT.copy(rotation = finalRot)
                selectedNode.animatedTransform = selectedNode.baseTransform.copyTransform()
            }
            EditorMode.SCALE -> {
                val targetNode = targetScaleNode ?: selectedNode
                val isCharacter = targetScaleNode != null || selectedNode.type == SceneNodeType.CHARACTER_ROOT || selectedNode.type == SceneNodeType.CHARACTER_PART
                val newScale = if (isCharacter) {
                    val s = when (activeAxis) {
                        GizmoAxis.X -> delta.x
                        GizmoAxis.Y -> delta.y
                        GizmoAxis.Z -> delta.z
                        GizmoAxis.CENTER -> (delta.x + delta.y + delta.z) * 0.5f
                        else -> (delta.x + delta.y + delta.z) * 0.5f
                    }
                    val uniformFactor = (1.0f + s * 0.8f).coerceAtLeast(0.05f)
                    Vec3(
                        (initT.scale.x * uniformFactor).coerceIn(0.05f, 20f),
                        (initT.scale.y * uniformFactor).coerceIn(0.05f, 20f),
                        (initT.scale.z * uniformFactor).coerceIn(0.05f, 20f)
                    )
                } else {
                    // For blocks, props, cubes: support precise per-axis scaling and center uniform scaling
                    when (activeAxis) {
                        GizmoAxis.X -> {
                            val factor = (1.0f + delta.x * 0.8f).coerceAtLeast(0.05f)
                            initT.scale.copy(x = (initT.scale.x * factor).coerceIn(0.05f, 20f))
                        }
                        GizmoAxis.Y -> {
                            val factor = (1.0f + delta.y * 0.8f).coerceAtLeast(0.05f)
                            initT.scale.copy(y = (initT.scale.y * factor).coerceIn(0.05f, 20f))
                        }
                        GizmoAxis.Z -> {
                            val factor = (1.0f + delta.z * 0.8f).coerceAtLeast(0.05f)
                            initT.scale.copy(z = (initT.scale.z * factor).coerceIn(0.05f, 20f))
                        }
                        GizmoAxis.CENTER -> {
                            val s = (delta.x + delta.y + delta.z) * 0.5f
                            val factor = (1.0f + s * 0.8f).coerceAtLeast(0.05f)
                            Vec3(
                                (initT.scale.x * factor).coerceIn(0.05f, 20f),
                                (initT.scale.y * factor).coerceIn(0.05f, 20f),
                                (initT.scale.z * factor).coerceIn(0.05f, 20f)
                            )
                        }
                        else -> initT.scale
                    }
                }
                targetNode.baseTransform = initT.copy(scale = newScale)
                targetNode.animatedTransform = targetNode.baseTransform.copyTransform()
            }
            else -> {}
        }
        sceneGraph.updateWorldMatrices()
    }

    fun endDrag(selectedNode: SceneNode) {
        val initT = initialTransform
        val targetNode = targetScaleNode ?: selectedNode
        if (initT != null && activeAxis != GizmoAxis.NONE) {
            val newT = targetNode.baseTransform.copyTransform()
            historyManager.executeCommand(
                TransformCommand(sceneGraph, targetNode.id, initT, newT)
            )
        }
        activeAxis = GizmoAxis.NONE
        initialTransform = null
        dragStartPlanePoint = null
        targetScaleNode = null
    }

    private fun Ray.distanceToPoint(point: Vec3): Float {
        val w = point - origin
        val c1 = w.dot(direction)
        val c2 = direction.dot(direction)
        val b = c1 / c2
        val pb = origin + direction * b
        return point.distanceTo(pb)
    }

    private fun Ray.distanceToSegment(p0: Vec3, p1: Vec3): Float {
        val segDir = p1 - p0
        val segLen = segDir.length()
        if (segLen <= 1e-6f) return distanceToPoint(p0)

        val u = segDir / segLen
        val v = direction
        val w0 = p0 - origin

        val a = u.dot(u)
        val b = u.dot(v)
        val c = v.dot(v)
        val d = u.dot(w0)
        val e = v.dot(w0)

        val denom = a * c - b * b
        var sN = if (abs(denom) > 1e-6f) (b * e - c * d) / denom else 0f
        sN = sN.coerceIn(0f, segLen)

        val closestOnSeg = p0 + u * sN
        return distanceToPoint(closestOnSeg)
    }
}
