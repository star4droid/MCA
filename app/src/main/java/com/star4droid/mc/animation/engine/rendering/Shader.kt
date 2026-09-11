package com.star4droid.mc.animation.engine.rendering

import android.opengl.GLES20
import android.util.Log

class Shader(vertexSource: String, fragmentSource: String) {

    val programId: Int

    val aPosition: Int
    val aNormal: Int
    val aTexCoord: Int

    val uMVPMatrix: Int
    val uModelMatrix: Int
    val uLightDir: Int
    val uLightColor: Int
    val uAmbientColor: Int
    val uObjectColor: Int
    val uSelectionColor: Int
    val uIsSelected: Int
    val uTexture: Int
    val uUseTexture: Int

    init {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val info = GLES20.glGetProgramInfoLog(prog)
                GLES20.glDeleteProgram(prog)
                throw RuntimeException("Could not link shader program: $info")
            }
        }

        aPosition = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormal = GLES20.glGetAttribLocation(programId, "aNormal")
        aTexCoord = GLES20.glGetAttribLocation(programId, "aTexCoord")

        uMVPMatrix = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uModelMatrix = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uLightDir = GLES20.glGetUniformLocation(programId, "uLightDir")
        uLightColor = GLES20.glGetUniformLocation(programId, "uLightColor")
        uAmbientColor = GLES20.glGetUniformLocation(programId, "uAmbientColor")
        uObjectColor = GLES20.glGetUniformLocation(programId, "uObjectColor")
        uSelectionColor = GLES20.glGetUniformLocation(programId, "uSelectionColor")
        uIsSelected = GLES20.glGetUniformLocation(programId, "uIsSelected")
        uTexture = GLES20.glGetUniformLocation(programId, "uTexture")
        uUseTexture = GLES20.glGetUniformLocation(programId, "uUseTexture")
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    companion object {
        private fun compileShader(type: Int, source: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val status = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                if (status[0] == 0) {
                    val info = GLES20.glGetShaderInfoLog(shader)
                    GLES20.glDeleteShader(shader)
                    throw RuntimeException("Error compiling shader (type $type): $info")
                }
            }
        }

        const val VERTEX_SHADER_SRC = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;

            attribute vec4 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;

            varying vec3 vNormal;
            varying vec3 vPosition;
            varying vec2 vTexCoord;

            void main() {
                vPosition = vec3(uModelMatrix * aPosition);
                // Simple normal transformation (assuming uniform scaling)
                vNormal = normalize(vec3(uModelMatrix * vec4(aNormal, 0.0)));
                vTexCoord = aTexCoord;
                gl_Position = uMVPMatrix * aPosition;
            }
        """

        const val FRAGMENT_SHADER_SRC = """
            precision mediump float;

            uniform vec3 uLightDir;
            uniform vec3 uLightColor;
            uniform vec3 uAmbientColor;
            uniform vec4 uObjectColor;
            uniform vec4 uSelectionColor;
            uniform float uIsSelected;
            uniform sampler2D uTexture;
            uniform float uUseTexture;

            varying vec3 vNormal;
            varying vec3 vPosition;
            varying vec2 vTexCoord;

            void main() {
                vec4 baseColor = uObjectColor;
                if (uUseTexture > 0.5) {
                    vec4 texColor = texture2D(uTexture, vTexCoord);
                    baseColor = texColor * uObjectColor;
                }

                // Diffuse lighting
                float diff = max(dot(vNormal, normalize(uLightDir)), 0.0);
                vec3 lighting = uAmbientColor + uLightColor * diff;

                vec3 finalRgb = baseColor.rgb * lighting;

                // Selection highlight tint
                if (uIsSelected > 0.5) {
                    finalRgb = mix(finalRgb, uSelectionColor.rgb, 0.35);
                }

                gl_FragColor = vec4(finalRgb, baseColor.a);
            }
        """
    }
}
