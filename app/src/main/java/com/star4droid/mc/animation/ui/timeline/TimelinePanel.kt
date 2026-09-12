package com.star4droid.mc.animation.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.mc.animation.animation.ActionBlock
import com.star4droid.mc.animation.animation.ActionBlockType
import com.star4droid.mc.animation.animation.TimelineAsset
import com.star4droid.mc.animation.engine.math.Vec3

fun getActionBlockColor(type: ActionBlockType): Color = when (type) {
    ActionBlockType.WALK -> Color(0xFF10B981)
    ActionBlockType.RUN -> Color(0xFFF59E0B)
    ActionBlockType.JUMP -> Color(0xFF0EA5E9)
    ActionBlockType.WAVE -> Color(0xFF8B5CF6)
    ActionBlockType.SLIDE_TO_POS -> Color(0xFF6366F1)
    ActionBlockType.MOVE_TO_POS -> Color(0xFF14B8A6)
}

fun getActionBlockIcon(type: ActionBlockType): ImageVector = when (type) {
    ActionBlockType.WALK -> Icons.Default.DirectionsWalk
    ActionBlockType.RUN -> Icons.Default.DirectionsRun
    ActionBlockType.JUMP -> Icons.Default.FlightTakeoff
    ActionBlockType.WAVE -> Icons.Default.PanTool
    ActionBlockType.SLIDE_TO_POS -> Icons.Default.TrendingFlat
    ActionBlockType.MOVE_TO_POS -> Icons.Default.NearMe
}

@Composable
fun TimelinePanel(
    timeline: TimelineAsset?,
    allTimelines: List<TimelineAsset>,
    currentTime: Float,
    isPlaying: Boolean,
    isLooping: Boolean,
    selectedNodeId: String?,
    selectedNodePosition: Vec3?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onReset: () -> Unit,
    onToggleLoop: () -> Unit,
    onSelectTimeline: (String) -> Unit,
    onCreateTimeline: (String) -> Unit,
    onAddActionBlock: (ActionBlockType) -> Unit,
    onRemoveActionBlock: (String) -> Unit,
    onUpdateActionBlock: (ActionBlock) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = (timeline?.duration ?: 10.0f).coerceAtLeast(10f)
    var addBlockMenuOpen by remember { mutableStateOf(false) }
    var timelineMenuOpen by remember { mutableStateOf(false) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    // 80 dp per second scale for generous horizontal spacing
    val dpPerSecond = 80.dp
    val totalTimelineWidth = dpPerSecond * duration

    val blocks = timeline?.actionBlocks ?: emptyList()
    val maxRow = (blocks.maxOfOrNull { it.trackRow } ?: 2).coerceAtLeast(3)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF50F172A))
            .border(1.dp, Color(0xFF334155))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // 1. Top Control Bar (Clean Material 3 Icons, NO emojis)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Playback controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onReset, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Reset", tint = Color(0xFFE2E8F0))
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFFEF4444) else Color(0xFF22C55E))
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }

                IconButton(onClick = onToggleLoop, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (isLooping) Icons.Default.Repeat else Icons.Default.RepeatOne,
                        contentDescription = "Loop",
                        tint = if (isLooping) Color(0xFF38BDF8) else Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Time display badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${String.format("%.2f", currentTime)}s / ${String.format("%.1f", duration)}s",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            // Right side: Timeline dropdown, Add Block button, Close button
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Add Action Block Button
                Box {
                    Button(
                        onClick = { addBlockMenuOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Block", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = addBlockMenuOpen,
                        onDismissRequest = { addBlockMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        ActionBlockType.values().forEach { blockType ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        getActionBlockIcon(blockType),
                                        contentDescription = null,
                                        tint = getActionBlockColor(blockType),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Column {
                                        Text(blockType.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${blockType.defaultDuration}s duration", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    addBlockMenuOpen = false
                                    onAddActionBlock(blockType)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Timeline", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Scrollable Timeline Canvas (Both Horizontally and Vertically)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF090D16))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
        ) {
            // Horizontal scroll container
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScroll)
            ) {
                // Main Timeline Tracks container with vertical scroll
                Column(
                    modifier = Modifier
                        .width(totalTimelineWidth + 60.dp)
                        .fillMaxHeight()
                        .verticalScroll(verticalScroll)
                ) {
                    // Time Ruler Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(Color(0xFF161F33))
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val time = (offset.x / (size.width - 60) * duration).coerceIn(0f, duration)
                                    onSeek(time)
                                }
                            }
                    ) {
                        // Ruler tick marks
                        val totalSeconds = duration.toInt() + 1
                        for (s in 0..totalSeconds) {
                            val xPos = dpPerSecond * s
                            Box(
                                modifier = Modifier
                                    .offset(x = xPos)
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(Color(0xFF475569))
                            )
                            Text(
                                "${s}s",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .offset(x = xPos + 2.dp, y = 2.dp)
                            )
                        }
                    }

                    // Block Tracks Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(((maxRow + 1) * 36).dp)
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val time = (offset.x / (size.width - 60) * duration).coerceIn(0f, duration)
                                    onSeek(time)
                                }
                            }
                    ) {
                        // Horizontal track row guidelines
                        for (r in 0..maxRow) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = (r * 36).dp)
                                    .height(1.dp)
                                    .background(Color(0xFF1E293B))
                            )
                        }

                        // Render Action Blocks
                        blocks.forEach { block ->
                            val isBlockSelected = block.id == selectedBlockId
                            val blockColor = getActionBlockColor(block.type)
                            val startX = dpPerSecond * block.startTime
                            val blockWidth = (dpPerSecond * block.duration).coerceAtLeast(36.dp)
                            val rowY = (block.trackRow * 36 + 2).dp

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isBlockSelected) blockColor else blockColor.copy(alpha = 0.85f),
                                shadowElevation = if (isBlockSelected) 4.dp else 1.dp,
                                modifier = Modifier
                                    .offset(x = startX, y = rowY)
                                    .width(blockWidth)
                                    .height(32.dp)
                                    .border(
                                        width = if (isBlockSelected) 2.dp else 1.dp,
                                        color = if (isBlockSelected) Color.White else Color(0x66FFFFFF),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        selectedBlockId = if (selectedBlockId == block.id) null else block.id
                                        onSeek(block.startTime)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            getActionBlockIcon(block.type),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            block.type.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        "${String.format("%.1f", block.duration)}s",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xDDFFFFFF)
                                    )
                                }
                            }
                        }

                        // Playhead Indicator Line
                        val playheadX = dpPerSecond * currentTime
                        Box(
                            modifier = Modifier
                                .offset(x = playheadX)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Color(0xFFEF4444))
                        )
                    }
                }
            }
        }

        // 3. Selected Action Block Inspector & Quick Settings
        val activeBlock = blocks.firstOrNull { it.id == selectedBlockId }
        if (activeBlock != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Block name and icon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            getActionBlockIcon(activeBlock.type),
                            contentDescription = null,
                            tint = getActionBlockColor(activeBlock.type),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "${activeBlock.type.displayName} Block",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFF1F5F9)
                            )
                            Text(
                                "Start: ${String.format("%.1f", activeBlock.startTime)}s | Dur: ${String.format("%.1f", activeBlock.duration)}s | Row: ${activeBlock.trackRow}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Controls: Row Up/Down, Time Adjust, Set Target to Current Position, Delete
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Move row up/down
                        IconButton(
                            onClick = {
                                if (activeBlock.trackRow > 0) {
                                    activeBlock.trackRow -= 1
                                    onUpdateActionBlock(activeBlock)
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Row Up", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                activeBlock.trackRow += 1
                                onUpdateActionBlock(activeBlock)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Row Down", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        }

                        // If Slide or Move block: button to select current object position!
                        if (activeBlock.type == ActionBlockType.SLIDE_TO_POS || activeBlock.type == ActionBlockType.MOVE_TO_POS) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    if (selectedNodePosition != null) {
                                        activeBlock.targetPosition = selectedNodePosition.copy()
                                        onUpdateActionBlock(activeBlock)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Target: Current Pos", fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Delete Block
                        IconButton(
                            onClick = {
                                onRemoveActionBlock(activeBlock.id)
                                selectedBlockId = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Block", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
