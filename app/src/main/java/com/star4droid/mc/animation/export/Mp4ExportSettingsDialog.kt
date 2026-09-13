package com.star4droid.mc.animation.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class VideoResolution(val name: String, val width: Int, val height: Int)

val RESOLUTIONS = listOf(
    VideoResolution("480p", 854, 480),
    VideoResolution("720p", 1280, 720),
    VideoResolution("1080p", 1920, 1080)
)

val FPS_OPTIONS = listOf(30, 60)

@Composable
fun Mp4ExportSettingsDialog(
    timelineDuration: Float,
    isExporting: Boolean = false,
    exportProgress: Float = 0f,
    onDismiss: () -> Unit,
    onStartExport: (Mp4ExportConfig) -> Unit
) {
    var selectedResIdx by remember { mutableIntStateOf(1) } // Default 720p
    var selectedFpsIdx by remember { mutableIntStateOf(0) } // Default 30 FPS
    var selectedEngineMode by remember { mutableStateOf(Mp4ExportEngineMode.PIXEL_COPY_BITMAP) }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Export MP4 Video",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                if (!isExporting) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Duration info
            Text(
                "Duration: ${String.format("%.1f", timelineDuration)}s",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(12.dp))

            // Rendering Engine Selector Chips Row
            Text("Rendering Engine", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Mp4ExportEngineMode.values().forEach { mode ->
                    val selected = mode == selectedEngineMode
                    Box(
                        modifier = Modifier
                            .width(190.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color(0xFF1E3A8A) else Color(0xFF0F172A))
                            .border(
                                1.dp,
                                if (selected) Color(0xFF3B82F6) else Color(0xFF334155),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !isExporting) { selectedEngineMode = mode }
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    mode.displayName,
                                    color = if (selected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                mode.description,
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                maxLines = 3
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Resolution selector
            Text("Resolution", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RESOLUTIONS.forEachIndexed { idx, res ->
                    val selected = idx == selectedResIdx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF2563EB) else Color(0xFF0F172A))
                            .border(1.dp, if (selected) Color(0xFF3B82F6) else Color(0xFF334155), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isExporting) { selectedResIdx = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            res.name,
                            color = if (selected) Color.White else Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // FPS selector
            Text("Frame Rate", color = Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FPS_OPTIONS.forEachIndexed { idx, fps ->
                    val selected = idx == selectedFpsIdx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF2563EB) else Color(0xFF0F172A))
                            .border(1.dp, if (selected) Color(0xFF3B82F6) else Color(0xFF334155), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isExporting) { selectedFpsIdx = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$fps FPS",
                            color = if (selected) Color.White else Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Export progress or render button
            if (isExporting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.size(48.dp),
                        color = Color(0xFF22C55E),
                        trackColor = Color(0xFF334155)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Rendering... ${(exportProgress * 100).toInt()}%",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            } else {
                Button(
                    onClick = {
                        val res = RESOLUTIONS[selectedResIdx]
                        val fps = FPS_OPTIONS[selectedFpsIdx]
                        val bitRate = when (selectedResIdx) {
                            0 -> 4_000_000
                            2 -> 12_000_000
                            else -> 8_000_000
                        }
                        onStartExport(
                            Mp4ExportConfig(
                                width = res.width,
                                height = res.height,
                                fps = fps,
                                bitRate = bitRate,
                                duration = timelineDuration,
                                engineMode = selectedEngineMode
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Render Video", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
