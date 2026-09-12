package com.star4droid.mc.animation.engine.scene

import com.star4droid.mc.animation.engine.math.Mat4
import com.star4droid.mc.animation.engine.math.Vec3
import java.util.concurrent.ConcurrentHashMap

class SceneGraph {
    val nodes: MutableMap<String, SceneNode> = ConcurrentHashMap()
    val rootNodeIds: MutableList<String> = mutableListOf()

    @Synchronized
    fun addNode(node: SceneNode, parentId: String? = null) {
        node.parentId = parentId
        nodes[node.id] = node

        if (parentId != null && nodes.containsKey(parentId)) {
            val parent = nodes[parentId]!!
            if (!parent.children.contains(node.id)) {
                parent.children.add(node.id)
            }
        } else {
            if (!rootNodeIds.contains(node.id)) {
                rootNodeIds.add(node.id)
            }
        }
        updateWorldMatrices()
    }

    @Synchronized
    fun removeNode(id: String): Boolean {
        val node = nodes[id] ?: return false

        // Recursively remove children
        val childrenCopy = ArrayList(node.children)
        for (childId in childrenCopy) {
            removeNode(childId)
        }

        // Remove from parent
        if (node.parentId != null) {
            nodes[node.parentId]?.children?.remove(id)
        } else {
            rootNodeIds.remove(id)
        }

        nodes.remove(id)
        updateWorldMatrices()
        return true
    }

    @Synchronized
    fun duplicateNode(id: String): String? {
        val original = nodes[id] ?: return null
        return duplicateRecursive(original, original.parentId)
    }

    private fun duplicateRecursive(source: SceneNode, parentId: String?): String {
        val clone = source.deepClone(parentId)
        // Offset position slightly so user can see the clone
        clone.baseTransform = clone.baseTransform.copy(
            position = clone.baseTransform.position.copy(
                x = clone.baseTransform.position.x + 1.0f,
                z = clone.baseTransform.position.z + 1.0f
            )
        )
        clone.animatedTransform = clone.baseTransform.copyTransform()
        addNode(clone, parentId)

        for (childId in source.children) {
            val child = nodes[childId] ?: continue
            duplicateRecursive(child, clone.id)
        }
        return clone.id
    }

    @Synchronized
    fun reparentNode(childId: String, newParentId: String?): Boolean {
        if (childId == newParentId) return false
        val child = nodes[childId] ?: return false

        // Prevent parenting to own descendant
        if (newParentId != null && isDescendant(newParentId, childId)) {
            return false
        }

        // Remove from current parent
        if (child.parentId != null) {
            nodes[child.parentId]?.children?.remove(childId)
        } else {
            rootNodeIds.remove(childId)
        }

        // Add to new parent
        child.parentId = newParentId
        if (newParentId != null) {
            nodes[newParentId]?.children?.add(childId)
        } else {
            rootNodeIds.add(childId)
        }

        updateWorldMatrices()
        return true
    }

    private fun isDescendant(potentialDescendantId: String, ancestorId: String): Boolean {
        var current: SceneNode? = nodes[potentialDescendantId]
        while (current != null) {
            if (current.parentId == ancestorId) return true
            current = current.parentId?.let { nodes[it] }
        }
        return false
    }

    @Synchronized
    fun updateWorldMatrices() {
        for (rootId in rootNodeIds) {
            val root = nodes[rootId] ?: continue
            updateNodeMatrixRecursive(root, Mat4.identity())
        }
    }

    private fun updateNodeMatrixRecursive(node: SceneNode, parentWorldMatrix: Mat4) {
        val localMatrix = node.animatedTransform.toLocalMatrix()
        node.worldMatrix = parentWorldMatrix * localMatrix

        for (childId in node.children) {
            val child = nodes[childId] ?: continue
            updateNodeMatrixRecursive(child, node.worldMatrix)
        }
    }

    @Synchronized
    fun resetToBase() {
        for (node in nodes.values) {
            node.animatedTransform = node.baseTransform.copyTransform()
        }
        updateWorldMatrices()
    }

    @Synchronized
    fun getActiveCamera(): SceneNode? {
        // Active Camera Rule:
        // 1. Find all cameras.
        // 2. Ignore disabled cameras.
        // 3. Sort cameras by hierarchy/order.
        // 4. Select the first enabled camera.
        val cameras = getAllCameras()
        return cameras.firstOrNull { it.cameraData?.enabled == true }
    }

    @Synchronized
    fun getAllCameras(): List<SceneNode> {
        val result = mutableListOf<SceneNode>()
        fun collect(nodeId: String) {
            val node = nodes[nodeId] ?: return
            if (node.type == SceneNodeType.CAMERA) {
                result.add(node)
            }
            for (childId in node.children) {
                collect(childId)
            }
        }
        for (rootId in rootNodeIds) {
            collect(rootId)
        }
        return result
    }

    fun getNode(id: String): SceneNode? = nodes[id]

    fun getAllNodes(): Collection<SceneNode> = nodes.values

    fun getRootNodes(): List<SceneNode> = rootNodeIds.mapNotNull { nodes[it] }

    @Synchronized
    fun findNonOverlappingPosition(
        requiredSpan: Vec3 = Vec3(2f, 2f, 2f),
        preferredOrigin: Vec3 = Vec3(0f, 0f, 0f)
    ): Vec3 {
        val occupiedBoxes = nodes.values
            .filter { it.parentId == null || it.type == SceneNodeType.CHARACTER_ROOT || it.type == SceneNodeType.GROUP }
            .map { node ->
                val pos = node.baseTransform.position
                val half = Vec3(
                    (node.boxDimensions.x * node.baseTransform.scale.x).coerceAtLeast(1f) * 0.5f + 0.5f,
                    (node.boxDimensions.y * node.baseTransform.scale.y).coerceAtLeast(1f) * 0.5f,
                    (node.boxDimensions.z * node.baseTransform.scale.z).coerceAtLeast(1f) * 0.5f + 0.5f
                )
                Pair(pos - half, pos + half)
            }

        if (occupiedBoxes.isEmpty()) {
            return preferredOrigin
        }

        val testHalf = Vec3(requiredSpan.x * 0.5f + 0.2f, requiredSpan.y * 0.5f, requiredSpan.z * 0.5f + 0.2f)
        val pMin = preferredOrigin - testHalf
        val pMax = preferredOrigin + testHalf
        val originClear = !occupiedBoxes.any { (bMin, bMax) ->
            pMin.x < bMax.x && pMax.x > bMin.x &&
            pMin.z < bMax.z && pMax.z > bMin.z
        }
        if (originClear) {
            return preferredOrigin
        }

        val stepSize = (requiredSpan.x.coerceAtLeast(requiredSpan.z) + 1.5f).coerceAtLeast(2.0f)
        for (ring in 1..15) {
            val dist = ring * stepSize
            val numSteps = (ring * 6).coerceAtLeast(6)
            for (step in 0 until numSteps) {
                val angle = (step.toDouble() / numSteps) * 2.0 * Math.PI
                val ox = (Math.cos(angle) * dist).toFloat()
                val oz = (Math.sin(angle) * dist).toFloat()
                val candidatePos = Vec3(
                    Math.round(preferredOrigin.x + ox).toFloat(),
                    preferredOrigin.y,
                    Math.round(preferredOrigin.z + oz).toFloat()
                )
                val cMin = candidatePos - testHalf
                val cMax = candidatePos + testHalf

                val overlaps = occupiedBoxes.any { (bMin, bMax) ->
                    cMin.x < bMax.x && cMax.x > bMin.x &&
                    cMin.z < bMax.z && cMax.z > bMin.z
                }

                if (!overlaps) {
                    return candidatePos
                }
            }
        }

        val maxX = occupiedBoxes.maxOfOrNull { it.second.x } ?: preferredOrigin.x
        return Vec3(maxX + requiredSpan.x * 0.5f + 2f, preferredOrigin.y, preferredOrigin.z)
    }
}
