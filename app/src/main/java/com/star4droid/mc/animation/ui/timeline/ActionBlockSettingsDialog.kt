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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.animation.ActionBlock
import com.star4droid.mc.animation.animation.ActionBlockType
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
    var targetScaleX by remember { mutableStateOf(block.scaleVector.x) }
    var targetScaleY by remember { mutableStateOf(block.scaleVector.y) }
    var targetScaleZ by remember { mutableStateOf(block.scaleVector.z) }
    var clipFileName by remember { mutableStateOf(block.clipFileName ?: "Walk Cycle") }

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
                            imageVector = getActionBlockIcon(block.type),
                            contentDescription = null,
                            tint = getActionBlockColor(block.type),
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

                Spacer(modifier = Modifier.height(12.dp))

                // Common Property: Duration
                Text(
                    text = "Duration: ${String.format("%.1f", duration)}s",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
                Slider(
                    value = duration,
                    onValueChange = { duration = (Math.round(it * 10f) / 10f).coerceIn(0.2f, 30f) },
                    valueRange = 0.2f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF0284C7)
                    )
                )

                // BLOCK-SPECIFIC PROPERTIES
                when (block.type) {
                    ActionBlockType.WALK, ActionBlockType.RUN -> {
                        // Position Move Toggle
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Move Position in 3D", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                                    Text("Translate character forward during limb animation", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = enablePositionMove,
                                    onCheckedChange = { enablePositionMove = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF22C55E))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Limb Swing Amplitude (Step Size): ${String.format("%.2f", stepSize)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = stepSize,
                            onValueChange = { stepSize = (Math.round(it * 100f) / 100f).coerceIn(0.1f, 3.0f) },
                            valueRange = 0.1f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B), activeTrackColor = Color(0xFFD97706))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Cadence Speed Multiplier: ${String.format("%.2f", speed)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = speed,
                            onValueChange = { speed = (Math.round(it * 100f) / 100f).coerceIn(0.25f, 4.0f) },
                            valueRange = 0.25f..4.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFA855F7), activeTrackColor = Color(0xFF7C3AED))
                        )

                        if (enablePositionMove) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Displacement Distance Vector:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                VectorAdjuster("X", vecX, Color(0xFFEF4444), { vecX = it }, Modifier.weight(1f))
                                VectorAdjuster("Y", vecY, Color(0xFF22C55E), { vecY = it }, Modifier.weight(1f))
                                VectorAdjuster("Z", vecZ, Color(0xFF3B82F6), { vecZ = it }, Modifier.weight(1f))
                            }
                        }
                    }

                    ActionBlockType.JUMP -> {
                        Text("Jump Height Multiplier: ${String.format("%.2f", stepSize)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = stepSize,
                            onValueChange = { stepSize = (Math.round(it * 100f) / 100f).coerceIn(0.2f, 4.0f) },
                            valueRange = 0.2f..4.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF0EA5E9), activeTrackColor = Color(0xFF0284C7))
                        )
                    }

                    ActionBlockType.WAVE -> {
                        Text("Wave Hand Cadence Speed: ${String.format("%.2f", speed)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = speed,
                            onValueChange = { speed = (Math.round(it * 100f) / 100f).coerceIn(0.2f, 3.0f) },
                            valueRange = 0.2f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF8B5CF6), activeTrackColor = Color(0xFF7C3AED))
                        )
                    }

                    ActionBlockType.SLIDE_TO_POS, ActionBlockType.MOVE_TO_POS -> {
                        Text("Target Position Offset:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VectorAdjuster("X", vecX, Color(0xFFEF4444), { vecX = it }, Modifier.weight(1f))
                            VectorAdjuster("Y", vecY, Color(0xFF22C55E), { vecY = it }, Modifier.weight(1f))
                            VectorAdjuster("Z", vecZ, Color(0xFF3B82F6), { vecZ = it }, Modifier.weight(1f))
                        }
                    }

                    ActionBlockType.SCALE -> {
                        Text("Target Scale Vector:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VectorAdjuster("Scale X", targetScaleX, Color(0xFFEF4444), { targetScaleX = it.coerceAtLeast(0.05f) }, Modifier.weight(1f))
                            VectorAdjuster("Scale Y", targetScaleY, Color(0xFF22C55E), { targetScaleY = it.coerceAtLeast(0.05f) }, Modifier.weight(1f))
                            VectorAdjuster("Scale Z", targetScaleZ, Color(0xFF3B82F6), { targetScaleZ = it.coerceAtLeast(0.05f) }, Modifier.weight(1f))
                        }
                    }

                    ActionBlockType.ANIMATION_CLIP -> {
                        Text("Animation Clip Preset:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(4.dp))
                        val clipOptions = listOf("Walk Cycle", "Run Sprint", "Wave Gesture", "Idle Motion", "Combat Attack")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            clipOptions.take(3).forEach { clipName ->
                                val isSel = clipFileName == clipName
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) Color(0xFFA855F7) else Color(0xFF334155))
                                        .clickable { clipFileName = clipName }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(clipName, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Clip Playback Speed: ${String.format("%.2f", speed)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = speed,
                            onValueChange = { speed = (Math.round(it * 100f) / 100f).coerceIn(0.2f, 4.0f) },
                            valueRange = 0.2f..4.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFA855F7), activeTrackColor = Color(0xFF7C3AED))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Limb Motion Amplitude: ${String.format("%.2f", stepSize)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = stepSize,
                            onValueChange = { stepSize = (Math.round(it * 100f) / 100f).coerceIn(0.1f, 3.0f) },
                            valueRange = 0.1f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF0284C7))
                        )
                    }

                    ActionBlockType.LOOK_LEFT, ActionBlockType.LOOK_RIGHT -> {
                        Text("Head Turn Amplitude: ${String.format("%.2f", stepSize)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = stepSize,
                            onValueChange = { stepSize = (Math.round(it * 100f) / 100f).coerceIn(0.1f, 3.0f) },
                            valueRange = 0.1f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF0284C7))
                        )
                    }

                    else -> {
                        Text("Action Motion Amplitude: ${String.format("%.2f", stepSize)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = stepSize,
                            onValueChange = { stepSize = (Math.round(it * 100f) / 100f).coerceIn(0.1f, 3.0f) },
                            valueRange = 0.1f..3.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF0284C7))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("Action Playback Speed: ${String.format("%.2f", speed)}x", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                        Slider(
                            value = speed,
                            onValueChange = { speed = (Math.round(it * 100f) / 100f).coerceIn(0.2f, 4.0f) },
                            valueRange = 0.2f..4.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFA855F7), activeTrackColor = Color(0xFF7C3AED))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val currentUpdatedBlock = remember(
                    duration, enablePositionMove, stepSize, speed,
                    vecX, vecY, vecZ, targetScaleX, targetScaleY, targetScaleZ, clipFileName
                ) {
                    block.copy(
                        duration = duration,
                        enablePositionMove = enablePositionMove,
                        stepSize = stepSize,
                        speed = speed,
                        moveVector = Vec3(vecX, vecY, vecZ),
                        scaleVector = Vec3(targetScaleX, targetScaleY, targetScaleZ),
                        clipFileName = clipFileName
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
