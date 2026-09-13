package com.star4droid.mc.animation.ui.ai_studio

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.mc.animation.ui.blocks.CustomBlockPreset

data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val text: String,
    val animationJson: String? = null,
    val animationName: String? = null,
    val objContent: String? = null,
    val objName: String? = null,
    val objColorHex: String? = null,
    val objTextureId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun AiAnimationChatOverlay(
    messages: List<AiChatMessage>,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onPlayAnimation: (String) -> Unit,
    onSaveCustomBlock: (name: String, json: String) -> Unit,
    onShowObjResult: (name: String, objContent: String, colorHex: String?, textureId: String?) -> Unit = { _, _, _, _ -> },
    onSaveObj: (name: String, objContent: String, colorHex: String?, textureId: String?) -> Unit = { _, _, _, _ -> },
    onAddToScene: ((name: String, objContent: String, colorHex: String?, textureId: String?) -> Unit)? = null,
    isObjectMode: Boolean = false,
    onNewChat: () -> Unit,
    onClearChat: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseChat: () -> Unit,
    onRetryMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }

    // Rotating Star Loader Animation
    val infiniteTransition = rememberInfiniteTransition()
    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xEE0F172A),
        tonalElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.75f)
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp)
        ) {
            // Header Action Icons Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp).then(if (isLoading) Modifier.rotate(starRotation) else Modifier)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isLoading) "AI Working..." else "AI Animator Chat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenHistory, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.History, contentDescription = "History List", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onNewChat, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.AddComment, contentDescription = "New Chat", tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClearChat, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "API Settings", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCloseChat, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Chat", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Divider(color = Color(0xFF334155), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Chat Messages List
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isUser = msg.sender == "USER"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isUser) 12.dp else 2.dp,
                                bottomEnd = if (isUser) 2.dp else 12.dp
                            ),
                            color = if (isUser) Color(0xFF2563EB) else Color(0xFF1E293B),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    msg.text,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )

                                // Interactive [Animation Created - Play] Card
                                if (!msg.animationJson.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        msg.animationName ?: "Animation Created",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF4ADE80)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Button(
                                                    onClick = {
                                                        onPlayAnimation(msg.animationJson)
                                                        onCloseChat()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Play Animation", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        onSaveCustomBlock(msg.animationName ?: "Custom AI Block", msg.animationJson)
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Save Block", fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Interactive Generated OBJ Card
                                if (!msg.objContent.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Category, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        msg.objName ?: "3D Object Created",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFFFCD34D)
                                                    )
                                                }
                                                if (!msg.objColorHex.isNullOrBlank()) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = try { Color(android.graphics.Color.parseColor(msg.objColorHex)) } catch (e: Exception) { Color(0xFFF59E0B) },
                                                        modifier = Modifier.size(12.dp)
                                                    ) {}
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Button(
                                                    onClick = {
                                                        onShowObjResult(
                                                            msg.objName ?: "Object",
                                                            msg.objContent,
                                                            msg.objColorHex,
                                                            msg.objTextureId
                                                        )
                                                        onCloseChat()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Preview", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                }

                                                if (onAddToScene != null) {
                                                    Button(
                                                        onClick = {
                                                            onAddToScene(
                                                                msg.objName ?: "Object",
                                                                msg.objContent,
                                                                msg.objColorHex,
                                                                msg.objTextureId
                                                            )
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text("Add to Scene", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        onSaveObj(
                                                            msg.objName ?: "Saved Object",
                                                            msg.objContent,
                                                            msg.objColorHex,
                                                            msg.objTextureId
                                                        )
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text("Save", fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Retry Button under AI Messages
                                if (!isUser) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Surface(
                                            onClick = {
                                                val prevUserMsg = messages.take(messages.indexOf(msg)).lastOrNull { it.sender == "USER" }
                                                val promptToRetry = prevUserMsg?.text ?: msg.text
                                                if (promptToRetry.isNotBlank() && !isLoading) {
                                                    onRetryMessage(promptToRetry)
                                                }
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Retry", fontSize = 9.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Rotating Stars Loading Indicator
                if (isLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Generating...",
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(22.dp).rotate(starRotation)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isObjectMode) "Gemini AI is generating your 3D model..." else "Gemini AI is crafting your animation...",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Message Input Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (isObjectMode) "Describe a 3D object (e.g. diamond sword, chest, chair)..." else "Ask AI to create or edit animation...",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier.size(44.dp).background(if (inputText.isNotBlank()) Color(0xFF8B5CF6) else Color(0xFF334155), CircleShape)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send Prompt", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
