package com.star4droid.mc.animation.ai

import android.content.Context
import android.content.SharedPreferences

enum class AiProvider(val id: String, val displayName: String) {
    GEMINI("gemini", "Gemini AI"),
    OPENCODE("opencode", "OpenCode AI");

    companion object {
        fun fromId(id: String): AiProvider {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI
        }
    }
}

object AiProviderManager {
    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_SELECTED_PROVIDER = "selected_ai_provider"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedProvider(context: Context): AiProvider {
        val id = getPrefs(context).getString(KEY_SELECTED_PROVIDER, AiProvider.GEMINI.id) ?: AiProvider.GEMINI.id
        return AiProvider.fromId(id)
    }

    fun saveSelectedProvider(context: Context, provider: AiProvider) {
        getPrefs(context)
            .edit()
            .putString(KEY_SELECTED_PROVIDER, provider.id)
            .apply()
    }

    fun getActiveModelDisplayName(context: Context): String {
        return when (getSelectedProvider(context)) {
            AiProvider.GEMINI -> GeminiApiService.getSelectedModel(context)
            AiProvider.OPENCODE -> {
                val modelId = OpenCodeApiService.getSelectedModel(context)
                OpenCodeApiService.MODELS.firstOrNull { it.apiModelId == modelId }?.displayName ?: modelId
            }
        }
    }

    suspend fun generateAnimation(
        context: Context,
        prompt: String,
        targetType: String,
        previousAnimationJson: String? = null,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> = emptyList()
    ): String {
        return when (getSelectedProvider(context)) {
            AiProvider.OPENCODE -> OpenCodeApiService.generateAnimation(
                context, prompt, targetType, previousAnimationJson, chatHistory
            )
            AiProvider.GEMINI -> GeminiApiService.generateAnimation(
                context, prompt, targetType, previousAnimationJson, chatHistory
            )
        }
    }

    suspend fun generate3DObject(
        context: Context,
        prompt: String,
        chatHistory: List<com.star4droid.mc.animation.ui.ai_studio.AiChatMessage> = emptyList()
    ): GeminiApiService.GeneratedObjResult {
        return when (getSelectedProvider(context)) {
            AiProvider.OPENCODE -> OpenCodeApiService.generate3DObject(
                context, prompt, chatHistory
            )
            AiProvider.GEMINI -> GeminiApiService.generate3DObject(
                context, prompt, chatHistory
            )
        }
    }
}
