package com.star4droid.mc.animation.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.ai.AiExecutionResult
import com.star4droid.mc.animation.ai.SideAiAssistant
import com.star4droid.mc.animation.ui.editor.EditorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SideAiDialog(
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assistant = remember { SideAiAssistant(context) }

    var promptText by remember { mutableStateOf("") }
    var createInNewGroup by remember { mutableStateOf(true) }
    var apiKeyText by remember { mutableStateOf("") }
    var showApiKeyInput by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<AiExecutionResult?>(null) }
    var historyOfNodeIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val examplePrompts = listOf(
        "Build a wooden house [dimension x:6 y:4 z:8]",
        "Build a stone tower 8 blocks high",
        "Build stone stairs width 4",
        "Spawn Steve and make him walk",
        "Spawn Zombie and make him jump",
        "Make selected object wave",
        "Group selected objects as NewStructure",
        "List all objects and positions in scene",
        "Reset camera to front view"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF8B5CF6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Side AI Assistant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFF1F5F9)
                            )
                            Text(
                                "World Building, Characters & Animation",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Prompt Input
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        placeholder = {
                            Text("e.g. Build a house [dimension x:6 y:4 z:8] or spawn Steve and make him walk", color = Color(0xFF64748B), fontSize = 12.sp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedTextColor = Color(0xFFF1F5F9),
                            unfocusedTextColor = Color(0xFFF1F5F9)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Options Row: New Group Checkbox & Undo Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { createInNewGroup = !createInNewGroup }
                        ) {
                            Checkbox(
                                checked = createInNewGroup,
                                onCheckedChange = { createInNewGroup = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF8B5CF6),
                                    checkmarkColor = Color.White
                                )
                            )
                            Text(
                                "New group for structures",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }

                        // Undo Button
                        if (historyOfNodeIds.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    for (id in historyOfNodeIds) {
                                        viewModel.sceneGraph.removeNode(id)
                                    }
                                    historyOfNodeIds = emptyList()
                                    lastResult = AiExecutionResult(true, "Undid previous AI generation.")
                                    viewModel.saveProject()
                                    viewModel.triggerRecomposition()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Undo AI Action", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons (Generate)
                    Button(
                        onClick = {
                            if (promptText.isNotBlank() && !isProcessing) {
                                isProcessing = true
                                scope.launch {
                                    val result = assistant.processPrompt(
                                        prompt = promptText,
                                        sceneGraph = viewModel.sceneGraph,
                                        camera = viewModel.camera,
                                        timeline = viewModel.getActiveTimeline(),
                                        selectedNodeId = viewModel.uiState.value.selectedNodeId,
                                        createInNewGroup = createInNewGroup,
                                        apiKeyOverride = if (apiKeyText.isNotBlank()) apiKeyText.trim() else null
                                    )
                                    isProcessing = false
                                    lastResult = result
                                    if (result.createdNodeIds.isNotEmpty()) {
                                        historyOfNodeIds = result.createdNodeIds
                                        viewModel.selectNode(result.createdNodeIds.first())
                                    }
                                    viewModel.saveProject()
                                    viewModel.triggerRecomposition()
                                }
                            }
                        },
                        enabled = promptText.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Processing with AI...", fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Execute AI Command", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Result card
                    lastResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (res.success) Color(0xFF064E3B) else Color(0xFF450A0A))
                                .border(1.dp, if (res.success) Color(0xFF10B981) else Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                res.message,
                                color = if (res.success) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Suggestion Chips
                    Text(
                        "Quick Prompts & Templates:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (example in examplePrompts) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { promptText = example }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(example, fontSize = 11.sp, color = Color(0xFFE2E8F0))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Optional API Key Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showApiKeyInput = !showApiKeyInput },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (showApiKeyInput) "Hide Custom API Key" else "Set Custom Gemini API Key (Optional)",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    AnimatedVisibility(visible = showApiKeyInput) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedTextField(
                                value = apiKeyText,
                                onValueChange = { apiKeyText = it },
                                placeholder = { Text("Gemini API Key (Leave blank to use built-in engine)", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = Color(0xFF475569),
                                    focusedTextColor = Color(0xFFF1F5F9),
                                    unfocusedTextColor = Color(0xFFF1F5F9)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
