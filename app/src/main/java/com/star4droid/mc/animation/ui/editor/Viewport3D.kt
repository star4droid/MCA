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
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.rendering.SceneRenderer
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.ui.world.WorldBuildingTool
import kotlin.math.abs
import kotlin.math.hypot

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
            viewModel = viewModel,
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
    val viewModel: EditorViewModel,
    val sceneGraph: SceneGraph,
    val camera: EditorCamera,
    val gizmoController: GizmoController,
    val onSelectNode: (String?) -> Unit,
    val onGizmoTransformChanged: () -> Unit
) : GLSurfaceView(context) {

    val renderer: SceneRenderer

    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private var hasMoved = false
    private var wasMultiTouch = false
    private var isDraggingGizmo = false
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var isScaling = false
    private var skipNextDrag = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            wasMultiTouch = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            camera.zoom(1.0f / detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            skipNextDrag = true
            wasMultiTouch = true
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
        if (scaleDetector.isInProgress || isScaling) {
            skipNextDrag = true
            wasMultiTouch = true
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                previousX = event.x
                previousY = event.y
                touchStartX = event.x
                touchStartY = event.y
                hasMoved = false
                wasMultiTouch = false
                skipNextDrag = false

                // If gizmo is active, check gizmo drag hit
                val ray = Ray.fromScreen(
                    screenX = event.x,
                    screenY = event.y,
                    viewportWidth = renderer.viewportWidth.toFloat(),
                    viewportHeight = renderer.viewportHeight.toFloat(),
                    invViewProj = renderer.invViewProjMatrix
                )

                val selectedNode = renderer.selectedNodeId?.let { sceneGraph.getNode(it) }
                if (selectedNode != null && gizmoController.currentMode != EditorMode.SELECT && !camera.isUsingSceneCamera && !viewModel.uiState.value.isWorldBuildingMode) {
                    val axis = gizmoController.checkAxisHit(ray, selectedNode.getWorldPosition())
                    if (axis != GizmoAxis.NONE) {
                        gizmoController.startDrag(selectedNode, axis, ray)
                        isDraggingGizmo = true
                        return true
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                wasMultiTouch = true
                skipNextDrag = true
                val index = event.actionIndex
                previousX = event.getX(index)
                previousY = event.getY(index)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                wasMultiTouch = true
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    if (newPointerIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newPointerIndex)
                        previousX = event.getX(newPointerIndex)
                        previousY = event.getY(newPointerIndex)
                    }
                }
                skipNextDrag = true
            }

            MotionEvent.ACTION_MOVE -> {
                val dxTotal = hypot(event.x - touchStartX, event.y - touchStartY)
                if (dxTotal > 15f) {
                    hasMoved = true
                }

                if (skipNextDrag) {
                    skipNextDrag = false
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        previousX = event.getX(pointerIndex)
                        previousY = event.getY(pointerIndex)
                    } else {
                        previousX = event.x
                        previousY = event.y
                    }
                    return true
                }

                val pointerIndex = event.findPointerIndex(activePointerId)
                val currentX = if (pointerIndex != -1) event.getX(pointerIndex) else event.x
                val currentY = if (pointerIndex != -1) event.getY(pointerIndex) else event.y

                val dx = currentX - previousX
                val dy = currentY - previousY

                previousX = currentX
                previousY = currentY

                if (isDraggingGizmo) {
                    val ray = Ray.fromScreen(
                        screenX = currentX,
                        screenY = currentY,
                        viewportWidth = renderer.viewportWidth.toFloat(),
                        viewportHeight = renderer.viewportHeight.toFloat(),
                        invViewProj = renderer.invViewProjMatrix
                    )
                    val selectedNode = renderer.selectedNodeId?.let { sceneGraph.getNode(it) }
                    if (selectedNode != null) {
                        gizmoController.updateDrag(selectedNode, ray)
                        onGizmoTransformChanged()
                    }
                } else if (event.pointerCount == 1 && !wasMultiTouch) {
                    // Orbit Editor Camera - suppressed after pinch release to prevent rotation shift
                    if (abs(dx) < 100f && abs(dy) < 100f) {
                        if (!camera.isUsingSceneCamera) {
                            camera.orbit(dx * 0.35f, dy * 0.35f)
                        }
                    }
                } else if (event.pointerCount == 2) {
                    // Pan Editor Camera
                    if (abs(dx) < 100f && abs(dy) < 100f) {
                        if (!camera.isUsingSceneCamera) {
                            camera.pan(dx, dy)
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                skipNextDrag = false

                if (isDraggingGizmo) {
                    val selectedNode = renderer.selectedNodeId?.let { sceneGraph.getNode(it) }
                    if (selectedNode != null) {
                        gizmoController.endDrag(selectedNode)
                        onGizmoTransformChanged()
                    }
                    isDraggingGizmo = false
                } else if (!hasMoved && !wasMultiTouch) {
                    // User click/tap detected
                    val ray = Ray.fromScreen(
                        screenX = event.x,
                        screenY = event.y,
                        viewportWidth = renderer.viewportWidth.toFloat(),
                        viewportHeight = renderer.viewportHeight.toFloat(),
                        invViewProj = renderer.invViewProjMatrix
                    )

                    var closestNode: SceneNode? = null
                    var closestDist = Float.POSITIVE_INFINITY

                    for (node in sceneGraph.getAllNodes()) {
                        if (!node.visible) continue
                        val (minB, maxB) = node.getWorldAABB()
                        val t = ray.intersectAABB(minB, maxB)
                        if (t != null && t < closestDist) {
                            closestDist = t
                            closestNode = node
                        }
                    }

                    val uiState = viewModel.uiState.value
                    if (uiState.isWorldBuildingMode) {
                        when (uiState.worldBuildingTool) {
                            WorldBuildingTool.REMOVE -> {
                                if (closestNode != null) {
                                    viewModel.removeBlock(closestNode.id)
                                }
                            }
                            WorldBuildingTool.SELECT -> {
                                if (closestNode != null) {
                                    viewModel.selectNode(closestNode.id)
                                    viewModel.setWorldBuildingParent(closestNode.id)
                                } else {
                                    viewModel.setWorldBuildingParent(null)
                                }
                            }
                            WorldBuildingTool.ADD -> {
                                if (closestNode != null) {
                                    // Place adjacent to clicked face using precise AABB bounds
                                    val (minB, maxB) = closestNode.getWorldAABB()
                                    val boxCenter = (minB + maxB) * 0.5f
                                    val boxExtents = maxB - minB
                                    val hitPoint = ray.origin + ray.direction * closestDist
                                    val rel = hitPoint - boxCenter

                                    val normRelX = if (boxExtents.x > 0f) rel.x / (boxExtents.x * 0.5f) else 0f
                                    val normRelY = if (boxExtents.y > 0f) rel.y / (boxExtents.y * 0.5f) else 0f
                                    val normRelZ = if (boxExtents.z > 0f) rel.z / (boxExtents.z * 0.5f) else 0f

                                    val absX = abs(normRelX)
                                    val absY = abs(normRelY)
                                    val absZ = abs(normRelZ)

                                    val normal = when {
                                        absY >= absX && absY >= absZ -> Vec3(0f, if (normRelY > 0) 1f else -1f, 0f)
                                        absX >= absZ -> Vec3(if (normRelX > 0) 1f else -1f, 0f, 0f)
                                        else -> Vec3(0f, 0f, if (normRelZ > 0) 1f else -1f)
                                    }

                                    val targetPoint = hitPoint + normal * 0.5f
                                    val placePos = Vec3(
                                        Math.round(targetPoint.x).toFloat(),
                                        Math.round(targetPoint.y).toFloat().coerceAtLeast(0f),
                                        Math.round(targetPoint.z).toFloat()
                                    )
                                    viewModel.addBlockAt(placePos)
                                } else {
                                    // Place on ground plane at y = 0
                                    if (abs(ray.direction.y) > 0.0001f) {
                                        val t = -ray.origin.y / ray.direction.y
                                        if (t > 0f) {
                                            val hit = ray.origin + ray.direction * t
                                            val placePos = Vec3(
                                                Math.round(hit.x).toFloat(),
                                                0f,
                                                Math.round(hit.z).toFloat()
                                            )
                                            viewModel.addBlockAt(placePos)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Normal Editor mode: select tapped node or deselect
                        onSelectNode(closestNode?.id)
                    }
                }

                hasMoved = false
                wasMultiTouch = false
            }
        }
        return true
    }
}
