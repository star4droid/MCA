package com.star4droid.mc.animation.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.mc.animation.animation.AnimationTrack
import com.star4droid.mc.animation.animation.Interpolation
import com.star4droid.mc.animation.animation.Keyframe
import com.star4droid.mc.animation.animation.TimelineAsset

@Composable
fun TimelinePanel(
    timeline: TimelineAsset?,
    allTimelines: List<TimelineAsset>,
    currentTime: Float,
    isPlaying: Boolean,
    isLooping: Boolean,
    selectedNodeId: String?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onReset: () -> Unit,
    onToggleLoop: () -> Unit,
    onSelectTimeline: (String) -> Unit,
    onCreateTimeline: (String) -> Unit,
    onDeleteKeyframe: (trackId: String, keyframeId: String) -> Unit,
    onUpdateKeyframeInterp: (trackId: String, keyframeId: String, interp: Interpolation) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = timeline?.duration ?: 10.0f
    val relevantTracks = remember(timeline, selectedNodeId) {
        if (selectedNodeId == null || timeline == null) emptyList()
        else timeline.getTracksForObject(selectedNodeId)
    }

    var selectedKeyframeInfo by remember { mutableStateOf<Pair<AnimationTrack, Keyframe>?>(null) }
    var timelineMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF0131B2E))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Control Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Playback controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Text("⏮️", fontSize = 14.sp)
                }
                IconButton(onClick = { onSeek((currentTime - 0.1f).coerceAtLeast(0f)) }, modifier = Modifier.size(32.dp)) {
                    Text("◀️", fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 16.sp)
                }
                IconButton(onClick = { onSeek((currentTime + 0.1f).coerceAtMost(duration)) }, modifier = Modifier.size(32.dp)) {
                    Text("▶️", fontSize = 12.sp)
                }
                IconButton(onClick = onToggleLoop, modifier = Modifier.size(32.dp)) {
                    Text(if (isLooping) "🔁" else "➡️", fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timecode
                val curMin = (currentTime / 60).toInt()
                val curSec = (currentTime % 60).toInt()
                val curMs = ((currentTime % 1f) * 1000).toInt()
                val totalMin = (duration / 60).toInt()
                val totalSec = (duration % 60).toInt()
                Text(
                    text = String.format("%02d:%02d.%03d / %02d:%02d", curMin, curSec, curMs, totalMin, totalSec),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Timeline Switcher & Close
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Button(
                        onClick = { timelineMenuOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(timeline?.name ?: "Timelines", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                    }
                    DropdownMenu(
                        expanded = timelineMenuOpen,
                        onDismissRequest = { timelineMenuOpen = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        for (tl in allTimelines) {
                            DropdownMenuItem(
                                text = { Text(tl.name, color = Color(0xFFE2E8F0)) },
                                onClick = {
                                    timelineMenuOpen = false
                                    onSelectTimeline(tl.id)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("+ New Timeline", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold) },
                            onClick = {
                                timelineMenuOpen = false
                                onCreateTimeline("Timeline ${allTimelines.size + 1}")
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Time Ruler & Scrubber Bar
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0F172A))
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        val targetTime = (offset.x / size.width) * duration
                        onSeek(targetTime.coerceIn(0f, duration))
                    }
                }
                .pointerInput(duration) {
                    detectDragGestures { change, _ ->
                        val targetTime = (change.position.x / size.width) * duration
                        onSeek(targetTime.coerceIn(0f, duration))
                    }
                }
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val scrubberX = (currentTime / duration) * totalWidthPx

            // Time ruler tick marks
            Row(modifier = Modifier.fillMaxWidth()) {
                val ticks = 10
                for (i in 0..ticks) {
                    val sec = (duration / ticks) * i
                    Text(
                        "${sec.toInt()}s",
                        fontSize = 8.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Red Scrubber Line & Head
            Box(
                modifier = Modifier
                    .offset(x = (scrubberX - 6).dp.coerceAtLeast(0.dp))
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .align(Alignment.CenterStart)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Keyframe Tracks
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (relevantTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (selectedNodeId == null) "Select an object to inspect animation tracks"
                        else "No animated keyframes yet. Use 🔑 icons in Inspector to keyframe channels.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            } else {
                for (track in relevantTracks) {
                    TrackRow(
                        track = track,
                        duration = duration,
                        currentTime = currentTime,
                        selectedKeyframe = selectedKeyframeInfo?.second,
                        onSelectKeyframe = { kf -> selectedKeyframeInfo = Pair(track, kf) },
                        onSeek = onSeek
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }

        // Selected Keyframe Editor Sheet Bar
        selectedKeyframeInfo?.let { (track, kf) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Keyframe @ ${String.format("%.2fs", kf.time)} = ${String.format("%.2f", kf.value)}",
                    fontSize = 11.sp,
                    color = Color(0xFFE2E8F0)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Curve:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.width(4.dp))
                    Interpolation.values().forEach { interp ->
                        val isSelected = kf.interpolation == interp
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF334155))
                                .clickable { onUpdateKeyframeInterp(track.id, kf.id, interp) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(interp.name, fontSize = 9.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = {
                            onDeleteKeyframe(track.id, kf.id)
                            selectedKeyframeInfo = null
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    track: AnimationTrack,
    duration: Float,
    currentTime: Float,
    selectedKeyframe: Keyframe?,
    onSelectKeyframe: (Keyframe) -> Unit,
    onSeek: (Float) -> Unit
) {
    val cleanProp = track.propertyPath.replace("transform.", "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E293B)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = cleanProp,
            fontSize = 10.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .width(100.dp)
                .padding(start = 6.dp)
        )

        // Track timeline channel
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .background(Color(0xFF0F172A))
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()

            // Keyframe diamonds
            for (kf in track.keyframes) {
                val kfX = (kf.time / duration).coerceIn(0f, 1f) * totalWidthPx
                val isSelected = (kf.id == selectedKeyframe?.id)

                Box(
                    modifier = Modifier
                        .offset(x = (kfX - 6).dp.coerceAtLeast(0.dp), y = 4.dp)
                        .size(10.dp)
                        .rotate(45f)
                        .background(if (isSelected) Color(0xFF38BDF8) else Color(0xFFF59E0B))
                        .clickable {
                            onSeek(kf.time)
                            onSelectKeyframe(kf)
                        }
                )
            }
        }
    }
}
