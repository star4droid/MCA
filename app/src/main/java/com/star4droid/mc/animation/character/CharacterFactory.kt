package com.star4droid.mc.animation.character

import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.CharacterPartType
import com.star4droid.mc.animation.engine.scene.Material
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.Transform
import java.util.UUID

object CharacterFactory {

    fun createCharacter(
        name: String = "Steve",
        isAlex: Boolean = false,
        skinId: String = if (isAlex) "alex" else "steve",
        startPosition: Vec3 = Vec3(0f, 0f, 0f)
    ): List<SceneNode> {
        val rootId = UUID.randomUUID().toString()
        val bodyId = UUID.randomUUID().toString()
        val headId = UUID.randomUUID().toString()
        val rightArmId = UUID.randomUUID().toString()
        val leftArmId = UUID.randomUUID().toString()
        val rightLegId = UUID.randomUUID().toString()
        val leftLegId = UUID.randomUUID().toString()

        val armWidth = if (isAlex) 0.2f else 0.25f
        val shoulderOffset = if (isAlex) 0.325f else 0.375f

        // Root node
        val rootNode = SceneNode(
            id = rootId,
            name = name,
            type = SceneNodeType.CHARACTER_ROOT,
            parentId = null,
            children = mutableListOf(bodyId, leftLegId, rightLegId),
            baseTransform = Transform(position = startPosition),
            animatedTransform = Transform(position = startPosition),
            characterPartType = CharacterPartType.ROOT,
            characterSkinId = skinId
        )

        // Body Node (Torso)
        val bodyNode = SceneNode(
            id = bodyId,
            name = "$name Body",
            type = SceneNodeType.CHARACTER_PART,
            parentId = rootId,
            children = mutableListOf(headId, leftArmId, rightArmId),
            baseTransform = Transform(
                position = Vec3(0f, 1.125f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(0f, 1.125f, 0f)
            ),
            material = Material(textureAssetId = "${skinId}_body", color = 0xFFFFFFFF.toInt()),
            characterPartType = CharacterPartType.BODY,
            boxDimensions = Vec3(0.5f, 0.75f, 0.25f),
            characterSkinId = skinId
        )

        // Head Node
        val headNode = SceneNode(
            id = headId,
            name = "$name Head",
            type = SceneNodeType.CHARACTER_PART,
            parentId = bodyId,
            children = mutableListOf(),
            baseTransform = Transform(
                position = Vec3(0f, 0.625f, 0f),
                pivot = Vec3(0f, -0.25f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(0f, 0.625f, 0f),
                pivot = Vec3(0f, -0.25f, 0f)
            ),
            material = Material(textureAssetId = "${skinId}_head", color = 0xFFFFFFFF.toInt()),
            characterPartType = CharacterPartType.HEAD,
            boxDimensions = Vec3(0.5f, 0.5f, 0.5f),
            characterSkinId = skinId
        )

        // Right Arm (Single continuous limb with shoulder pivot)
        val rightArmNode = SceneNode(
            id = rightArmId,
            name = "$name Right Arm",
            type = SceneNodeType.CHARACTER_PART,
            parentId = bodyId,
            children = mutableListOf(),
            baseTransform = Transform(
                position = Vec3(-shoulderOffset, 0.1875f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(-shoulderOffset, 0.1875f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            material = Material(textureAssetId = "${skinId}_arm_right", color = 0xFFFFFFFF.toInt()),
            characterPartType = CharacterPartType.RIGHT_ARM,
            boxDimensions = Vec3(armWidth, 0.75f, 0.25f),
            characterSkinId = skinId
        )

        // Left Arm (Single continuous limb with shoulder pivot)
        val leftArmNode = SceneNode(
            id = leftArmId,
            name = "$name Left Arm",
            type = SceneNodeType.CHARACTER_PART,
            parentId = bodyId,
            children = mutableListOf(),
            baseTransform = Transform(
                position = Vec3(shoulderOffset, 0.1875f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(shoulderOffset, 0.1875f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            material = Material(textureAssetId = "${skinId}_arm_left", color = 0xFFFFFFFF.toInt()),
            characterPartType = CharacterPartType.LEFT_ARM,
            boxDimensions = Vec3(armWidth, 0.75f, 0.25f),
            characterSkinId = skinId
        )

        // Right Leg (Single continuous limb with hip pivot)
        val rightLegNode = SceneNode(
            id = rightLegId,
            name = "$name Right Leg",
            type = SceneNodeType.CHARACTER_PART,
            parentId = rootId,
            children = mutableListOf(),
            baseTransform = Transform(
                position = Vec3(-0.125f, 0.375f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(-0.125f, 0.375f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            material = Material(textureAssetId = "${skinId}_leg_right", color = 0xFFFFFFFF.toInt()),
            characterPartType = CharacterPartType.RIGHT_LEG,
            boxDimensions = Vec3(0.25f, 0.75f, 0.25f),
            characterSkinId = skinId
        )

        // Left Leg (Single continuous limb with hip pivot)
        val leftLegNode = SceneNode(
            id = leftLegId,
            name = "$name Left Leg",
            type = SceneNodeType.CHARACTER_PART,
            parentId = rootId,
            children = mutableListOf(),
            baseTransform = Transform(
                position = Vec3(0.125f, 0.375f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            animatedTransform = Transform(
                position = Vec3(0.125f, 0.375f, 0f),
                pivot = Vec3(0f, 0.375f, 0f)
            ),
            material = Material(textureAssetId = "${skinId}_leg_left", color = 0xFFFFFFFF.toInt()),
            characterPartType = CharacterPartType.LEFT_LEG,
            boxDimensions = Vec3(0.25f, 0.75f, 0.25f),
            characterSkinId = skinId
        )

        return listOf(
            rootNode, bodyNode, headNode,
            rightArmNode, leftArmNode,
            rightLegNode, leftLegNode
        )
    }

    fun addCharacterToScene(
        sceneGraph: SceneGraph,
        name: String = "Steve",
        isAlex: Boolean = false,
        skinId: String = if (isAlex) "alex" else "steve",
        position: Vec3 = Vec3(0f, 0f, 0f)
    ): String {
        val parts = createCharacter(name, isAlex, skinId, position)
        for (part in parts) {
            sceneGraph.nodes[part.id] = part
            if (part.parentId == null) {
                sceneGraph.rootNodeIds.add(part.id)
            }
        }
        sceneGraph.updateWorldMatrices()
        return parts.first().id
    }
}
