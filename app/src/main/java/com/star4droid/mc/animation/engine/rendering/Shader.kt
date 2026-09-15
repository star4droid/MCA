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
    val uAlpha: Int

    val uNumPointLights: Int
    val uPointLightPos: Int
    val uPointLightColor: Int
    val uPointLightIntensity: Int
    val uPointLightRange: Int

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
        uAlpha = GLES20.glGetUniformLocation(programId, "uAlpha")

        uNumPointLights = GLES20.glGetUniformLocation(programId, "uNumPointLights")
        uPointLightPos = GLES20.glGetUniformLocation(programId, "uPointLightPos")
        uPointLightColor = GLES20.glGetUniformLocation(programId, "uPointLightColor")
        uPointLightIntensity = GLES20.glGetUniformLocation(programId, "uPointLightIntensity")
        uPointLightRange = GLES20.glGetUniformLocation(programId, "uPointLightRange")
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
            uniform float uAlpha;

            uniform int uNumPointLights;
            uniform vec3 uPointLightPos[4];
            uniform vec3 uPointLightColor[4];
            uniform float uPointLightIntensity[4];
            uniform float uPointLightRange[4];

            varying vec3 vNormal;
            varying vec3 vPosition;
            varying vec2 vTexCoord;

            void main() {
                vec4 baseColor = uObjectColor;
                if (uUseTexture > 0.5) {
                    vec4 texColor = texture2D(uTexture, vTexCoord);
                    baseColor = vec4(texColor.rgb * uObjectColor.rgb, texColor.a * uObjectColor.a);
                }

                float effectiveAlpha = baseColor.a * (uAlpha > 0.0 ? uAlpha : 1.0);
                if (effectiveAlpha < 0.02) {
                    discard;
                }

                vec3 norm = normalize(vNormal);
                float diff = max(dot(norm, normalize(uLightDir)), 0.0);
                vec3 lighting = uAmbientColor + uLightColor * diff;

                for (int i = 0; i < 4; i++) {
                    if (i >= uNumPointLights) break;
                    vec3 lightVec = uPointLightPos[i] - vPosition;
                    float dist = length(lightVec);
                    if (dist < uPointLightRange[i]) {
                        vec3 lDir = normalize(lightVec);
                        float pDiff = max(dot(norm, lDir), 0.0);
                        float atten = 1.0 - (dist / uPointLightRange[i]);
                        atten = atten * atten;
                        lighting += uPointLightColor[i] * pDiff * uPointLightIntensity[i] * atten;
                    }
                }

                vec3 finalRgb = baseColor.rgb * lighting;

                if (uIsSelected > 0.5) {
                    finalRgb = mix(finalRgb, uSelectionColor.rgb, 0.4);
                }

                gl_FragColor = vec4(finalRgb, effectiveAlpha);
            }
        """
    }
}
