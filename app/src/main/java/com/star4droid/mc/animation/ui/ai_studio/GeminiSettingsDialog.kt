package com.star4droid.mc.animation.ui.ai_studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.ai.AiProvider
import com.star4droid.mc.animation.ai.AiProviderManager
import com.star4droid.mc.animation.ai.GeminiApiService
import com.star4droid.mc.animation.ai.OpenCodeApiService

@Composable
fun GeminiSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(AiProviderManager.getSelectedProvider(context)) }

    // Gemini states
    var geminiApiKey by remember { mutableStateOf(GeminiApiService.getApiKey(context)) }
    var selectedGeminiModel by remember { mutableStateOf(GeminiApiService.getSelectedModel(context)) }
    var isGeminiModelDropdownOpen by remember { mutableStateOf(false) }

    // OpenCode states
    var opencodeApiKey by remember { mutableStateOf(OpenCodeApiService.getApiKey(context)) }
    var selectedOpenCodeModel by remember { mutableStateOf(OpenCodeApiService.getSelectedModel(context)) }
    var isOpenCodeModelDropdownOpen by remember { mutableStateOf(false) }

    val presetGeminiModels = listOf(
        "gemini-flash-latest",
        "gemini-2.5-flash",
        "gemini-1.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-2.5-pro",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-pro-preview"
    )

    val openCodeModels = OpenCodeApiService.MODELS

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Provider Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                // Provider Switcher Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedProvider == AiProvider.GEMINI) Color(0xFF8B5CF6) else Color.Transparent)
                            .clickable { selectedProvider = AiProvider.GEMINI }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gemini AI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedProvider == AiProvider.OPENCODE) Color(0xFF8B5CF6) else Color.Transparent)
                            .clickable { selectedProvider = AiProvider.OPENCODE }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OpenCode AI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Divider(color = Color(0xFF334155))

                // GEMINI CONFIGURATION SECTION
                if (selectedProvider == AiProvider.GEMINI) {
                    // API Key Field
                    Column {
                        Text("Gemini API Key", fontSize = 11.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = { geminiApiKey = it },
                            placeholder = { Text("Enter your Gemini API key", fontSize = 11.sp, color = Color(0xFF64748B)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF8B5CF6)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedBorderColor = Color(0xFF8B5CF6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (GeminiApiService.isUsingBuildConfigKey(context)) {
                            Text(
                                "✓ Active: Using GEMINI_API_KEY from AI Studio Secrets",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981)
                            )
                        } else if (geminiApiKey.isNotBlank()) {
                            Text(
                                "✓ Custom Gemini API Key configured",
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8)
                            )
                        } else {
                            Text(
                                "No Gemini API key detected. Set one here or in Secrets.",
                                fontSize = 10.sp,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    // Model Selector Spinner
                    Column {
                        Text("Gemini Model", fontSize = 11.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedTextField(
                                value = selectedGeminiModel,
                                onValueChange = { selectedGeminiModel = it },
                                label = { Text("Model Name") },
                                trailingIcon = {
                                    IconButton(onClick = { isGeminiModelDropdownOpen = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A),
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            DropdownMenu(
                                expanded = isGeminiModelDropdownOpen,
                                onDismissRequest = { isGeminiModelDropdownOpen = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                presetGeminiModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                m,
                                                color = Color.White,
                                                fontWeight = if (m == selectedGeminiModel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedGeminiModel = m
                                            isGeminiModelDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // OPENCODE CONFIGURATION SECTION
                if (selectedProvider == AiProvider.OPENCODE) {
                    // API Key Field
                    Column {
                        Text("OpenCode Zen API Key", fontSize = 11.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = opencodeApiKey,
                            onValueChange = { opencodeApiKey = it },
                            placeholder = { Text("Enter your OpenCode Zen API key", fontSize = 11.sp, color = Color(0xFF64748B)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF8B5CF6)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedBorderColor = Color(0xFF8B5CF6),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (opencodeApiKey.isNotBlank()) {
                            Text(
                                "✓ OpenCode Zen API Key configured",
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8)
                            )
                        } else {
                            Text(
                                "Enter your OpenCode Zen API key to enable OpenCode models.",
                                fontSize = 10.sp,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    // Model Selector Spinner
                    Column {
                        Text("OpenCode Model", fontSize = 11.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            val activeDisplayName = openCodeModels.firstOrNull { it.apiModelId == selectedOpenCodeModel }?.displayName ?: selectedOpenCodeModel
                            OutlinedTextField(
                                value = activeDisplayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model Name") },
                                trailingIcon = {
                                    IconButton(onClick = { isOpenCodeModelDropdownOpen = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().clickable { isOpenCodeModelDropdownOpen = true },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A),
                                    focusedBorderColor = Color(0xFF8B5CF6),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            DropdownMenu(
                                expanded = isOpenCodeModelDropdownOpen,
                                onDismissRequest = { isOpenCodeModelDropdownOpen = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                openCodeModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${m.displayName} (${m.apiModelId})",
                                                color = Color.White,
                                                fontWeight = if (m.apiModelId == selectedOpenCodeModel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedOpenCodeModel = m.apiModelId
                                            isOpenCodeModelDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        AiProviderManager.saveSelectedProvider(context, selectedProvider)
                        GeminiApiService.saveApiKey(context, geminiApiKey)
                        GeminiApiService.saveSelectedModel(context, selectedGeminiModel)
                        OpenCodeApiService.saveApiKey(context, opencodeApiKey)
                        OpenCodeApiService.saveSelectedModel(context, selectedOpenCodeModel)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("Save Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
