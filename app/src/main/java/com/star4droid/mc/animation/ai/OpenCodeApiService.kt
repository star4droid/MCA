package com.star4droid.mc.animation.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class OpenCodeModelEntry(
    val displayName: String,
    val apiModelId: String
)

object OpenCodeApiService {
    private const val TAG = "OpenCodeApiService"
    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_OPENCODE_API_KEY = "opencode_api_key"
    private const val KEY_OPENCODE_MODEL = "selected_opencode_model"

    const val ZEN_API_BASE_URL = "https://opencode.ai/zen/v1/chat/completions"
    const val DEFAULT_MODEL = "big-pickle"

    val MODELS = listOf(
        OpenCodeModelEntry("Big Pickle", "big-pickle"),
        OpenCodeModelEntry("DeepSeek V4 Flash Free", "deepseek-v4-flash-free"),
        OpenCodeModelEntry("MiMo-V2.5 Free", "mimo-v2.5-free"),
        OpenCodeModelEntry("North Mini Code Free", "north-mini-code-free"),
        OpenCodeModelEntry("Nemotron 3 Ultra Free", "nemotron-3-ultra-free")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_OPENCODE_API_KEY, "")?.trim() ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context)
            .edit()
            .putString(KEY_OPENCODE_API_KEY, apiKey.trim())
            .apply()
    }

    fun getSelectedModel(context: Context): String {
        return getPrefs(context)
            .getString(KEY_OPENCODE_MODEL, DEFAULT_MODEL)
            ?: DEFAULT_MODEL
    }

    fun saveSelectedModel(context: Context, model: String) {
        getPrefs(context)
            .edit()
            .putString(KEY_OPENCODE_MODEL, model.trim())
            .apply()
    }

    suspend fun executeOpenCodeCall(
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String? = null,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Executing OpenCode Zen API request with model: $model")

        val url = URL(ZEN_API_BASE_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 120000
        }

        val messagesArr = JSONArray()

        if (!systemInstruction.isNullOrBlank()) {
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }

        for (msg in chatHistory) {
            val role = if (msg.sender == "USER") "user" else "assistant"
            messagesArr.put(JSONObject().apply {
                put("role", role)
                put("content", msg.text)
            })
        }

        messagesArr.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", messagesArr)
        }

        conn.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val responseObj = JSONObject(responseText)

            if (responseObj.has("error")) {
                val errObj = responseObj.optJSONObject("error")
                val errMsg = errObj?.optString("message") ?: "Unknown OpenCode API error"
                throw IllegalStateException("OpenCode API Error: $errMsg")
            }

            val choices = responseObj.optJSONArray("choices")
                ?: throw IllegalStateException("Empty response choices from OpenCode API")

            val firstChoice = choices.optJSONObject(0)
                ?: throw IllegalStateException("Invalid choice format from OpenCode API")

            val messageObj = firstChoice.optJSONObject("message")
                ?: throw IllegalStateException("Missing message in OpenCode response")

            return@withContext messageObj.optString("content", "")
        } else {
            val errStream = conn.errorStream
            val errText = if (errStream != null) {
                BufferedReader(InputStreamReader(errStream)).use { it.readText() }
            } else ""

            val errMsg = try {
                val json = JSONObject(errText)
                json.optJSONObject("error")?.optString("message", errText) ?: errText
            } catch (e: Exception) {
                errText.ifBlank { "HTTP Error $responseCode" }
            }

            throw IllegalStateException("OpenCode API Error ($responseCode): $errMsg")
        }
    }

    suspend fun generateAnimation(
        context: Context,
        prompt: String,
        targetType: String,
        previousAnimationJson: String? = null,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            throw IllegalStateException("OpenCode API Key is missing. Please set your OpenCode Zen API Key in Settings.")
        }

        val model = getSelectedModel(context)

        val systemInstruction = """
            You are an expert 3D animator engine for Minecraft Animator (MCA).
            Generate a JSON animation specification with explicit keyframes for a 3D $targetType.

            Target Type: $targetType

            Target Nodes for CHARACTER:
            "root", "head", "body", "rightArm", "rightForearm",
            "leftArm", "leftForearm", "rightLeg", "rightThigh",
            "rightLowerLeg", "rightCalf", "leftLeg", "leftThigh",
            "leftLowerLeg", "leftCalf".

            Target Nodes for BLOCK:
            "block" or "root".

            Keyframe properties:
            - time: float offset in seconds (from 0.0 to duration)
            - position: [x, y, z] translation vector (optional)
            - rotation: [rx, ry, rz] Euler rotation angles in degrees (optional)
            - scale: [sx, sy, sz] scale vector (optional)

            Output ONLY valid raw JSON matching this structure:

            {
              "name": "Animation Name",
              "description": "Short description of the motion",
              "category": "$targetType",
              "duration": 2.0,
              "tracks": [
                {
                  "targetNode": "rightArm",
                  "keyframes": [
                    { "time": 0.0, "rotation": [0.0, 0.0, 0.0] },
                    { "time": 1.0, "rotation": [-60.0, 0.0, 30.0] },
                    { "time": 2.0, "rotation": [0.0, 0.0, 0.0] }
                  ]
                }
              ]
            }
        """.trimIndent()

        val fullPrompt = if (!previousAnimationJson.isNullOrBlank()) {
            """
                PREVIOUS ANIMATION STATE JSON:
                $previousAnimationJson

                NEW USER REQUEST:
                "$prompt"

                Refine and modify the previous animation JSON according to the new request. Return ONLY valid JSON.
            """.trimIndent()
        } else {
            prompt
        }

        val rawResult = executeOpenCodeCall(
            apiKey = apiKey,
            model = model,
            prompt = fullPrompt,
            systemInstruction = systemInstruction,
            chatHistory = chatHistory
        )

        return@withContext rawResult
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    suspend fun generate3DObject(
        context: Context,
        prompt: String,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> = emptyList()
    ): GeminiApiService.GeneratedObjResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            throw IllegalStateException("OpenCode API Key is missing. Please set your OpenCode Zen API Key in Settings.")
        }

        val model = getSelectedModel(context)

        val systemInstruction = """
            You are an expert 3D modeler, procedural geometry designer, and Wavefront OBJ specialist.
            Your task is to generate a complete, high-quality 3D object in standard Wavefront OBJ (.obj) format based on the user request.
            Return ONLY a valid JSON object matching exactly this schema:

            {
              "name": "Short Title of 3D Object",
              "description": "Brief description of the generated 3D model",
              "colorHex": "#RRGGBB",
              "suggestedTexture": "appropriate_material",
              "obj": "Complete Wavefront OBJ content"
            }
        """.trimIndent()

        val rawText = executeOpenCodeCall(
            apiKey = apiKey,
            model = model,
            prompt = prompt,
            systemInstruction = systemInstruction,
            chatHistory = chatHistory
        )

        val cleanText = rawText
            .removePrefix("```json")
            .removePrefix("```obj")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        var name = prompt.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        var description = "3D model of $prompt"
        var colorHex = "#38BDF8"
        var suggestedTexture = "generic_material"
        var objContent = ""

        try {
            val json = JSONObject(cleanText)
            name = json.optString("name", name)
            description = json.optString("description", description)
            colorHex = json.optString("colorHex", colorHex)
            suggestedTexture = json.optString("suggestedTexture", suggestedTexture)
            objContent = json.optString("obj", "")
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed: ${e.message}")
        }

        if (objContent.isBlank()) {
            val objMatch = Regex("\"obj\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*[,}]").find(cleanText)
            if (objMatch != null) {
                objContent = objMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }

        if (objContent.isBlank() && cleanText.contains("v ") && cleanText.contains("f ")) {
            val lines = cleanText.lines().filter { line ->
                val trimmed = line.trim()
                trimmed.startsWith("#") || trimmed.startsWith("v ") || trimmed.startsWith("vn ") ||
                trimmed.startsWith("vt ") || trimmed.startsWith("f ") || trimmed.startsWith("o ") ||
                trimmed.startsWith("g ") || trimmed.startsWith("s ")
            }
            if (lines.isNotEmpty()) {
                objContent = lines.joinToString("\n")
            }
        }

        if (objContent.isBlank() || !objContent.contains("v ") || !objContent.contains("f ")) {
            throw IllegalStateException("OpenCode AI generated an invalid 3D model (missing vertices or faces). Please try again.")
        }

        return@withContext GeminiApiService.GeneratedObjResult(
            name = name,
            description = description,
            colorHex = colorHex,
            suggestedTexture = suggestedTexture,
            objContent = objContent
        )
    }
}
