package com.star4droid.mc.animation

import com.star4droid.mc.animation.animation.AnimationEvaluator
import com.star4droid.mc.animation.animation.Interpolation
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.animation.TimelineInstance
import com.star4droid.mc.animation.character.CharacterFactory
import com.star4droid.mc.animation.engine.math.Mat4
import com.star4droid.mc.animation.engine.math.Ray
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationEngineUnitTest {

    @Test
    fun testVectorAndMatrixMath() {
        val v1 = Vec3(1f, 2f, 3f)
        val v2 = Vec3(4f, 5f, 6f)
        val vSum = v1 + v2
        assertEquals(5f, vSum.x, 1e-5f)
        assertEquals(7f, vSum.y, 1e-5f)
        assertEquals(9f, vSum.z, 1e-5f)

        val matTrans = Mat4.translation(10f, 20f, 30f)
        val transformed = matTrans.transformPoint(Vec3.ZERO)
        assertEquals(10f, transformed.x, 1e-5f)
        assertEquals(20f, transformed.y, 1e-5f)
        assertEquals(30f, transformed.z, 1e-5f)
    }

    @Test
    fun testRaycastAABB() {
        val ray = Ray(origin = Vec3(0f, 0f, 5f), direction = Vec3(0f, 0f, -1f))
        val min = Vec3(-1f, -1f, -1f)
        val max = Vec3(1f, 1f, 1f)
        val hitDistance = ray.intersectAABB(min, max)

        assertNotNull(hitDistance)
        assertEquals(4f, hitDistance!!, 1e-4f)
    }

    @Test
    fun testCharacterRigHierarchy() {
        val scene = SceneGraph()
        val rootId = CharacterFactory.addCharacterToScene(scene, "Steve", false, "steve", Vec3.ZERO)
        val root = scene.getNode(rootId)

        assertNotNull(root)
        assertTrue(root!!.children.isNotEmpty())
        assertTrue(scene.nodes.size >= 7) // root, body, head, arms, legs
    }

    @Test
    fun testAnimationKeyframeEvaluation() {
        val scene = SceneGraph()
        val node = SceneNode(
            id = "test_node",
            name = "Test Cube",
            type = SceneNodeType.BLOCK,
            baseTransform = Transform(position = Vec3(0f, 0f, 0f)),
            animatedTransform = Transform(position = Vec3(0f, 0f, 0f))
        )
        scene.addNode(node)

        val timeline = TimelineAsset(name = "Test Timeline", duration = 5f)
        val track = timeline.getOrCreateTrack("test_node", "transform.position.y")
        track.addOrUpdateKeyframe(0f, 0f, Interpolation.LINEAR)
        track.addOrUpdateKeyframe(2f, 10f, Interpolation.LINEAR)

        val instances = listOf(TimelineInstance(timelineAssetId = timeline.id))

        // Test at time 0
        AnimationEvaluator.evaluate(scene, listOf(timeline), instances, 0f)
        assertEquals(0f, node.animatedTransform.position.y, 1e-4f)

        // Test at time 1.0 (midway) -> y = 5.0
        AnimationEvaluator.evaluate(scene, listOf(timeline), instances, 1.0f)
        assertEquals(5f, node.animatedTransform.position.y, 1e-4f)

        // Test at time 2.0 -> y = 10.0
        AnimationEvaluator.evaluate(scene, listOf(timeline), instances, 2.0f)
        assertEquals(10f, node.animatedTransform.position.y, 1e-4f)
    }
}
