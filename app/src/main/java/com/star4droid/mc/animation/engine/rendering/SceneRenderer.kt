package com.star4droid.mc.animation.engine.rendering

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.star4droid.mc.animation.engine.camera.EditorCamera
import com.star4droid.mc.animation.engine.gizmo.EditorMode
import com.star4droid.mc.animation.engine.gizmo.GizmoAxis
import com.star4droid.mc.animation.engine.gizmo.GizmoController
import com.star4droid.mc.animation.engine.math.Mat4
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.CharacterPartType
import com.star4droid.mc.animation.engine.scene.TimeOfDay
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SceneRenderer(
    private val context: Context,
    val sceneGraph: SceneGraph,
    val camera: EditorCamera,
    val gizmoController: GizmoController
) : GLSurfaceView.Renderer {

    var selectedNodeId: String? = null
    var currentTimeOfDay: TimeOfDay = TimeOfDay.NOON

    private var shader: Shader? = null
    private var cubeMesh: Mesh? = null
    private var headMesh: Mesh? = null
    private var gridMesh: Mesh? = null
    private var wireframeMesh: Mesh? = null
    private var cameraFrustumMesh: Mesh? = null
    private val textureManager = TextureManager()

    var viewportWidth: Int = 1
        private set
    var viewportHeight: Int = 1
        private set

    // Reusable matrices to prevent GC churn during render loop
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProjMatrix = FloatArray(16)
    val invViewProjMatrix = Mat4()

    private var planeMesh: Mesh? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        shader = Shader(Shader.VERTEX_SHADER_SRC, Shader.FRAGMENT_SHADER_SRC)
        cubeMesh = Geometry.createCubeMesh(1f, 1f, 1f)
        headMesh = Geometry.createHeadMesh(1f, 1f, 1f)
        planeMesh = Geometry.createPlaneMesh(1f, 1f)
        gridMesh = Geometry.createGridMesh(30, 1.0f)
        wireframeMesh = Geometry.createBoundingWireframeMesh(1f, 1f, 1f)
        cameraFrustumMesh = Geometry.createCameraFrustumMesh(60f, 16f / 9f, 2.2f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = shader ?: return
        val cube = cubeMesh ?: return
        val grid = gridMesh ?: return

        // 1. Clear color based on TimeOfDay
        val (bgR, bgG, bgB) = when (currentTimeOfDay) {
            TimeOfDay.MORNING -> Triple(0.72f, 0.78f, 0.88f)
            TimeOfDay.NOON -> Triple(0.48f, 0.65f, 1.0f)
            TimeOfDay.EVENING -> Triple(0.85f, 0.52f, 0.38f)
            TimeOfDay.NIGHT -> Triple(0.06f, 0.08f, 0.14f)
        }
        GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 2. Compute View & Projection
        val aspect = if (viewportHeight > 0) viewportWidth.toFloat() / viewportHeight.toFloat() else 1.0f

        // Check if there is an active scene camera and user switched to Camera view
        val activeCam = sceneGraph.getActiveCamera()
        if (camera.isUsingSceneCamera && activeCam != null) {
            camera.activeSceneCameraMatrix = activeCam.worldMatrix
            activeCam.cameraData?.let { camera.fov = it.fov }
        } else {
            camera.isUsingSceneCamera = false
        }

        val vMat = camera.getViewMatrix()
        val pMat = camera.getProjectionMatrix(aspect)

        System.arraycopy(vMat.values, 0, viewMatrix, 0, 16)
        System.arraycopy(pMat.values, 0, projMatrix, 0, 16)
        Matrix.multiplyMM(viewProjMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Cache inverse ViewProj for touch raycasting
        val vp = Mat4(viewProjMatrix)
        vp.inverted()?.let {
            System.arraycopy(it.values, 0, invViewProjMatrix.values, 0, 16)
        }

        s.use()

        // Light setup
        val sunDir = when (currentTimeOfDay) {
            TimeOfDay.MORNING -> floatArrayOf(0.8f, 0.4f, 0.5f)
            TimeOfDay.NOON -> floatArrayOf(0.3f, 0.9f, 0.3f)
            TimeOfDay.EVENING -> floatArrayOf(-0.8f, 0.3f, 0.4f)
            TimeOfDay.NIGHT -> floatArrayOf(0.2f, 0.5f, 0.8f)
        }
        val sunColor = when (currentTimeOfDay) {
            TimeOfDay.MORNING -> floatArrayOf(1.0f, 0.9f, 0.8f)
            TimeOfDay.NOON -> floatArrayOf(1.0f, 0.98f, 0.92f)
            TimeOfDay.EVENING -> floatArrayOf(1.0f, 0.65f, 0.45f)
            TimeOfDay.NIGHT -> floatArrayOf(0.25f, 0.3f, 0.45f)
        }
        val ambientColor = when (currentTimeOfDay) {
            TimeOfDay.NIGHT -> floatArrayOf(0.12f, 0.14f, 0.2f)
            else -> floatArrayOf(0.35f, 0.35f, 0.38f)
        }

        GLES20.glUniform3fv(s.uLightDir, 1, sunDir, 0)
        GLES20.glUniform3fv(s.uLightColor, 1, sunColor, 0)
        GLES20.glUniform3fv(s.uAmbientColor, 1, ambientColor, 0)
        GLES20.glUniform4f(s.uSelectionColor, 0.2f, 0.8f, 1.0f, 1.0f)

        // Bind dynamic scene lights (Point/Spot)
        val lightNodes = sceneGraph.getAllNodes().filter { it.type == SceneNodeType.LIGHT && it.visible }.take(4)
        GLES20.glUniform1i(s.uNumPointLights, lightNodes.size)
        if (lightNodes.isNotEmpty()) {
            val positions = FloatArray(lightNodes.size * 3)
            val colors = FloatArray(lightNodes.size * 3)
            val intensities = FloatArray(lightNodes.size)
            val ranges = FloatArray(lightNodes.size)

            lightNodes.forEachIndexed { i, lightNode ->
                val p = lightNode.getWorldPosition()
                positions[i * 3] = p.x
                positions[i * 3 + 1] = p.y
                positions[i * 3 + 2] = p.z

                val ld = lightNode.lightData ?: com.star4droid.mc.animation.engine.scene.LightData()
                val cInt = ld.color
                colors[i * 3] = ((cInt shr 16) and 0xFF) / 255f
                colors[i * 3 + 1] = ((cInt shr 8) and 0xFF) / 255f
                colors[i * 3 + 2] = (cInt and 0xFF) / 255f

                intensities[i] = ld.intensity
                ranges[i] = ld.range
            }

            GLES20.glUniform3fv(s.uPointLightPos, lightNodes.size, positions, 0)
            GLES20.glUniform3fv(s.uPointLightColor, lightNodes.size, colors, 0)
            GLES20.glUniform1fv(s.uPointLightIntensity, lightNodes.size, intensities, 0)
            GLES20.glUniform1fv(s.uPointLightRange, lightNodes.size, ranges, 0)
        }

        // 3. Render Ground Grid
        renderGrid(s, grid, viewProjMatrix)

        // 4. Render Scene Graph Nodes
        for (node in sceneGraph.getAllNodes()) {
            if (!node.visible) continue
            val isSelected = (node.id == selectedNodeId)
            renderSceneNode(s, cube, node, viewProjMatrix, isSelected)
        }

        // 5. Render Transform Gizmo for selected object
        val selectedNode = selectedNodeId?.let { sceneGraph.getNode(it) }
        if (selectedNode != null && gizmoController.currentMode != EditorMode.SELECT && !camera.isUsingSceneCamera) {
            renderGizmo(s, cube, selectedNode.getWorldPosition(), viewProjMatrix)
        }
    }

    private fun renderGrid(s: Shader, grid: Mesh, viewProj: FloatArray) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewProj, 0, modelMatrix, 0)

        GLES20.glUniformMatrix4fv(s.uMVPMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(s.uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(s.uObjectColor, 0.4f, 0.45f, 0.5f, 0.6f)
        GLES20.glUniform1f(s.uIsSelected, 0f)
        GLES20.glUniform1f(s.uUseTexture, 0f)

        bindMesh(s, grid)
        GLES20.glDrawElements(GLES20.GL_LINES, grid.indexCount, GLES20.GL_UNSIGNED_SHORT, grid.indexBuffer)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun renderSceneNode(
        s: Shader,
        cube: Mesh,
        node: SceneNode,
        viewProj: FloatArray,
        isSelected: Boolean
    ) {
        when (node.type) {
            SceneNodeType.BLOCK, SceneNodeType.CHARACTER_PART, SceneNodeType.GROUND, SceneNodeType.PLANE -> {
                // Combine node world matrix with local box dimension scaling
                val nodeMat = node.worldMatrix.values
                val dimScale = Mat4.scaling(node.boxDimensions)
                val finalModel = Mat4(nodeMat) * dimScale

                System.arraycopy(finalModel.values, 0, modelMatrix, 0, 16)
                Matrix.multiplyMM(mvpMatrix, 0, viewProj, 0, modelMatrix, 0)

                GLES20.glUniformMatrix4fv(s.uMVPMatrix, 1, false, mvpMatrix, 0)
                GLES20.glUniformMatrix4fv(s.uModelMatrix, 1, false, modelMatrix, 0)

                // Opacity & Color
                val matColor = node.material.color
                val r = ((matColor shr 16) and 0xFF) / 255f
                val g = ((matColor shr 8) and 0xFF) / 255f
                val b = (matColor and 0xFF) / 255f
                GLES20.glUniform4f(s.uObjectColor, r, g, b, node.material.opacity)
                GLES20.glUniform1f(s.uIsSelected, if (isSelected) 1f else 0f)

                // Texture
                val texId = node.material.textureAssetId
                val textureHandle = textureManager.getTexture(texId)
                if (textureHandle != 0) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
                    GLES20.glUniform1i(s.uTexture, 0)
                    GLES20.glUniform1f(s.uUseTexture, 1f)
                } else {
                    GLES20.glUniform1f(s.uUseTexture, 0f)
                }

                val isTransparent = node.material.opacity < 0.98f || node.material.textureAssetId == "glass"
                if (isTransparent) {
                    GLES20.glEnable(GLES20.GL_BLEND)
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                } else {
                    GLES20.glDisable(GLES20.GL_BLEND)
                }

                val meshToDraw = when {
                    node.type == SceneNodeType.PLANE && planeMesh != null -> planeMesh!!
                    node.characterPartType == CharacterPartType.HEAD && headMesh != null -> headMesh!!
                    else -> cube
                }

                bindMesh(s, meshToDraw)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, meshToDraw.indexCount, GLES20.GL_UNSIGNED_SHORT, meshToDraw.indexBuffer)

                if (isTransparent) {
                    GLES20.glDisable(GLES20.GL_BLEND)
                }
            }
            SceneNodeType.CAMERA -> {
                // Do NOT draw camera marker when looking through the scene camera or when in camera mode,
                // because rendering a solid marker at the camera's eye position completely blocks the view.
                if (!camera.isUsingSceneCamera) {
                    val eyePos = camera.getEyePosition()
                    val dist = node.getWorldPosition().distanceTo(eyePos)
                    if (dist > 0.45f) {
                        renderCameraModelAndFrustum(s, cube, cameraFrustumMesh, node, viewProj, isSelected)
                    }
                }
            }
            SceneNodeType.LIGHT -> {
                renderLightIcon(s, cube, node, viewProj, isSelected)
            }
            else -> {}
        }
    }

    private fun renderCameraModelAndFrustum(
        s: Shader,
        cube: Mesh,
        frustum: Mesh?,
        node: SceneNode,
        viewProj: FloatArray,
        isSelected: Boolean
    ) {
        val nodeWorld = node.worldMatrix

        // 1. Camera Body (Dark sleek slate matte chassis)
        val bodyMat = nodeWorld * Mat4.scaling(0.42f, 0.32f, 0.48f)
        drawMeshWithMatrix(s, cube, bodyMat, viewProj, isSelected, 0.16f, 0.20f, 0.26f)

        // 2. Camera Front Lens Barrel (Deep dark barrel extending forward)
        val lensMat = nodeWorld * Mat4.translation(Vec3(0f, 0f, -0.32f)) * Mat4.scaling(0.24f, 0.24f, 0.22f)
        drawMeshWithMatrix(s, cube, lensMat, viewProj, isSelected, 0.08f, 0.10f, 0.14f)

        // 3. Lens Front Ring Accent (Luminous Cyan accent ring)
        val ringMat = nodeWorld * Mat4.translation(Vec3(0f, 0f, -0.44f)) * Mat4.scaling(0.26f, 0.26f, 0.04f)
        drawMeshWithMatrix(s, cube, ringMat, viewProj, isSelected, 0.22f, 0.82f, 1.0f)

        // 4. Top Film Reels / Viewfinder (Two vintage spool cylinders on top)
        val reel1Mat = nodeWorld * Mat4.translation(Vec3(-0.09f, 0.22f, -0.06f)) * Mat4.scaling(0.16f, 0.12f, 0.16f)
        drawMeshWithMatrix(s, cube, reel1Mat, viewProj, isSelected, 0.28f, 0.34f, 0.44f)

        val reel2Mat = nodeWorld * Mat4.translation(Vec3(0.09f, 0.22f, 0.10f)) * Mat4.scaling(0.14f, 0.10f, 0.14f)
        drawMeshWithMatrix(s, cube, reel2Mat, viewProj, isSelected, 0.28f, 0.34f, 0.44f)

        // 5. Perspective Frustum Lines (Wireframe FOV view cone radiating into 3D scene)
        if (frustum != null) {
            val frustumMat = nodeWorld * Mat4.translation(Vec3(0f, 0f, -0.44f))
            System.arraycopy(frustumMat.values, 0, modelMatrix, 0, 16)
            Matrix.multiplyMM(mvpMatrix, 0, viewProj, 0, modelMatrix, 0)

            GLES20.glUniformMatrix4fv(s.uMVPMatrix, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(s.uModelMatrix, 1, false, modelMatrix, 0)
            val lineR = if (isSelected) 1.0f else 0.22f
            val lineG = if (isSelected) 0.85f else 0.82f
            val lineB = if (isSelected) 0.2f else 1.0f
            GLES20.glUniform4f(s.uObjectColor, lineR, lineG, lineB, 0.85f)
            GLES20.glUniform1f(s.uIsSelected, if (isSelected) 1f else 0f)
            GLES20.glUniform1f(s.uUseTexture, 0f)

            bindMesh(s, frustum)
            GLES20.glLineWidth(2.5f)
            GLES20.glDrawElements(GLES20.GL_LINES, frustum.indexCount, GLES20.GL_UNSIGNED_SHORT, frustum.indexBuffer)
        }
    }

    private fun drawMeshWithMatrix(
        s: Shader,
        mesh: Mesh,
        matrix: Mat4,
        viewProj: FloatArray,
        isSelected: Boolean,
        r: Float, g: Float, b: Float
    ) {
        System.arraycopy(matrix.values, 0, modelMatrix, 0, 16)
        Matrix.multiplyMM(mvpMatrix, 0, viewProj, 0, modelMatrix, 0)

        GLES20.glUniformMatrix4fv(s.uMVPMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(s.uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(s.uObjectColor, r, g, b, 1.0f)
        GLES20.glUniform1f(s.uIsSelected, if (isSelected) 1f else 0f)
        GLES20.glUniform1f(s.uUseTexture, 0f)

        bindMesh(s, mesh)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)
    }

    private fun renderLightIcon(
        s: Shader,
        cube: Mesh,
        node: SceneNode,
        viewProj: FloatArray,
        isSelected: Boolean
    ) {
        val lightData = node.lightData ?: com.star4droid.mc.animation.engine.scene.LightData()
        val pos = node.getWorldPosition()
        val cInt = lightData.color
        val r = ((cInt shr 16) and 0xFF) / 255f
        val g = ((cInt shr 8) and 0xFF) / 255f
        val b = (cInt and 0xFF) / 255f

        val nodeWorld = node.worldMatrix

        when (lightData.lightType) {
            com.star4droid.mc.animation.engine.scene.LightType.SUN -> {
                // Sun Light Icon: Glowing central core + 4 diagonal rays
                val coreMat = nodeWorld * Mat4.scaling(0.32f, 0.32f, 0.32f)
                drawMeshWithMatrix(s, cube, coreMat, viewProj, isSelected, 1.0f, 0.9f, 0.2f)

                // Cross Rays
                val ray1Mat = nodeWorld * Mat4.scaling(0.55f, 0.08f, 0.08f)
                drawMeshWithMatrix(s, cube, ray1Mat, viewProj, isSelected, 1.0f, 0.8f, 0.1f)
                val ray2Mat = nodeWorld * Mat4.scaling(0.08f, 0.55f, 0.08f)
                drawMeshWithMatrix(s, cube, ray2Mat, viewProj, isSelected, 1.0f, 0.8f, 0.1f)
            }
            com.star4droid.mc.animation.engine.scene.LightType.POINT -> {
                // Point Light Icon: Glowing sphere bulb + base ring
                val bulbMat = nodeWorld * Mat4.scaling(0.28f, 0.28f, 0.28f)
                drawMeshWithMatrix(s, cube, bulbMat, viewProj, isSelected, r, g, b)

                val baseMat = nodeWorld * Mat4.translation(Vec3(0f, -0.18f, 0f)) * Mat4.scaling(0.12f, 0.12f, 0.12f)
                drawMeshWithMatrix(s, cube, baseMat, viewProj, isSelected, 0.3f, 0.3f, 0.35f)
            }
            com.star4droid.mc.animation.engine.scene.LightType.SPOT -> {
                // Spot Light Icon: Spotlight housing + front lens glowing cone
                val housingMat = nodeWorld * Mat4.scaling(0.26f, 0.26f, 0.35f)
                drawMeshWithMatrix(s, cube, housingMat, viewProj, isSelected, 0.2f, 0.22f, 0.28f)

                val lensMat = nodeWorld * Mat4.translation(Vec3(0f, 0f, -0.22f)) * Mat4.scaling(0.28f, 0.28f, 0.06f)
                drawMeshWithMatrix(s, cube, lensMat, viewProj, isSelected, r, g, b)
            }
        }
    }

    private fun renderMarker(
        s: Shader,
        cube: Mesh,
        pos: Vec3,
        viewProj: FloatArray,
        isSelected: Boolean,
        r: Float, g: Float, b: Float
    ) {
        val m = Mat4.translation(pos) * Mat4.scaling(0.28f, 0.28f, 0.28f)
        System.arraycopy(m.values, 0, modelMatrix, 0, 16)
        Matrix.multiplyMM(mvpMatrix, 0, viewProj, 0, modelMatrix, 0)

        GLES20.glUniformMatrix4fv(s.uMVPMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(s.uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(s.uObjectColor, r, g, b, 1.0f)
        GLES20.glUniform1f(s.uIsSelected, if (isSelected) 1f else 0f)
        GLES20.glUniform1f(s.uUseTexture, 0f)

        bindMesh(s, cube)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cube.indexCount, GLES20.GL_UNSIGNED_SHORT, cube.indexBuffer)
    }

    private fun renderGizmo(s: Shader, cube: Mesh, position: Vec3, viewProj: FloatArray) {
        // Disable depth test momentarily or draw on top
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)

        val camPos = camera.getEyePosition()
        val dist = position.distanceTo(camPos)
        val scale = (dist * 0.12f).coerceAtLeast(0.4f)

        val activeAxis = gizmoController.activeAxis

        // Center Cube
        drawGizmoBox(
            s, cube, position, Vec3.ZERO,
            Vec3(0.12f, 0.12f, 0.12f) * scale,
            if (activeAxis == GizmoAxis.CENTER) 1f else 0.9f,
            if (activeAxis == GizmoAxis.CENTER) 1f else 0.9f,
            if (activeAxis == GizmoAxis.CENTER) 0.3f else 0.9f,
            viewProj
        )

        // X Arrow (Red)
        val xLen = 1.2f * scale
        val xThick = 0.06f * scale
        drawGizmoBox(
            s, cube, position + Vec3(xLen * 0.5f, 0f, 0f), Vec3.ZERO,
            Vec3(xLen, xThick, xThick),
            1f, if (activeAxis == GizmoAxis.X) 0.8f else 0.2f, if (activeAxis == GizmoAxis.X) 0.8f else 0.2f,
            viewProj
        )

        // Y Arrow (Green)
        val yLen = 1.2f * scale
        val yThick = 0.06f * scale
        drawGizmoBox(
            s, cube, position + Vec3(0f, yLen * 0.5f, 0f), Vec3.ZERO,
            Vec3(yThick, yLen, yThick),
            if (activeAxis == GizmoAxis.Y) 0.8f else 0.2f, 1f, if (activeAxis == GizmoAxis.Y) 0.8f else 0.2f,
            viewProj
        )

        // Z Arrow (Blue)
        val zLen = 1.2f * scale
        val zThick = 0.06f * scale
        drawGizmoBox(
            s, cube, position + Vec3(0f, 0f, zLen * 0.5f), Vec3.ZERO,
            Vec3(xThick, xThick, zLen),
            if (activeAxis == GizmoAxis.Z) 0.8f else 0.2f, if (activeAxis == GizmoAxis.Z) 0.8f else 0.3f, 1f,
            viewProj
        )
    }

    private fun drawGizmoBox(
        s: Shader,
        cube: Mesh,
        pos: Vec3,
        rot: Vec3,
        size: Vec3,
        r: Float, g: Float, b: Float,
        viewProj: FloatArray
    ) {
        val m = Mat4.translation(pos) * Mat4.rotationEuler(rot) * Mat4.scaling(size)
        System.arraycopy(m.values, 0, modelMatrix, 0, 16)
        Matrix.multiplyMM(mvpMatrix, 0, viewProj, 0, modelMatrix, 0)

        GLES20.glUniformMatrix4fv(s.uMVPMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(s.uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(s.uObjectColor, r, g, b, 1.0f)
        GLES20.glUniform1f(s.uIsSelected, 0f)
        GLES20.glUniform1f(s.uUseTexture, 0f)

        bindMesh(s, cube)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cube.indexCount, GLES20.GL_UNSIGNED_SHORT, cube.indexBuffer)
    }

    private fun bindMesh(s: Shader, mesh: Mesh) {
        mesh.vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(s.aPosition, 3, GLES20.GL_FLOAT, false, 8 * 4, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(s.aPosition)

        mesh.vertexBuffer.position(3)
        GLES20.glVertexAttribPointer(s.aNormal, 3, GLES20.GL_FLOAT, false, 8 * 4, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(s.aNormal)

        mesh.vertexBuffer.position(6)
        GLES20.glVertexAttribPointer(s.aTexCoord, 2, GLES20.GL_FLOAT, false, 8 * 4, mesh.vertexBuffer)
        GLES20.glEnableVertexAttribArray(s.aTexCoord)
    }
}
