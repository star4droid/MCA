package com.star4droid.mc.animation.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.animation.ActionBlock
import com.star4droid.mc.animation.engine.math.Vec3

@Composable
fun ActionBlockSettingsDialog(
    block: ActionBlock,
    onDismiss: () -> Unit,
    onApplyToThis: (ActionBlock) -> Unit,
    onApplyToAll: (ActionBlock) -> Unit,
    onRemoveCustom: (String) -> Unit
) {
    var duration by remember { mutableStateOf(block.duration) }
    var enablePositionMove by remember { mutableStateOf(block.enablePositionMove) }
    var stepSize by remember { mutableStateOf(block.stepSize) }
    var speed by remember { mutableStateOf(block.speed) }
    var vecX by remember { mutableStateOf(block.moveVector.x) }
    var vecY by remember { mutableStateOf(block.moveVector.y) }
    var vecZ by remember { mutableStateOf(block.moveVector.z) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsWalk,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${block.type.displayName} Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = block.name,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                // Custom settings status pill
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (block.hasCustomSettings) Color(0xFF0F766E).copy(alpha = 0.35f)
                            else Color(0xFF334155).copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (block.hasCustomSettings) Icons.Default.Star else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (block.hasCustomSettings) Color(0xFF2DD4BF) else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (block.hasCustomSettings) "Custom Block Settings (Protected from Apply to All)"
                        else "Default Global Settings",
                        fontSize = 11.sp,
                        color = if (block.hasCustomSettings) Color(0xFF2DD4BF) else Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle: Enable Position Move
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (enablePositionMove) Icons.Default.NearMe else Icons.Default.DirectionsWalk,
                                    contentDescription = null,
                                    tint = if (enablePositionMove) Color(0xFF22C55E) else Color(0xFFE2E8F0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Move Position",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                            Text(
                                if (enablePositionMove)
                                    "Character travels forward in 3D space while playing walk animation"
                                else
                                    "Character animates limbs (hands & legs) in-place without moving position",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Switch(
                            checked = enablePositionMove,
                            onCheckedChange = { enablePositionMove = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF22C55E),
                                uncheckedThumbColor = Color(0xFF64748B),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Block Duration
                Text(
                    text = "Duration: ${String.format("%.1f", duration)}s (Loops walk cycle until end)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
                Slider(
                    value = duration,
                    onValueChange = { duration = (Math.round(it * 10f) / 10f).coerceIn(0.5f, 20f) },
                    valueRange = 0.5f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF0284C7)
                    )
                )

                // Step Size / Stride Amplitude
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Limb Swing Amplitude (Step Size): ${String.format("%.2f", stepSize)}x",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
                Slider(
                    value = stepSize,
                    onValueChange = { stepSize = (Math.round(it * 100f) / 100f).coerceIn(0.2f, 3.0f) },
                    valueRange = 0.2f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF59E0B),
                        activeTrackColor = Color(0xFFD97706)
                    )
                )

                // Animation Speed Multiplier
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Walk Cadence Speed: ${String.format("%.2f", speed)}x",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = (Math.round(it * 100f) / 100f).coerceIn(0.25f, 3.0f) },
                    valueRange = 0.25f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFA855F7),
                        activeTrackColor = Color(0xFF7C3AED)
                    )
                )

                // Walk Vector Displacement (Visible when Position Move is Enabled)
                if (enablePositionMove) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Displacement Vector (Distance traveled):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VectorAdjuster(
                            label = "X",
                            value = vecX,
                            color = Color(0xFFEF4444),
                            onValueChange = { vecX = it },
                            modifier = Modifier.weight(1f)
                        )
                        VectorAdjuster(
                            label = "Y",
                            value = vecY,
                            color = Color(0xFF22C55E),
                            onValueChange = { vecY = it },
                            modifier = Modifier.weight(1f)
                        )
                        VectorAdjuster(
                            label = "Z",
                            value = vecZ,
                            color = Color(0xFF3B82F6),
                            onValueChange = { vecZ = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Direction Presets
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PresetChip("Forward (+Z)", onClick = { vecX = 0f; vecY = 0f; vecZ = 3f }, modifier = Modifier.weight(1f))
                        PresetChip("Backward (-Z)", onClick = { vecX = 0f; vecY = 0f; vecZ = -3f }, modifier = Modifier.weight(1f))
                        PresetChip("Left (-X)", onClick = { vecX = -3f; vecY = 0f; vecZ = 0f }, modifier = Modifier.weight(1f))
                        PresetChip("Right (+X)", onClick = { vecX = 3f; vecY = 0f; vecZ = 0f }, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                val currentUpdatedBlock = remember(duration, enablePositionMove, stepSize, speed, vecX, vecY, vecZ) {
                    block.copy(
                        duration = duration,
                        enablePositionMove = enablePositionMove,
                        stepSize = stepSize,
                        speed = speed,
                        moveVector = Vec3(vecX, vecY, vecZ)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onApplyToThis(currentUpdatedBlock)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply to This", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onApplyToAll(currentUpdatedBlock)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply to All", fontSize = 12.sp)
                    }
                }

                if (block.hasCustomSettings) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onRemoveCustom(block.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Custom Settings (Revert to Global)", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VectorAdjuster(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = color, fontSize = 11.sp)
        Text(
            text = String.format("%.1f", value),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { onValueChange(value - 0.5f) },
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
            ) {
                Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            IconButton(
                onClick = { onValueChange(value + 0.5f) },
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
            ) {
                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PresetChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF334155),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            color = Color(0xFFCBD5E1),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
        )
    }
}
