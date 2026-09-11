package com.star4droid.mc.animation.engine.history

import com.star4droid.mc.animation.animation.AnimationTrack
import com.star4droid.mc.animation.animation.Keyframe
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.Transform

interface ActionCommand {
    val description: String
    fun execute()
    fun undo()
}

class HistoryManager(private val maxHistory: Int = 50) {
    private val undoStack = ArrayDeque<ActionCommand>()
    private val redoStack = ArrayDeque<ActionCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun executeCommand(command: ActionCommand) {
        command.execute()
        undoStack.addLast(command)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val cmd = undoStack.removeLast()
        cmd.undo()
        redoStack.addLast(cmd)
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val cmd = redoStack.removeLast()
        cmd.execute()
        undoStack.addLast(cmd)
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

class TransformCommand(
    private val sceneGraph: SceneGraph,
    private val nodeId: String,
    private val oldTransform: Transform,
    private val newTransform: Transform
) : ActionCommand {
    override val description: String = "Transform Object"

    override fun execute() {
        sceneGraph.getNode(nodeId)?.let {
            it.baseTransform = newTransform.copyTransform()
            it.animatedTransform = newTransform.copyTransform()
            sceneGraph.updateWorldMatrices()
        }
    }

    override fun undo() {
        sceneGraph.getNode(nodeId)?.let {
            it.baseTransform = oldTransform.copyTransform()
            it.animatedTransform = oldTransform.copyTransform()
            sceneGraph.updateWorldMatrices()
        }
    }
}

class AddNodeCommand(
    private val sceneGraph: SceneGraph,
    private val node: SceneNode,
    private val parentId: String?
) : ActionCommand {
    override val description: String = "Add ${node.name}"

    override fun execute() {
        sceneGraph.addNode(node, parentId)
    }

    override fun undo() {
        sceneGraph.removeNode(node.id)
    }
}

class RemoveNodeCommand(
    private val sceneGraph: SceneGraph,
    private val node: SceneNode,
    private val parentId: String?
) : ActionCommand {
    override val description: String = "Remove ${node.name}"

    override fun execute() {
        sceneGraph.removeNode(node.id)
    }

    override fun undo() {
        sceneGraph.addNode(node, parentId)
    }
}

class AddKeyframeCommand(
    private val timeline: TimelineAsset,
    private val targetObjectId: String,
    private val propertyPath: String,
    private val keyframe: Keyframe
) : ActionCommand {
    override val description: String = "Add Keyframe"

    override fun execute() {
        val track = timeline.getOrCreateTrack(targetObjectId, propertyPath)
        track.addOrUpdateKeyframe(keyframe.time, keyframe.value, keyframe.interpolation)
    }

    override fun undo() {
        val track = timeline.getTrack(targetObjectId, propertyPath)
        track?.removeKeyframe(keyframe.id)
    }
}
