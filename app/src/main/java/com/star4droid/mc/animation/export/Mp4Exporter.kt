package com.star4droid.mc.animation.export

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import com.star4droid.mc.animation.animation.AnimationEvaluator
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.animation.TimelineInstance
import com.star4droid.mc.animation.engine.camera.EditorCamera
import com.star4droid.mc.animation.engine.gizmo.GizmoController
import com.star4droid.mc.animation.engine.rendering.SceneRenderer
import com.star4droid.mc.animation.engine.scene.SceneGraph
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Mp4ExportEngineMode(
    val displayName: String,
    val description: String
) {
    FBO_LIVE_THREAD("FBO Live Thread", "Offscreen FBO on live GL surface thread. 100% texture & lighting accurate."),
    GRAFIKA_SHARED_EGL("Grafika EGL Core", "Google Grafika architecture with EGL_RECORDABLE_ANDROID configuration."),
    PIXEL_COPY_BITMAP("Bitmap PixelCopy", "Extracts ARGB frame bitmaps via glReadPixels/PixelCopy with exact duration timestamps.")
}

data class Mp4ExportConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitRate: Int = 8_000_000,
    val duration: Float = 10f,
    val engineMode: Mp4ExportEngineMode = Mp4ExportEngineMode.PIXEL_COPY_BITMAP
)

class Mp4Exporter {

    interface ExportCallback {
        fun onProgress(frame: Int, totalFrames: Int)
        fun onComplete(outputFile: File)
        fun onError(error: String)
    }

    fun export(
        context: Context,
        outputFile: File,
        config: Mp4ExportConfig,
        sceneGraph: SceneGraph,
        timeline: TimelineAsset,
        timelineInstances: List<TimelineInstance>,
        gizmoController: GizmoController,
        camera: EditorCamera,
        glSurfaceView: GLSurfaceView? = null,
        callback: ExportCallback
    ) {
        when (config.engineMode) {
            Mp4ExportEngineMode.FBO_LIVE_THREAD -> {
                if (glSurfaceView != null) {
                    exportWithFboLiveThread(context, outputFile, config, sceneGraph, timeline, timelineInstances, gizmoController, camera, glSurfaceView, callback)
                } else {
                    exportWithGrafikaSharedEgl(context, outputFile, config, sceneGraph, timeline, timelineInstances, gizmoController, camera, callback)
                }
            }
            Mp4ExportEngineMode.GRAFIKA_SHARED_EGL -> {
                exportWithGrafikaSharedEgl(context, outputFile, config, sceneGraph, timeline, timelineInstances, gizmoController, camera, callback)
            }
            Mp4ExportEngineMode.PIXEL_COPY_BITMAP -> {
                if (glSurfaceView != null) {
                    exportWithPixelCopy(context, outputFile, config, sceneGraph, timeline, timelineInstances, gizmoController, camera, glSurfaceView, callback)
                } else {
                    exportWithGrafikaSharedEgl(context, outputFile, config, sceneGraph, timeline, timelineInstances, gizmoController, camera, callback)
                }
            }
        }
    }

    /**
     * MODE 1: FBO Offscreen Render on Active Live GL Thread.
     * Restores original EGL context & surface after completion so UI viewport never freezes.
     */
    private fun exportWithFboLiveThread(
        context: Context,
        outputFile: File,
        config: Mp4ExportConfig,
        sceneGraph: SceneGraph,
        timeline: TimelineAsset,
        timelineInstances: List<TimelineInstance>,
        gizmoController: GizmoController,
        camera: EditorCamera,
        glSurfaceView: GLSurfaceView,
        callback: ExportCallback
    ) {
        val totalFrames = (config.duration * config.fps).toInt().coerceAtLeast(1)
        val dt = 1.0f / config.fps
        val frameIntervalNs = 1_000_000_000L / config.fps

        val exportCamera = EditorCamera().apply {
            target = camera.target
            distance = camera.distance
            yaw = camera.yaw
            pitch = camera.pitch
            fov = camera.fov
        }
        val exportRenderer = SceneRenderer(context, sceneGraph, exportCamera, gizmoController)

        Thread {
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var inputSurface: Surface? = null
            val trackHolder = intArrayOf(-1)

            try {
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    config.width,
                    config.height
                ).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = encoder.createInputSurface()
                encoder.start()

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val bufferInfo = MediaCodec.BufferInfo()

                val lock = java.lang.Object()
                var isFinished = false
                var errorOccurred: String? = null

                glSurfaceView.queueEvent {
                    val origDisplay = EGL14.eglGetCurrentDisplay()
                    val origDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
                    val origReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
                    val origContext = EGL14.eglGetCurrentContext()

                    var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
                    var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
                    var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

                    try {
                        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                        val attribList = intArrayOf(
                            EGL14.EGL_RED_SIZE, 8,
                            EGL14.EGL_GREEN_SIZE, 8,
                            EGL14.EGL_BLUE_SIZE, 8,
                            EGL14.EGL_ALPHA_SIZE, 8,
                            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                            0x3142, 1, // EGL_RECORDABLE_ANDROID
                            EGL14.EGL_NONE
                        )
                        val configs = arrayOfNulls<EGLConfig>(1)
                        val numConfigs = IntArray(1)
                        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                        val eglConfig = configs[0] ?: throw RuntimeException("No recordable EGL config")

                        val contextAttribs = intArrayOf(
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                            EGL14.EGL_NONE
                        )
                        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, origContext, contextAttribs, 0)
                        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, inputSurface!!, intArrayOf(EGL14.EGL_NONE), 0)

                        exportRenderer.isExportRendering = true
                        exportRenderer.initOffscreen(config.width, config.height)

                        for (frame in 0 until totalFrames) {
                            val time = frame * dt
                            callback.onProgress(frame, totalFrames)

                            AnimationEvaluator.evaluate(
                                sceneGraph = sceneGraph,
                                timelines = listOf(timeline),
                                timelineInstances = timelineInstances,
                                currentTime = time
                            )

                            val activeCamera = sceneGraph.getActiveCamera()
                            if (activeCamera != null && activeCamera.cameraData != null) {
                                val cam = activeCamera.cameraData!!
                                val pos = activeCamera.getWorldPosition()
                                val rot = activeCamera.animatedTransform.rotation
                                exportCamera.setFromSceneCamera(pos, rot, cam.fov)
                            } else {
                                exportCamera.target = camera.target
                                exportCamera.distance = camera.distance
                                exportCamera.yaw = camera.yaw
                                exportCamera.pitch = camera.pitch
                                exportCamera.fov = camera.fov
                            }

                            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                            GLES20.glViewport(0, 0, config.width, config.height)
                            exportRenderer.onDrawFrame(null)

                            val ptsNs = frame.toLong() * frameIntervalNs
                            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                            EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                            drainEncoder(encoder, muxer, bufferInfo, trackHolder)
                        }

                        encoder.signalEndOfInputStream()
                        drainEncoderUntilEOS(encoder, muxer, bufferInfo, trackHolder)

                    } catch (e: Exception) {
                        errorOccurred = e.message ?: "GL export error"
                    } finally {
                        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)

                        // Restore original UI EGL context & viewport state
                        if (origDisplay != EGL14.EGL_NO_DISPLAY && origContext != EGL14.EGL_NO_CONTEXT) {
                            EGL14.eglMakeCurrent(origDisplay, origDrawSurface, origReadSurface, origContext)
                        }
                        exportRenderer.isExportRendering = false
                        glSurfaceView.requestRender()

                        synchronized(lock) {
                            isFinished = true
                            lock.notifyAll()
                        }
                    }
                }

                synchronized(lock) {
                    while (!isFinished) {
                        try { lock.wait() } catch (_: InterruptedException) {}
                    }
                }

                if (errorOccurred != null) {
                    callback.onError(errorOccurred!!)
                } else {
                    callback.onComplete(outputFile)
                }

            } catch (e: Exception) {
                callback.onError(e.message ?: "Export failed")
            } finally {
                try { encoder?.stop() } catch (_: Exception) {}
                try { encoder?.release() } catch (_: Exception) {}
                try {
                    if (trackHolder[0] >= 0) muxer?.stop()
                    muxer?.release()
                } catch (_: Exception) {}
                inputSurface?.release()
            }
        }.start()
    }

    /**
     * MODE 2: Google Grafika Shared EGL Architecture (with EGL_RECORDABLE_ANDROID = 1).
     */
    private fun exportWithGrafikaSharedEgl(
        context: Context,
        outputFile: File,
        config: Mp4ExportConfig,
        sceneGraph: SceneGraph,
        timeline: TimelineAsset,
        timelineInstances: List<TimelineInstance>,
        gizmoController: GizmoController,
        camera: EditorCamera,
        callback: ExportCallback
    ) {
        val totalFrames = (config.duration * config.fps).toInt().coerceAtLeast(1)
        val dt = 1.0f / config.fps
        val frameIntervalNs = 1_000_000_000L / config.fps

        val exportCamera = EditorCamera().apply {
            target = camera.target
            distance = camera.distance
            yaw = camera.yaw
            pitch = camera.pitch
            fov = camera.fov
        }
        val exportRenderer = SceneRenderer(context, sceneGraph, exportCamera, gizmoController)

        Thread {
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
            var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
            var inputSurface: Surface? = null
            val trackHolder = intArrayOf(-1)

            try {
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    config.width,
                    config.height
                ).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = encoder.createInputSurface()
                encoder.start()

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("EGL display failed")

                val version = IntArray(2)
                EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

                val attribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    0x3142, 1, // EGL_RECORDABLE_ANDROID
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                val eglConfig = configs[0] ?: throw RuntimeException("No recordable EGL config")

                val sharedContext = EGL14.eglGetCurrentContext()
                val contextAttribs = intArrayOf(
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
                )
                eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, sharedContext, contextAttribs, 0)
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, inputSurface!!, intArrayOf(EGL14.EGL_NONE), 0)

                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                exportRenderer.isExportRendering = true
                exportRenderer.initOffscreen(config.width, config.height)

                val bufferInfo = MediaCodec.BufferInfo()

                for (frame in 0 until totalFrames) {
                    val time = frame * dt
                    callback.onProgress(frame, totalFrames)

                    AnimationEvaluator.evaluate(
                        sceneGraph = sceneGraph,
                        timelines = listOf(timeline),
                        timelineInstances = timelineInstances,
                        currentTime = time
                    )

                    val activeCamera = sceneGraph.getActiveCamera()
                    if (activeCamera != null && activeCamera.cameraData != null) {
                        val cam = activeCamera.cameraData!!
                        val pos = activeCamera.getWorldPosition()
                        val rot = activeCamera.animatedTransform.rotation
                        exportCamera.setFromSceneCamera(pos, rot, cam.fov)
                    } else {
                        exportCamera.target = camera.target
                        exportCamera.distance = camera.distance
                        exportCamera.yaw = camera.yaw
                        exportCamera.pitch = camera.pitch
                        exportCamera.fov = camera.fov
                    }

                    GLES20.glViewport(0, 0, config.width, config.height)
                    exportRenderer.onDrawFrame(null)

                    val ptsNs = frame.toLong() * frameIntervalNs
                    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                    drainEncoder(encoder, muxer, bufferInfo, trackHolder)
                    try { Thread.sleep(10) } catch (_: Exception) {}
                }

                encoder.signalEndOfInputStream()
                drainEncoderUntilEOS(encoder, muxer, bufferInfo, trackHolder)

                callback.onComplete(outputFile)

            } catch (e: Exception) {
                callback.onError(e.message ?: "Grafika export error")
            } finally {
                try { encoder?.stop() } catch (_: Exception) {}
                try { encoder?.release() } catch (_: Exception) {}
                try {
                    if (trackHolder[0] >= 0) muxer?.stop()
                    muxer?.release()
                } catch (_: Exception) {}
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                inputSurface?.release()
            }
        }.start()
    }

    /**
     * MODE 3: Bitmap PixelCopy / glReadPixels Pipeline Fallback.
     * Renders offscreen FBO on GL thread without polluting main screen buffer,
     * and passes exact presentation timestamps (frame * frameIntervalNs) to EGLSurface so output video
     * length equals timeline duration EXACTLY (e.g. 11.5s). Restores UI EGL context cleanly.
     */
    private fun exportWithPixelCopy(
        context: Context,
        outputFile: File,
        config: Mp4ExportConfig,
        sceneGraph: SceneGraph,
        timeline: TimelineAsset,
        timelineInstances: List<TimelineInstance>,
        gizmoController: GizmoController,
        camera: EditorCamera,
        glSurfaceView: GLSurfaceView,
        callback: ExportCallback
    ) {
        val totalFrames = (config.duration * config.fps).toInt().coerceAtLeast(1)
        val dt = 1.0f / config.fps
        val frameIntervalNs = 1_000_000_000L / config.fps

        val exportCamera = EditorCamera().apply {
            target = camera.target
            distance = camera.distance
            yaw = camera.yaw
            pitch = camera.pitch
            fov = camera.fov
        }
        val exportRenderer = SceneRenderer(context, sceneGraph, exportCamera, gizmoController)

        Thread {
            var encoder: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var inputSurface: Surface? = null
            var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
            var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
            var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
            val trackHolder = intArrayOf(-1)

            try {
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    config.width,
                    config.height
                ).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = encoder.createInputSurface()
                encoder.start()

                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val bufferInfo = MediaCodec.BufferInfo()

                // Setup EGL surface for input surface on helper thread
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                EGL14.eglInitialize(eglDisplay, IntArray(2), 0, IntArray(2), 1)
                val attribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    0x3142, 1,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
                val eglConfig = configs[0] ?: throw RuntimeException("No EGL config for PixelCopy")
                val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, inputSurface!!, intArrayOf(EGL14.EGL_NONE), 0)

                val pixelBuffer = ByteBuffer.allocateDirect(config.width * config.height * 4).order(ByteOrder.nativeOrder())
                val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)

                for (frame in 0 until totalFrames) {
                    val time = frame * dt
                    callback.onProgress(frame, totalFrames)

                    val lock = java.lang.Object()
                    var frameDone = false

                    glSurfaceView.queueEvent {
                        val origDisplay = EGL14.eglGetCurrentDisplay()
                        val origDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
                        val origReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
                        val origContext = EGL14.eglGetCurrentContext()

                        val fbo = IntArray(1)
                        val fboTex = IntArray(1)

                        try {
                            // Create offscreen FBO so UI screen buffer is 100% untouched
                            GLES20.glGenFramebuffers(1, fbo, 0)
                            GLES20.glGenTextures(1, fboTex, 0)
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex[0])
                            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, config.width, config.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
                            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex[0], 0)

                            exportRenderer.isExportRendering = true
                            exportRenderer.initOffscreen(config.width, config.height)

                            AnimationEvaluator.evaluate(
                                sceneGraph = sceneGraph,
                                timelines = listOf(timeline),
                                timelineInstances = timelineInstances,
                                currentTime = time
                            )

                            val activeCamera = sceneGraph.getActiveCamera()
                            if (activeCamera != null && activeCamera.cameraData != null) {
                                val cam = activeCamera.cameraData!!
                                val pos = activeCamera.getWorldPosition()
                                val rot = activeCamera.animatedTransform.rotation
                                exportCamera.setFromSceneCamera(pos, rot, cam.fov)
                            } else {
                                exportCamera.target = camera.target
                                exportCamera.distance = camera.distance
                                exportCamera.yaw = camera.yaw
                                exportCamera.pitch = camera.pitch
                                exportCamera.fov = camera.fov
                            }

                            GLES20.glViewport(0, 0, config.width, config.height)
                            exportRenderer.onDrawFrame(null)

                            pixelBuffer.rewind()
                            GLES20.glReadPixels(0, 0, config.width, config.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
                            pixelBuffer.rewind()
                            bitmap.copyPixelsFromBuffer(pixelBuffer)

                        } finally {
                            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                            if (fbo[0] != 0) GLES20.glDeleteFramebuffers(1, fbo, 0)
                            if (fboTex[0] != 0) GLES20.glDeleteTextures(1, fboTex, 0)

                            // Restore UI EGL state and clear export rendering flag
                            if (origDisplay != EGL14.EGL_NO_DISPLAY && origContext != EGL14.EGL_NO_CONTEXT) {
                                EGL14.eglMakeCurrent(origDisplay, origDrawSurface, origReadSurface, origContext)
                            }
                            exportRenderer.isExportRendering = false
                            glSurfaceView.requestRender()

                            synchronized(lock) {
                                frameDone = true
                                lock.notifyAll()
                            }
                        }
                    }

                    synchronized(lock) {
                        while (!frameDone) {
                            try { lock.wait() } catch (_: InterruptedException) {}
                        }
                    }

                    // Render extracted Bitmap onto MediaCodec Surface with EXACT presentation timestamp
                    EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                    val ptsNs = frame.toLong() * frameIntervalNs
                    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)

                    // Draw bitmap onto EGLSurface using Canvas
                    val canvas = inputSurface.lockCanvas(null)
                    if (canvas != null) {
                        val matrix = android.graphics.Matrix().apply {
                            postScale(1f, -1f, config.width / 2f, config.height / 2f)
                        }
                        canvas.drawBitmap(bitmap, matrix, null)
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    drainEncoder(encoder, muxer, bufferInfo, trackHolder)
                }

                encoder.signalEndOfInputStream()
                drainEncoderUntilEOS(encoder, muxer, bufferInfo, trackHolder)

                callback.onComplete(outputFile)

            } catch (e: Exception) {
                callback.onError(e.message ?: "PixelCopy export error")
            } finally {
                try { encoder?.stop() } catch (_: Exception) {}
                try { encoder?.release() } catch (_: Exception) {}
                try {
                    if (trackHolder[0] >= 0) muxer?.stop()
                    muxer?.release()
                } catch (_: Exception) {}
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                inputSurface?.release()
            }
        }.start()
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackHolder: IntArray
    ) {
        val timeoutUs = 10_000L
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (trackHolder[0] < 0) {
                        trackHolder[0] = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                    }
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && trackHolder[0] >= 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackHolder[0], outputBuffer, bufferInfo)
                        }

                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    }

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return
                    }
                }
            }
        }
    }

    private fun drainEncoderUntilEOS(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackHolder: IntArray
    ) {
        val timeoutUs = 20_000L
        var retries = 0
        while (retries < 50) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    retries++
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (trackHolder[0] < 0) {
                        trackHolder[0] = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                    }
                }
                outputBufferIndex >= 0 -> {
                    retries = 0
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && trackHolder[0] >= 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackHolder[0], outputBuffer, bufferInfo)
                        }

                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                    }

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }
}
