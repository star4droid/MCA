package com.star4droid.mc.animation.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiApiService {
    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "selected_model"
    const val DEFAULT_MODEL = "gemini-3.5-flash"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getSelectedModel(context: Context): String {
        return getPrefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun saveSelectedModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    suspend fun generateAnimation(
        context: Context,
        prompt: String,
        targetType: String, // "CHARACTER" or "BLOCK"
        previousAnimationJson: String? = null,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            throw IllegalStateException("API Key is missing. Please set your Gemini API Key in Settings.")
        }
        val model = getSelectedModel(context)

        val systemInstruction = """
            You are an expert 3D animator engine for Minecraft Animator (MCA).
            Generate a JSON animation specification with explicit keyframes for a 3D $targetType.
            
            Target Type: $targetType
            
            Target Nodes for CHARACTER: "root", "head", "body", "rightArm", "rightForearm", "leftArm", "leftForearm", "rightLeg" (or "rightThigh"), "rightLowerLeg" (or "rightCalf"), "leftLeg" (or "leftThigh"), "leftLowerLeg" (or "leftCalf").
            Target Nodes for BLOCK: "block" (or "root").
            
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
                },
                {
                  "targetNode": "leftArm",
                  "keyframes": [
                    { "time": 0.0, "rotation": [0.0, 0.0, 0.0] },
                    { "time": 1.0, "rotation": [60.0, 0.0, -30.0] },
                    { "time": 2.0, "rotation": [0.0, 0.0, 0.0] }
                  ]
                }
              ]
            }
        """.trimIndent()

        val historyContext = if (chatHistory.isNotEmpty()) {
            val historyStr = chatHistory.takeLast(10).joinToString("\n") { msg ->
                if (msg.sender == "USER") "USER INSTRUCTION: \"${msg.text}\"" else "AI RESULT SUMMARY: ${msg.text}"
            }
            "\nPAST INSTRUCTIONS AND CHAT HISTORY:\n$historyStr\n"
        } else ""

        val fullPrompt = if (!previousAnimationJson.isNullOrBlank()) {
            """
            $systemInstruction
            $historyContext
            
            PREVIOUS ANIMATION STATE JSON:
            $previousAnimationJson
            
            NEW USER REQUEST:
            "$prompt"
            
            Refine and modify the previous animation JSON according to the new request and conversation history. Return ONLY valid JSON.
            """.trimIndent()
        } else {
            """
            $systemInstruction
            $historyContext
            
            NEW USER PROMPT:
            "$prompt"
            
            Return ONLY valid JSON.
            """.trimIndent()
        }

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 20000
        }

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", fullPrompt))
                    })
                })
            })
        }

        conn.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val responseObj = JSONObject(responseText)
            val candidates = responseObj.optJSONArray("candidates")
            val first = candidates?.optJSONObject(0) ?: throw IllegalStateException("Empty response candidates")
            val content = first.optJSONObject("content") ?: throw IllegalStateException("Empty response content")
            val parts = content.optJSONArray("parts") ?: throw IllegalStateException("Empty text parts")
            val part = parts.optJSONObject(0) ?: throw IllegalStateException("Empty text part")
            val rawText = part.optString("text", "")

            val cleanJson = rawText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            return@withContext cleanJson
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Gemini API Error ($responseCode): $errText")
        }
    }
}
