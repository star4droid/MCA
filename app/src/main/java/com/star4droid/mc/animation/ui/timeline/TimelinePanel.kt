package com.star4droid.mc.animation.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
    ActionBlockType.SCALE -> Color(0xFFEC4899)
    ActionBlockType.ANIMATION_CLIP -> Color(0xFFA855F7)
}

fun getActionBlockIcon(type: ActionBlockType): ImageVector = when (type) {
    ActionBlockType.WALK -> Icons.Default.DirectionsWalk
    ActionBlockType.RUN -> Icons.Default.DirectionsRun
    ActionBlockType.JUMP -> Icons.Default.FlightTakeoff
    ActionBlockType.WAVE -> Icons.Default.PanTool
    ActionBlockType.SLIDE_TO_POS -> Icons.Default.TrendingFlat
    ActionBlockType.MOVE_TO_POS -> Icons.Default.NearMe
    ActionBlockType.SCALE -> Icons.Default.AspectRatio
    ActionBlockType.ANIMATION_CLIP -> Icons.Default.Movie
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
    onApplyBlockSettings: (ActionBlock, Boolean) -> Unit,
    onRemoveCustomBlockSettings: (String) -> Unit,
    onClose: () -> Unit,
    onExportAnimation: ((String) -> Unit)? = null,
    onImportAnimation: ((java.io.File) -> Unit)? = null,
    getSavedAnimationFiles: (() -> List<java.io.File>)? = null,
    modifier: Modifier = Modifier
) {
    val duration = (timeline?.duration ?: 10.0f).coerceAtLeast(10f)
    var addBlockMenuOpen by remember { mutableStateOf(false) }
    var timelineMenuOpen by remember { mutableStateOf(false) }
    var importMenuOpen by remember { mutableStateOf(false) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var editingBlock by remember { mutableStateOf<ActionBlock?>(null) }

    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    // 80 dp per second scale for generous horizontal spacing
    val dpPerSecond = 80.dp
    val totalTimelineWidth = dpPerSecond * duration

    val blocks = timeline?.actionBlocks ?: emptyList()
    val maxRow = (blocks.maxOfOrNull { it.trackRow } ?: 2).coerceAtLeast(3)

    val density = LocalDensity.current

    // Action Block Settings Dialog
    if (editingBlock != null) {
        ActionBlockSettingsDialog(
            block = editingBlock!!,
            onDismiss = { editingBlock = null },
            onApplyToThis = { updated ->
                onApplyBlockSettings(updated, false)
            },
            onApplyToAll = { updated ->
                onApplyBlockSettings(updated, true)
            },
            onRemoveCustom = { blockId ->
                onRemoveCustomBlockSettings(blockId)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF50F172A))
            .border(1.dp, Color(0xFF334155))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // 1. Top Control Bar (Clean Material 3 Icons in Horizontally Scrollable Row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Playback controls
            IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Reset", tint = Color(0xFFE2E8F0), modifier = Modifier.size(18.dp))
            }

            // Decreased play button size
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFFEF4444) else Color(0xFF22C55E))
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(onClick = onToggleLoop, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (isLooping) Icons.Default.Repeat else Icons.Default.RepeatOne,
                    contentDescription = "Loop",
                    tint = if (isLooping) Color(0xFF38BDF8) else Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Time display badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    "${String.format("%.2f", currentTime)}s / ${String.format("%.1f", duration)}s",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }

            // Add Action Block Button
            Box {
                Button(
                    onClick = { addBlockMenuOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.height(28.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
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

            // Export Animation File Button
            IconButton(
                onClick = {
                    val name = "anim_${System.currentTimeMillis() % 10000}"
                    onExportAnimation?.invoke(name)
                },
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF334155))
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export Animation File", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
            }

            // Import Animation File Button
            Box {
                IconButton(
                    onClick = { importMenuOpen = true },
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF334155))
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Import Animation", tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                }

                DropdownMenu(
                    expanded = importMenuOpen,
                    onDismissRequest = { importMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    val files = getSavedAnimationFiles?.invoke() ?: emptyList()
                    if (files.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No saved animations found", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                            onClick = { importMenuOpen = false }
                        )
                    } else {
                        files.forEach { file ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                },
                                text = { Text(file.nameWithoutExtension, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    importMenuOpen = false
                                    onImportAnimation?.invoke(file)
                                }
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Timeline", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
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
                            val blockWidth = (dpPerSecond * block.duration).coerceAtLeast(42.dp)
                            val rowY = (block.trackRow * 36 + 2).dp

                            var dragDeltaX by remember(block.id) { mutableStateOf(0f) }
                            var dragDeltaY by remember(block.id) { mutableStateOf(0f) }
                            var isDraggingBlock by remember(block.id) { mutableStateOf(false) }

                            val offsetX = startX + with(density) { dragDeltaX.toDp() }
                            val offsetY = rowY + with(density) { dragDeltaY.toDp() }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isBlockSelected || isDraggingBlock) blockColor else blockColor.copy(alpha = 0.88f),
                                shadowElevation = if (isDraggingBlock) 8.dp else if (isBlockSelected) 4.dp else 1.dp,
                                modifier = Modifier
                                    .offset(x = offsetX, y = offsetY)
                                    .width(blockWidth)
                                    .height(32.dp)
                                    .border(
                                        width = if (isDraggingBlock) 2.dp else if (isBlockSelected) 2.dp else 1.dp,
                                        color = if (isDraggingBlock) Color(0xFF38BDF8) else if (isBlockSelected) Color.White else Color(0x55FFFFFF),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .pointerInput(block.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                isDraggingBlock = true
                                                selectedBlockId = block.id
                                                dragDeltaX = 0f
                                                dragDeltaY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragDeltaX += dragAmount.x
                                                dragDeltaY += dragAmount.y
                                            },
                                            onDragEnd = {
                                                isDraggingBlock = false
                                                val deltaSec = dragDeltaX / dpPerSecond.toPx()
                                                val newStart = (block.startTime + deltaSec).coerceAtLeast(0f)
                                                val rowChange = Math.round(dragDeltaY / 36.dp.toPx()).toInt()
                                                val newRow = (block.trackRow + rowChange).coerceIn(0, maxRow)
                                                dragDeltaX = 0f
                                                dragDeltaY = 0f
                                                block.startTime = (Math.round(newStart * 10f) / 10f)
                                                block.trackRow = newRow
                                                onUpdateActionBlock(block)
                                            },
                                            onDragCancel = {
                                                isDraggingBlock = false
                                                dragDeltaX = 0f
                                                dragDeltaY = 0f
                                            }
                                        )
                                    }
                                    .clickable {
                                        selectedBlockId = if (selectedBlockId == block.id) null else block.id
                                        onSeek(block.startTime)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            Icons.Default.DragIndicator,
                                            contentDescription = "Long-press to drag",
                                            tint = Color(0x88FFFFFF),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            getActionBlockIcon(block.type),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            block.type.displayName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )

                                        if (block.hasCustomSettings) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Custom settings",
                                                tint = Color(0xFFFDE047),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }

                                        if (block.enablePositionMove) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                Icons.Default.NearMe,
                                                contentDescription = "Position movement enabled",
                                                tint = Color(0xFF86EFAC),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${String.format("%.1f", block.duration)}s",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xDDFFFFFF)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        IconButton(
                                            onClick = {
                                                editingBlock = block
                                                selectedBlockId = block.id
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${activeBlock.type.displayName} Block",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFF1F5F9)
                                )
                                if (activeBlock.hasCustomSettings) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Custom",
                                        fontSize = 9.sp,
                                        color = Color(0xFF2DD4BF),
                                        modifier = Modifier
                                            .background(Color(0xFF0F766E).copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                "Start: ${String.format("%.1f", activeBlock.startTime)}s | Dur: ${String.format("%.1f", activeBlock.duration)}s | Step: ${String.format("%.1f", activeBlock.stepSize)}x",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Controls: purely compact icons in horizontally scrollable row to avoid hidden buttons
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Quick toggle for enablePositionMove
                        IconButton(
                            onClick = {
                                activeBlock.enablePositionMove = !activeBlock.enablePositionMove
                                onUpdateActionBlock(activeBlock)
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeBlock.enablePositionMove) Color(0xFF15803D) else Color(0xFF334155))
                        ) {
                            Icon(
                                if (activeBlock.enablePositionMove) Icons.Default.NearMe else Icons.Default.DirectionsWalk,
                                contentDescription = if (activeBlock.enablePositionMove) "Position Move ON" else "Position Move OFF",
                                tint = if (activeBlock.enablePositionMove) Color(0xFF86EFAC) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Settings dialog button
                        IconButton(
                            onClick = { editingBlock = activeBlock },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        // Move row up
                        IconButton(
                            onClick = {
                                if (activeBlock.trackRow > 0) {
                                    activeBlock.trackRow -= 1
                                    onUpdateActionBlock(activeBlock)
                                }
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155))
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Row Up", tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                        }

                        // Move row down
                        IconButton(
                            onClick = {
                                activeBlock.trackRow += 1
                                onUpdateActionBlock(activeBlock)
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155))
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Row Down", tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                        }

                        // Target pos button (for Slide / Move)
                        if (activeBlock.type == ActionBlockType.SLIDE_TO_POS || activeBlock.type == ActionBlockType.MOVE_TO_POS) {
                            IconButton(
                                onClick = {
                                    if (selectedNodePosition != null) {
                                        activeBlock.targetPosition = selectedNodePosition.copy()
                                        onUpdateActionBlock(activeBlock)
                                    }
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.GpsFixed, contentDescription = "Set Target to Current Position", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Target scale button (for SCALE)
                        if (activeBlock.type == ActionBlockType.SCALE) {
                            IconButton(
                                onClick = {
                                    onUpdateActionBlock(activeBlock)
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEC4899))
                            ) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Scale Settings", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Delete Block
                        IconButton(
                            onClick = {
                                onRemoveActionBlock(activeBlock.id)
                                selectedBlockId = null
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Block", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
