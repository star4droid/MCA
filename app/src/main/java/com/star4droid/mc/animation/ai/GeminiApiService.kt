package com.star4droid.mc.animation.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.star4droid.mc.animation.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object GeminiApiService {
    private const val TAG = "GeminiApiService"
    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "selected_model"

    // Use gemini-flash-latest (resolves to gemini-3.8-flash) as recommended default for speed & reliability
    const val DEFAULT_MODEL = "gemini-flash-latest"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Resolves the Gemini API Key.
     * Priority:
     * 1. User-provided custom API key in SharedPreferences
     * 2. BuildConfig.GEMINI_API_KEY injected via Secrets plugin / .env
     */
    fun getApiKey(context: Context): String {
        val userKey = getPrefs(context).getString(KEY_API_KEY, "")?.trim() ?: ""
        if (userKey.isNotBlank()) {
            return userKey
        }
        val buildConfigKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            return buildConfigKey
        }
        return ""
    }

    fun isUsingBuildConfigKey(context: Context): Boolean {
        val userKey = getPrefs(context).getString(KEY_API_KEY, "")?.trim() ?: ""
        val buildConfigKey = BuildConfig.GEMINI_API_KEY.trim()
        return userKey.isBlank() && buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY"
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

    /**
     * Executes a POST request to Gemini generateContent endpoint.
     * Includes automatic fallback across compatible flash models if a 429 quota limit is encountered.
     */
    private suspend fun executeGeminiCall(
        apiKey: String,
        primaryModel: String,
        prompt: String,
        systemInstruction: String? = null,
        jsonMode: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val candidateModels = linkedSetOf(
            primaryModel,
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-3.1-flash-lite-preview"
        ).toList()

        var lastErrorText = ""
        var lastResponseCode = -1

        for (model in candidateModels) {
            try {
                Log.d(TAG, "Attempting Gemini API request with model: $model")
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 90000
                }

                val requestJson = JSONObject().apply {
                    val contentsArr = JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    }
                    put("contents", contentsArr)

                    if (!systemInstruction.isNullOrBlank()) {
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", systemInstruction))
                            })
                        })
                    }

                    if (jsonMode) {
                        put("generationConfig", JSONObject().apply {
                            put("responseMimeType", "application/json")
                        })
                    }
                }

                conn.outputStream.use { os ->
                    os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                lastResponseCode = responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val responseObj = JSONObject(responseText)
                    val candidates = responseObj.optJSONArray("candidates")
                    val first = candidates?.optJSONObject(0)
                        ?: throw IllegalStateException("Empty response candidates from model $model")
                    val content = first.optJSONObject("content")
                        ?: throw IllegalStateException("Empty response content from model $model")
                    val parts = content.optJSONArray("parts")
                        ?: throw IllegalStateException("Empty parts from model $model")
                    val part = parts.optJSONObject(0)
                        ?: throw IllegalStateException("Empty part from model $model")
                    val rawText = part.optString("text", "")

                    return@withContext rawText
                } else {
                    val errStream = conn.errorStream
                    val errText = if (errStream != null) {
                        BufferedReader(InputStreamReader(errStream)).use { it.readText() }
                    } else ""
                    lastErrorText = errText
                    Log.w(TAG, "Gemini API HTTP $responseCode for model $model: $errText")

                    // If quota exceeded (429) or model deprecated (404), try next model in candidate list
                    if ((responseCode == 429 || responseCode == 404) && model != candidateModels.last()) {
                        Log.i(TAG, "Switching to backup model due to HTTP $responseCode on $model")
                        continue
                    } else {
                        throw IllegalStateException("Gemini API Error ($responseCode): ${extractErrorMessage(errText)}")
                    }
                }
            } catch (e: Exception) {
                if (e is IllegalStateException && e.message?.startsWith("Gemini API Error") == true) {
                    throw e
                }
                Log.w(TAG, "Exception during call to $model: ${e.message}")
                if (model == candidateModels.last()) {
                    throw IllegalStateException("Failed to connect to Gemini API: ${e.message ?: "Network error"}")
                }
            }
        }

        throw IllegalStateException("Gemini API Error ($lastResponseCode): ${extractErrorMessage(lastErrorText)}")
    }

    private fun extractErrorMessage(rawError: String): String {
        return try {
            val json = JSONObject(rawError)
            val errorObj = json.optJSONObject("error")
            errorObj?.optString("message", rawError) ?: rawError
        } catch (e: Exception) {
            if (rawError.isNotBlank()) rawError else "Unknown server error"
        }
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
            throw IllegalStateException("Gemini API Key is missing. Please set your Gemini API Key in Settings (gear icon) or configure GEMINI_API_KEY.")
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
            $historyContext
            
            PREVIOUS ANIMATION STATE JSON:
            $previousAnimationJson
            
            NEW USER REQUEST:
            "$prompt"
            
            Refine and modify the previous animation JSON according to the new request and conversation history. Return ONLY valid JSON.
            """.trimIndent()
        } else {
            """
            $historyContext
            
            NEW USER PROMPT:
            "$prompt"
            
            Return ONLY valid JSON.
            """.trimIndent()
        }

        val rawResult = executeGeminiCall(
            apiKey = apiKey,
            primaryModel = model,
            prompt = fullPrompt,
            systemInstruction = systemInstruction,
            jsonMode = true
        )

        return@withContext rawResult
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    data class GeneratedObjResult(
        val name: String,
        val description: String,
        val colorHex: String,
        val suggestedTexture: String,
        val objContent: String
    )

    /**
     * Generates a fully functional 3D Wavefront OBJ model using the Gemini API.
     * Takes the user's creative prompt and creates authentic 3D geometry with vertices and faces.
     */
    suspend fun generate3DObject(
        context: Context,
        prompt: String,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> = emptyList()
    ): GeneratedObjResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API Key is missing. Please set your API key in Settings (gear icon) or configure GEMINI_API_KEY in Secrets.")
        }
        val model = getSelectedModel(context)

        val systemInstruction = """
            You are an expert 3D modeler and CAD specialist for Minecraft Animator (MCA).
            Generate a complete, high-quality 3D object in Wavefront OBJ (.obj) format based on the user prompt.
            
            3D Wavefront OBJ Rules:
            1. Geometry & Coordinate System:
               - Coordinate system: Y is UP (+Y), X is RIGHT (+X), Z is FORWARD (+Z).
               - Model should be centered at X=0, Z=0 and rest on the ground at Y >= 0.
               - Dimensions should be around 1.0 to 2.5 Minecraft blocks/units in height and width.
            2. Vertices & Faces:
               - Generate geometric vertices using 'v x y z' lines.
               - Generate faces using 'f v1 v2 v3' (triangles) or 'f v1 v2 v3 v4' (quads).
               - All face vertex indices MUST be 1-based positive integers referencing declared 'v' lines.
               - Normals should face outward (counter-clockwise vertex ordering).
               - Construct a recognizable 3D representation of the requested object with between 12 and 150 faces.
            3. Aesthetic & Materials:
               - Minecraft / Voxel / Low-poly aesthetic suitable for Minecraft animation scenes.
               - Choose a matching colorHex (#RRGGBB) representing the primary material.
               - Choose the most fitting suggestedTexture from:
                 "oak_planks", "stone", "iron_block", "gold_block", "diamond_block",
                 "obsidian", "emerald_block", "redstone_block", "bricks", "crafting_table",
                 "wool_red", "wool_blue", "wool_green", "wool_black", "wool_white",
                 "glass", "cobblestone", "tnt", "glowstone".

            Return ONLY a valid JSON object matching this schema:
            {
              "name": "Short Title of 3D Object",
              "description": "Brief 1-sentence description of the 3D model features",
              "colorHex": "#RRGGBB",
              "suggestedTexture": "diamond_block",
              "obj": "The complete Wavefront OBJ string starting with v definitions and ending with f definitions"
            }
        """.trimIndent()

        val historyContext = if (chatHistory.isNotEmpty()) {
            val relevantHistory = chatHistory.takeLast(6).joinToString("\n") { msg ->
                if (msg.sender == "USER") "User: \"${msg.text}\"" else "Assistant: \"${msg.text}\""
            }
            "Prior Chat Context:\n$relevantHistory\n\n"
        } else ""

        val fullPrompt = """
            ${historyContext}User Request:
            "$prompt"

            Generate the 3D model now according to the instructions and output ONLY the JSON object.
        """.trimIndent()

        val rawText = executeGeminiCall(
            apiKey = apiKey,
            primaryModel = model,
            prompt = fullPrompt,
            systemInstruction = systemInstruction,
            jsonMode = true
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
        var suggestedTexture = "stone"
        var objContent = ""

        // Strategy 1: Parse structured JSON
        try {
            val json = JSONObject(cleanText)
            name = json.optString("name", name)
            description = json.optString("description", description)
            colorHex = json.optString("colorHex", colorHex)
            suggestedTexture = json.optString("suggestedTexture", suggestedTexture)
            objContent = json.optString("obj", "")
        } catch (e: Exception) {
            Log.w(TAG, "Standard JSON parse failed, trying regex extraction: ${e.message}")
        }

        // Strategy 2: Regex extraction if raw JSON parsing encountered unescaped quotes/newlines
        if (objContent.isBlank()) {
            Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(cleanText)?.let {
                name = it.groupValues[1]
            }
            Regex("\"description\"\\s*:\\s*\"([^\"]+)\"").find(cleanText)?.let {
                description = it.groupValues[1]
            }
            Regex("\"colorHex\"\\s*:\\s*\"(#[0-9a-fA-F]{6})\"").find(cleanText)?.let {
                colorHex = it.groupValues[1]
            }
            Regex("\"suggestedTexture\"\\s*:\\s*\"([^\"]+)\"").find(cleanText)?.let {
                suggestedTexture = it.groupValues[1]
            }

            val objMatch = Regex("\"obj\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*[,}]").find(cleanText)
            if (objMatch != null) {
                objContent = objMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }

        // Strategy 3: Direct Wavefront OBJ lines extraction from the response
        if (objContent.isBlank() && cleanText.contains("v ") && cleanText.contains("f ")) {
            val lines = cleanText.lines().filter { l ->
                val trimmed = l.trim()
                trimmed.startsWith("#") || trimmed.startsWith("v ") || trimmed.startsWith("vn ") ||
                trimmed.startsWith("vt ") || trimmed.startsWith("f ") || trimmed.startsWith("o ") ||
                trimmed.startsWith("g ") || trimmed.startsWith("s ")
            }
            if (lines.isNotEmpty()) {
                objContent = lines.joinToString("\n")
            }
        }

        // Validate that the generated OBJ contains real vertices and faces
        if (objContent.isBlank() || !objContent.contains("v ") || !objContent.contains("f ")) {
            throw IllegalStateException(
                "Gemini generated an invalid 3D model (missing vertices or faces). Please try again with a descriptive prompt such as 'a medieval sword' or 'a wooden treasure chest'."
            )
        }

        return@withContext GeneratedObjResult(
            name = name,
            description = description,
            colorHex = colorHex,
            suggestedTexture = suggestedTexture,
            objContent = objContent
        )
    }

    /**
     * Procedural fallback object generator for offline testing or starter assets.
     */
    fun generateProceduralStarterObj(prompt: String): GeneratedObjResult {
        val p = prompt.lowercase()
        val name = prompt.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return when {
            p.contains("sword") || p.contains("blade") || p.contains("weapon") -> {
                val obj = """
                    # Procedural Sword
                    v -0.04 0.0 -0.04
                    v  0.04 0.0 -0.04
                    v  0.04 0.35 -0.04
                    v -0.04 0.35 -0.04
                    v -0.25 0.35 -0.06
                    v  0.25 0.35 -0.06
                    v  0.25 0.42 -0.06
                    v -0.25 0.42 -0.06
                    v -0.06 0.42 -0.02
                    v  0.06 0.42 -0.02
                    v  0.06 1.45 -0.02
                    v  0.0 1.65 0.0
                    v -0.06 1.45 -0.02
                    f 1 2 3 4
                    f 5 6 7 8
                    f 9 10 11 12 13
                """.trimIndent()
                GeneratedObjResult(name, "A sleek 3D sword crafted for battle", "#38BDF8", "diamond_block", obj)
            }
            p.contains("table") || p.contains("desk") -> {
                val obj = """
                    # Procedural Table
                    v -0.1 0.0 -0.1
                    v  0.1 0.0 -0.1
                    v  0.1 0.7 -0.1
                    v -0.1 0.7 -0.1
                    v -0.65 0.7 -0.65
                    v  0.65 0.7 -0.65
                    v  0.65 0.85 -0.65
                    v -0.65 0.85 -0.65
                    v -0.65 0.7  0.65
                    v  0.65 0.7  0.65
                    v  0.65 0.85  0.65
                    v -0.65 0.85  0.65
                    f 1 2 3 4
                    f 5 6 7 8
                    f 9 10 11 12
                    f 8 7 11 12
                    f 5 6 10 9
                """.trimIndent()
                GeneratedObjResult(name, "A polished sturdy table", "#8B5A2B", "oak_planks", obj)
            }
            else -> {
                val obj = """
                    # Procedural Crystal
                    v  0.0  0.0  0.0
                    v -0.35 0.6 -0.35
                    v  0.35 0.6 -0.35
                    v  0.35 0.6  0.35
                    v -0.35 0.6  0.35
                    v  0.0  1.5  0.0
                    f 1 2 3
                    f 1 3 4
                    f 1 4 5
                    f 1 5 2
                    f 6 3 2
                    f 6 4 3
                    f 6 5 4
                    f 6 2 5
                """.trimIndent()
                GeneratedObjResult(name, "A glowing magical crystal shard", "#00FFFF", "diamond_block", obj)
            }
        }
    }
}
