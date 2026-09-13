package com.star4droid.mc.animation.ai

import android.content.Context
import com.star4droid.mc.animation.ui.ai_studio.AiChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<AiChatMessage> = emptyList()
)

object AiChatHistoryRepository {

    private fun getSessionsFile(context: Context): File {
        return File(context.filesDir, "ai_chat_sessions.json")
    }

    fun loadSessions(context: Context): List<ChatSession> {
        val file = getSessionsFile(context)
        if (!file.exists()) {
            val initial = listOf(ChatSession(title = "Welcome Session", messages = defaultWelcomeMessages()))
            saveSessions(context, initial)
            return initial
        }

        val sessions = mutableListOf<ChatSession>()
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val sObj = array.getJSONObject(i)
                val msgList = mutableListOf<AiChatMessage>()
                val msgArray = sObj.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until msgArray.length()) {
                    val mObj = msgArray.getJSONObject(j)
                    msgList.add(
                        AiChatMessage(
                            id = mObj.optString("id", UUID.randomUUID().toString()),
                            sender = mObj.optString("sender", "AI"),
                            text = mObj.optString("text", ""),
                            animationJson = mObj.optString("animationJson").ifEmpty { null },
                            animationName = mObj.optString("animationName").ifEmpty { null },
                            objContent = mObj.optString("objContent").ifEmpty { null },
                            objName = mObj.optString("objName").ifEmpty { null },
                            objColorHex = mObj.optString("objColorHex").ifEmpty { null },
                            objTextureId = mObj.optString("objTextureId").ifEmpty { null },
                            timestamp = mObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                sessions.add(
                    ChatSession(
                        id = sObj.optString("id", UUID.randomUUID().toString()),
                        title = sObj.optString("title", "Chat Session"),
                        timestamp = sObj.optLong("timestamp", System.currentTimeMillis()),
                        messages = msgList
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (sessions.isNotEmpty()) sessions else listOf(ChatSession(title = "Welcome Session", messages = defaultWelcomeMessages()))
    }

    fun saveSessions(context: Context, sessions: List<ChatSession>) {
        try {
            val array = JSONArray()
            for (session in sessions) {
                array.put(JSONObject().apply {
                    put("id", session.id)
                    put("title", session.title)
                    put("timestamp", session.timestamp)
                    val msgArray = JSONArray()
                    for (msg in session.messages) {
                        msgArray.put(JSONObject().apply {
                            put("id", msg.id)
                            put("sender", msg.sender)
                            put("text", msg.text)
                            msg.animationJson?.let { put("animationJson", it) }
                            msg.animationName?.let { put("animationName", it) }
                            msg.objContent?.let { put("objContent", it) }
                            msg.objName?.let { put("objName", it) }
                            msg.objColorHex?.let { put("objColorHex", it) }
                            msg.objTextureId?.let { put("objTextureId", it) }
                            put("timestamp", msg.timestamp)
                        })
                    }
                    put("messages", msgArray)
                })
            }
            getSessionsFile(context).writeText(array.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSession(context: Context, sessionId: String): List<ChatSession> {
        val current = loadSessions(context).filter { it.id != sessionId }
        saveSessions(context, current)
        return current
    }

    fun defaultWelcomeMessages(): List<AiChatMessage> {
        return listOf(
            AiChatMessage(
                sender = "AI",
                text = "Welcome to AI Animation Studio! Describe an animation for your character or block (e.g. 'shake the block' or 'dance and wave')."
            )
        )
    }
}
