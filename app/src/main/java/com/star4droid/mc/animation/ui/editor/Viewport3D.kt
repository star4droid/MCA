package com.star4droid.mc.animation.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.star4droid.mc.animation.engine.camera.EditorCamera
import com.star4droid.mc.animation.engine.gizmo.EditorMode
import com.star4droid.mc.animation.engine.gizmo.GizmoAxis
import com.star4droid.mc.animation.engine.gizmo.GizmoController
import com.star4droid.mc.animation.engine.math.Ray
import com.star4droid.mc.animation.engine.rendering.SceneRenderer
import com.star4droid.mc.animation.engine.scene.SceneGraph
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
@Composable
fun Viewport3D(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glView = remember {
        EditorGLSurfaceView(
            context = context,
            sceneGraph = viewModel.sceneGraph,
            camera = viewModel.camera,
            gizmoController = viewModel.gizmoController,
            onSelectNode = { nodeId -> viewModel.selectNode(nodeId) },
            onGizmoTransformChanged = {
                viewModel.selectNode(viewModel.uiState.value.selectedNodeId)
            }
        ).also {
            viewModel.renderer = it.renderer
        }
    }

    DisposableEffect(Unit) {
        glView.onResume()
        onDispose {
            glView.onPause()
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier.fillMaxSize()
    )
}

@SuppressLint("ViewConstructor")
class EditorGLSurfaceView(
    context: Context,
    val sceneGraph: SceneGraph,
    val camera: EditorCamera,
    val gizmoController: GizmoController,
    val onSelectNode: (String?) -> Unit,
    val onGizmoTransformChanged: () -> Unit
) : GLSurfaceView(context) {

    val renderer: SceneRenderer

    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var isDraggingGizmo = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            camera.zoom(1.0f / detector.scaleFactor)
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        renderer = SceneRenderer(context, sceneGraph, camera, gizmoController)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) return true

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = x
                previousY = y

                // Raycast to check if gizmo or object was hit
                val ray = Ray.fromScreen(
                    screenX = x,
                    screenY = y,
                    viewportWidth = renderer.viewportWidth.toFloat(),
                    viewportHeight = renderer.viewportHeight.toFloat(),
                    invViewProj = renderer.invViewProjMatrix
                )

                val selectedNode = renderer.selectedNodeId?.let { sceneGraph.getNode(it) }
                if (selectedNode != null && gizmoController.currentMode != EditorMode.SELECT && !camera.isUsingSceneCamera) {
                    val axis = gizmoController.checkAxisHit(ray, selectedNode.getWorldPosition())
                    if (axis != GizmoAxis.NONE) {
                        gizmoController.startDrag(selectedNode, axis, ray)
                        isDraggingGizmo = true
                        return true
                    }
                }

                // Check hit against scene nodes for selection
                var closestNodeId: String? = null
                var closestDist = Float.POSITIVE_INFINITY

                for (node in sceneGraph.getAllNodes()) {
                    if (!node.visible) continue
                    val (minB, maxB) = node.getWorldAABB()
                    val t = ray.intersectAABB(minB, maxB)
                    if (t != null && t < closestDist) {
                        closestDist = t
                        closestNodeId = node.id
                    }
                }

                if (closestNodeId != null) {
                    onSelectNode(closestNodeId)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - previousX
                val dy = y - previousY

                if (isDraggingGizmo) {
                    val ray = Ray.fromScreen(
                        screenX = x,
                        screenY = y,
                        viewportWidth = renderer.viewportWidth.toFloat(),
                        viewportHeight = renderer.viewportHeight.toFloat(),
                        invViewProj = renderer.invViewProjMatrix
                    )
                    val selectedNode = renderer.selectedNodeId?.let { sceneGraph.getNode(it) }
                    if (selectedNode != null) {
                        gizmoController.updateDrag(selectedNode, ray)
                        onGizmoTransformChanged()
                    }
                } else if (event.pointerCount == 1) {
                    // Orbit Editor Camera
                    if (!camera.isUsingSceneCamera) {
                        camera.orbit(dx * 0.35f, dy * 0.35f)
                    }
                } else if (event.pointerCount == 2) {
                    // Pan Editor Camera
                    if (!camera.isUsingSceneCamera) {
                        camera.pan(dx, dy)
                    }
                }

                previousX = x
                previousY = y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingGizmo) {
                    val selectedNode = renderer.selectedNodeId?.let { sceneGraph.getNode(it) }
                    if (selectedNode != null) {
                        gizmoController.endDrag(selectedNode)
                        onGizmoTransformChanged()
                    }
                    isDraggingGizmo = false
                }
            }
        }
        return true
    }
}
