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

    // Use gemini-flash-latest as the default model for speed & reliability
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

        return userKey.isBlank() &&
                buildConfigKey.isNotBlank() &&
                buildConfigKey != "MY_GEMINI_API_KEY"
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context)
            .edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
    }

    fun getSelectedModel(context: Context): String {
        return getPrefs(context)
            .getString(KEY_MODEL, DEFAULT_MODEL)
            ?: DEFAULT_MODEL
    }

    fun saveSelectedModel(context: Context, model: String) {
        getPrefs(context)
            .edit()
            .putString(KEY_MODEL, model.trim())
            .apply()
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

                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                            "$model:generateContent?key=$apiKey"
                )

                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 90000
                }

                val requestJson = JSONObject().apply {
                    val contentsArr = JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put(
                                    "parts",
                                    JSONArray().apply {
                                        put(
                                            JSONObject().put(
                                                "text",
                                                prompt
                                            )
                                        )
                                    }
                                )
                            }
                        )
                    }

                    put("contents", contentsArr)

                    if (!systemInstruction.isNullOrBlank()) {
                        put(
                            "systemInstruction",
                            JSONObject().apply {
                                put(
                                    "parts",
                                    JSONArray().apply {
                                        put(
                                            JSONObject().put(
                                                "text",
                                                systemInstruction
                                            )
                                        )
                                    }
                                )
                            }
                        )
                    }

                    if (jsonMode) {
                        put(
                            "generationConfig",
                            JSONObject().apply {
                                put("responseMimeType", "application/json")
                            }
                        )
                    }
                }

                conn.outputStream.use { os ->
                    os.write(
                        requestJson
                            .toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                }

                val responseCode = conn.responseCode
                lastResponseCode = responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText =
                        conn.inputStream.bufferedReader().use { it.readText() }

                    val responseObj = JSONObject(responseText)

                    val candidates =
                        responseObj.optJSONArray("candidates")

                    val first =
                        candidates?.optJSONObject(0)
                            ?: throw IllegalStateException(
                                "Empty response candidates from model $model"
                            )

                    val content =
                        first.optJSONObject("content")
                            ?: throw IllegalStateException(
                                "Empty response content from model $model"
                            )

                    val parts =
                        content.optJSONArray("parts")
                            ?: throw IllegalStateException(
                                "Empty parts from model $model"
                            )

                    val part =
                        parts.optJSONObject(0)
                            ?: throw IllegalStateException(
                                "Empty part from model $model"
                            )

                    val rawText =
                        part.optString("text", "")

                    return@withContext rawText
                } else {
                    val errStream = conn.errorStream

                    val errText =
                        if (errStream != null) {
                            BufferedReader(
                                InputStreamReader(errStream)
                            ).use { it.readText() }
                        } else {
                            ""
                        }

                    lastErrorText = errText

                    Log.w(
                        TAG,
                        "Gemini API HTTP $responseCode for model $model: $errText"
                    )

                    // If quota exceeded (429) or model deprecated (404),
                    // try the next model in the candidate list.
                    if (
                        (responseCode == 429 || responseCode == 404) &&
                        model != candidateModels.last()
                    ) {
                        Log.i(
                            TAG,
                            "Switching to backup model due to HTTP " +
                                    "$responseCode on $model"
                        )

                        continue
                    } else {
                        throw IllegalStateException(
                            "Gemini API Error ($responseCode): " +
                                    extractErrorMessage(errText)
                        )
                    }
                }
            } catch (e: Exception) {
                if (
                    e is IllegalStateException &&
                    e.message?.startsWith("Gemini API Error") == true
                ) {
                    throw e
                }

                Log.w(
                    TAG,
                    "Exception during call to $model: ${e.message}"
                )

                if (model == candidateModels.last()) {
                    throw IllegalStateException(
                        "Failed to connect to Gemini API: " +
                                "${e.message ?: "Network error"}"
                    )
                }
            }
        }

        throw IllegalStateException(
            "Gemini API Error ($lastResponseCode): " +
                    extractErrorMessage(lastErrorText)
        )
    }

    private fun extractErrorMessage(rawError: String): String {
        return try {
            val json = JSONObject(rawError)
            val errorObj = json.optJSONObject("error")

            errorObj?.optString("message", rawError)
                ?: rawError
        } catch (e: Exception) {
            if (rawError.isNotBlank()) {
                rawError
            } else {
                "Unknown server error"
            }
        }
    }

    suspend fun generateAnimation(
        context: Context,
        prompt: String,
        targetType: String,
        previousAnimationJson: String? = null,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> =
            emptyList()
    ): String = withContext(Dispatchers.IO) {

        val apiKey = getApiKey(context)

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Gemini API Key is missing. Please set your Gemini API Key " +
                        "in Settings (gear icon) or configure GEMINI_API_KEY."
            )
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

        val historyContext =
            if (chatHistory.isNotEmpty()) {
                val historyStr =
                    chatHistory
                        .takeLast(10)
                        .joinToString("\n") { msg ->
                            if (msg.sender == "USER") {
                                "USER INSTRUCTION: \"${msg.text}\""
                            } else {
                                "AI RESULT SUMMARY: ${msg.text}"
                            }
                        }

                "\nPAST INSTRUCTIONS AND CHAT HISTORY:\n" +
                        "$historyStr\n"
            } else {
                ""
            }

        val fullPrompt =
            if (!previousAnimationJson.isNullOrBlank()) {
                """
                    $historyContext

                    PREVIOUS ANIMATION STATE JSON:
                    $previousAnimationJson

                    NEW USER REQUEST:
                    "$prompt"

                    Refine and modify the previous animation JSON according
                    to the new request and conversation history.
                    Return ONLY valid JSON.
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
     * Generates a complete 3D Wavefront OBJ model using the Gemini API.
     *
     * The model is NOT restricted to Minecraft, voxel, cuboid, or blocky
     * geometry. The AI can generate general-purpose 3D objects based on
     * the user's description.
     */
    suspend fun generate3DObject(
        context: Context,
        prompt: String,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> =
            emptyList()
    ): GeneratedObjResult = withContext(Dispatchers.IO) {

        val apiKey = getApiKey(context)

        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Gemini API Key is missing. Please set your API key " +
                        "in Settings (gear icon) or configure GEMINI_API_KEY in Secrets."
            )
        }

        val model = getSelectedModel(context)

        val systemInstruction = """
            You are an expert 3D modeler, procedural geometry designer,
            and Wavefront OBJ specialist.

            Your task is to generate a complete, high-quality 3D object
            in standard Wavefront OBJ (.obj) format based entirely on
            the user's request.

            GENERAL 3D MODEL REQUIREMENT:

            - Do NOT restrict the model to Minecraft style.
            - Do NOT restrict the model to voxel geometry.
            - Do NOT force the model to be blocky, cuboid, square,
              stepped, or composed only of rectangular prisms.
            - The user may request ANY type of 3D object.
            - The requested object can be realistic, stylized,
              cartoon, low-poly, high-poly, mechanical, organic,
              architectural, fantasy, sci-fi, or any other style.
            - Carefully interpret the user's requested object,
              shape, proportions, materials, and visual style.
            - Use the most appropriate geometry for the requested object.
            - Use curved, cylindrical, spherical, conical, beveled,
              organic, mechanical, polygonal, or custom geometry
              whenever appropriate.
            - Only use cubes, cuboids, or voxel geometry when the
              requested object actually requires them or when they
              are appropriate to the requested style.
            - The final model should clearly resemble the object
              requested by the user.

            USER REQUEST INTERPRETATION:

            - Analyze the complete user prompt before generating geometry.
            - Identify the main object and its important components.
            - Identify distinctive details that make the object recognizable.
            - Preserve the requested proportions and overall silhouette.
            - If the user specifies a style, follow that style.
            - If the user specifies dimensions, respect them as closely
              as practical.
            - If dimensions are not specified, choose sensible dimensions.
            - If the object consists of multiple parts, model those parts
              separately or as connected geometry as appropriate.
            - Do not replace a complex requested object with a generic
              primitive simply because it is easier to generate.

            3D WAVEFRONT OBJ RULES:

            1. Coordinate System:
               - Y is UP (+Y).
               - X is RIGHT (+X).
               - Z is FORWARD (+Z).
               - Center the model approximately around X=0 and Z=0.
               - Place the model on or near the ground at Y >= 0 unless
                 the requested object or orientation requires otherwise.
               - Use sensible dimensions and proportions.

            2. Vertices:
               - Generate actual geometric vertices using:
                 v x y z
               - Vertex coordinates must be valid floating-point numbers.
               - Avoid unnecessary duplicate vertices when practical.

            3. Faces:
               - Generate faces using:
                 f v1 v2 v3
                 or
                 f v1 v2 v3 v4
               - Triangles and quads are both valid.
               - Face vertex indices MUST be positive 1-based indices.
               - Every face index MUST reference an existing vertex.
               - Use consistent face winding.
               - Faces should generally have outward-facing normals.
               - Do not generate invalid or missing face references.

            4. Geometry Complexity:
               - Use enough geometry to accurately represent the requested
                 object.
               - Do NOT impose an arbitrary Minecraft-style face limit.
               - Do NOT artificially limit every model to a tiny number
                 of polygons.
               - At the same time, avoid excessive unnecessary geometry.
               - Use simple geometry for simple objects.
               - Use additional geometry for curved surfaces, organic
                 shapes, mechanical details, or complex silhouettes.
               - The final polygon count should be appropriate for the
                 requested object and suitable for use in a mobile 3D
                 application.

            5. Shape Construction:
               - Combine primitives and custom geometry when useful.
               - Cylinders may be represented using multiple radial segments.
               - Spheres may use latitude/longitude-style geometry.
               - Curved surfaces should use enough segments to appear
                 reasonably smooth for the requested style.
               - Mechanical objects may use separate components.
               - Organic objects may use carefully shaped polygonal meshes.
               - Low-poly requests should intentionally use fewer polygons.
               - Realistic requests should use sufficient geometry to
                 represent important curves and proportions.

            6. Style:
               - Follow the style explicitly requested by the user.
               - Realistic means realistic proportions and appropriate
                 curved/smooth geometry.
               - Low-poly means intentionally simplified polygonal geometry.
               - Stylized means prioritize the requested artistic appearance.
               - Cartoon means exaggerated but recognizable forms.
               - Anime means appropriate stylized proportions and shapes.
               - Sci-fi and fantasy objects should contain appropriate
                 structural and decorative details.
               - If no style is specified, create a clean and visually
                 appealing general-purpose 3D representation.

            7. Materials and Appearance:
               - Choose a suitable primary colorHex in #RRGGBB format.
               - suggestedTexture should describe the most appropriate
                 primary material or surface appearance.
               - Do NOT restrict suggestedTexture to Minecraft textures.
               - Possible values include, but are not limited to:
                 wood, metal, steel, iron, aluminum, gold, silver,
                 copper, glass, plastic, rubber, leather, fabric,
                 stone, concrete, ceramic, marble, granite, carbon_fiber,
                 chrome, brushed_metal, painted_metal, skin, fur,
                 clay, paper, cardboard, ice, crystal, dirt, sand,
                 or another material appropriate to the requested object.

            8. OBJ Output:
               - The OBJ content must contain real geometry.
               - It must contain at least one valid vertex line beginning
                 with "v ".
               - It must contain at least one valid face line beginning
                 with "f ".
               - The OBJ may optionally contain:
                 o object names
                 g groups
                 vt texture coordinates
                 vn vertex normals
                 s smoothing groups
                 mtllib references
                 usemtl material assignments
               - Only include optional OBJ features when they are valid
                 and useful.
               - Do not include Markdown code fences inside the OBJ data.

            9. JSON Output:
               Return ONLY a valid JSON object matching exactly this schema:

               {
                 "name": "Short Title of 3D Object",
                 "description": "Brief description of the generated 3D model",
                 "colorHex": "#RRGGBB",
                 "suggestedTexture": "appropriate_material",
                 "obj": "Complete Wavefront OBJ content"
               }

            10. JSON Safety:
                - The final response MUST be valid JSON.
                - The OBJ content is stored inside the JSON "obj" string.
                - Escape newline characters correctly.
                - Escape quotation marks inside the OBJ string when necessary.
                - Do not place comments or explanations outside the JSON object.
                - Do not wrap the JSON in Markdown code fences.

            QUALITY REQUIREMENT:

            Generate an actual model, not a placeholder.
            Prioritize:
            1. recognizable silhouette,
            2. correct proportions,
            3. important structural details,
            4. appropriate geometry,
            5. valid OBJ topology,
            6. efficient polygon usage.

            The result should be usable as a general-purpose 3D asset
            in a 3D engine and should visually correspond to the user's
            requested object.
        """.trimIndent()

        val historyContext =
            if (chatHistory.isNotEmpty()) {
                val relevantHistory =
                    chatHistory
                        .takeLast(6)
                        .joinToString("\n") { msg ->
                            if (msg.sender == "USER") {
                                "User: \"${msg.text}\""
                            } else {
                                "Assistant: \"${msg.text}\""
                            }
                        }

                "Prior Chat Context:\n$relevantHistory\n\n"
            } else {
                ""
            }

        val fullPrompt = """
            ${historyContext}User Request:
            "$prompt"

            Generate the 3D model now according to the instructions
            and output ONLY the JSON object.
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

        var name = prompt
            .trim()
            .replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase()
                } else {
                    it.toString()
                }
            }

        var description = "3D model of $prompt"
        var colorHex = "#38BDF8"
        var suggestedTexture = "generic_material"
        var objContent = ""

        // Strategy 1: Parse structured JSON.
        try {
            val json = JSONObject(cleanText)

            name = json.optString("name", name)
            description = json.optString("description", description)
            colorHex = json.optString("colorHex", colorHex)
            suggestedTexture =
                json.optString(
                    "suggestedTexture",
                    suggestedTexture
                )

            objContent = json.optString("obj", "")
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Standard JSON parse failed, trying regex extraction: ${e.message}"
            )
        }

        // Strategy 2: Regex extraction if raw JSON parsing failed
        // because of unescaped quotes/newlines.
        if (objContent.isBlank()) {
            Regex(
                "\"name\"\\s*:\\s*\"([^\"]+)\""
            ).find(cleanText)?.let {
                name = it.groupValues[1]
            }

            Regex(
                "\"description\"\\s*:\\s*\"([^\"]+)\""
            ).find(cleanText)?.let {
                description = it.groupValues[1]
            }

            Regex(
                "\"colorHex\"\\s*:\\s*\"(#[0-9a-fA-F]{6})\""
            ).find(cleanText)?.let {
                colorHex = it.groupValues[1]
            }

            Regex(
                "\"suggestedTexture\"\\s*:\\s*\"([^\"]+)\""
            ).find(cleanText)?.let {
                suggestedTexture = it.groupValues[1]
            }

            val objMatch =
                Regex(
                    "\"obj\"\\s*:\\s*\"([\\s\\S]*?)\"\\s*[,}]"
                ).find(cleanText)

            if (objMatch != null) {
                objContent = objMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }

        // Strategy 3: Direct Wavefront OBJ lines extraction
        // from the response.
        if (
            objContent.isBlank() &&
            cleanText.contains("v ") &&
            cleanText.contains("f ")
        ) {
            val lines =
                cleanText.lines().filter { line ->
                    val trimmed = line.trim()

                    trimmed.startsWith("#") ||
                            trimmed.startsWith("v ") ||
                            trimmed.startsWith("vn ") ||
                            trimmed.startsWith("vt ") ||
                            trimmed.startsWith("f ") ||
                            trimmed.startsWith("o ") ||
                            trimmed.startsWith("g ") ||
                            trimmed.startsWith("s ") ||
                            trimmed.startsWith("mtllib ") ||
                            trimmed.startsWith("usemtl ")
                }

            if (lines.isNotEmpty()) {
                objContent = lines.joinToString("\n")
            }
        }

        // Validate that the generated OBJ contains real vertices and faces.
        if (
            objContent.isBlank() ||
            !objContent.contains("v ") ||
            !objContent.contains("f ")
        ) {
            throw IllegalStateException(
                "Gemini generated an invalid 3D model " +
                        "(missing vertices or faces). Please try again " +
                        "with a descriptive prompt such as " +
                        "'a realistic medieval sword', " +
                        "'a sports car', 'a wooden chair', " +
                        "or 'a stylized dragon'."
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

        val name = prompt
            .trim()
            .replaceFirstChar {
                if (it.isLowerCase()) {
                    it.titlecase()
                } else {
                    it.toString()
                }
            }

        return when {
            p.contains("sword") ||
                    p.contains("blade") ||
                    p.contains("weapon") -> {

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

                GeneratedObjResult(
                    name,
                    "A stylized 3D sword crafted for battle",
                    "#38BDF8",
                    "metal",
                    obj
                )
            }

            p.contains("table") ||
                    p.contains("desk") -> {

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

                GeneratedObjResult(
                    name,
                    "A polished sturdy wooden table",
                    "#8B5A2B",
                    "wood",
                    obj
                )
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

                GeneratedObjResult(
                    name,
                    "A glowing faceted crystal shard",
                    "#00FFFF",
                    "crystal",
                    obj
                )
            }
        }
    }
}
