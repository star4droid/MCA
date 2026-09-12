package com.star4droid.mc.animation.ui.files

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.assets.BuiltInAssets
import java.io.File
import java.io.FileOutputStream

enum class FileCategory(val label: String) {
    ALL("All Files"),
    IMAGES("Textures & Images"),
    SOUNDS("Audio & Sounds"),
    MODELS("Models & Data")
}

@Composable
fun FileBrowserDialog(
    onDismiss: () -> Unit,
    onFileSelected: (File) -> Unit = {},
    onTextureImported: (textureId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val baseAppDir = remember { context.getExternalFilesDir(null) ?: context.filesDir }

    val texturesDir = remember { File(baseAppDir, "textures").apply { if (!exists()) mkdirs() } }
    val soundsDir = remember { File(baseAppDir, "sounds").apply { if (!exists()) mkdirs() } }
    val modelsDir = remember { File(baseAppDir, "models").apply { if (!exists()) mkdirs() } }

    var currentCategory by remember { mutableStateOf(FileCategory.ALL) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isPlayingAudio by remember { mutableStateOf<String?>(null) }
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun refreshFiles() {
        val allFiles = mutableListOf<File>()
        texturesDir.listFiles()?.let { allFiles.addAll(it) }
        soundsDir.listFiles()?.let { allFiles.addAll(it) }
        modelsDir.listFiles()?.let { allFiles.addAll(it) }

        val filtered = when (currentCategory) {
            FileCategory.ALL -> allFiles
            FileCategory.IMAGES -> allFiles.filter { it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }
            FileCategory.SOUNDS -> allFiles.filter { it.extension.lowercase() in listOf("mp3", "wav", "ogg", "m4a") }
            FileCategory.MODELS -> allFiles.filter { it.extension.lowercase() in listOf("json", "obj", "bbmodel", "gltf") }
        }
        files = filtered.sortedByDescending { it.lastModified() }
    }

    LaunchedEffect(currentCategory) {
        refreshFiles()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        }
    }

    fun playSound(file: File) {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    isPlayingAudio = null
                    it.release()
                    activeMediaPlayer = null
                }
            }
            activeMediaPlayer = mp
            isPlayingAudio = file.absolutePath
        } catch (e: Exception) {
            statusMessage = "Could not play audio: ${e.message}"
        }
    }

    fun stopSound() {
        activeMediaPlayer?.stop()
        activeMediaPlayer?.release()
        activeMediaPlayer = null
        isPlayingAudio = null
    }

    // Generic file importer
    val fileImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: ""
                val targetDir = when {
                    mime.startsWith("audio/") -> soundsDir
                    mime.startsWith("image/") -> texturesDir
                    else -> when (currentCategory) {
                        FileCategory.SOUNDS -> soundsDir
                        FileCategory.IMAGES -> texturesDir
                        FileCategory.MODELS -> modelsDir
                        else -> texturesDir
                    }
                }

                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    mime.contains("mp3") -> "mp3"
                    mime.contains("wav") -> "wav"
                    mime.contains("ogg") -> "ogg"
                    else -> "dat"
                }

                val fileName = "custom_${System.currentTimeMillis()}.$ext"
                val destFile = File(targetDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (destFile.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")) {
                    val bitmap = BitmapFactory.decodeFile(destFile.absolutePath)
                    if (bitmap != null) {
                        val textureId = "custom_${destFile.nameWithoutExtension}"
                        BuiltInAssets.registerCustomTexture(textureId, bitmap)
                        onTextureImported(textureId)
                    }
                }

                statusMessage = "Imported ${destFile.name} successfully!"
                refreshFiles()
            } catch (e: Exception) {
                statusMessage = "Failed to import file: ${e.message}"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "App File & Asset Manager",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFF1F5F9)
                            )
                            Text(
                                "Textures, Sounds, Models & Custom Files",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FileCategory.values().forEach { cat ->
                        val isSelected = currentCategory == cat
                        Surface(
                            onClick = { currentCategory = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF334155),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = cat.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Import Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            when (currentCategory) {
                                FileCategory.IMAGES -> fileImporter.launch("image/*")
                                FileCategory.SOUNDS -> fileImporter.launch("audio/*")
                                else -> fileImporter.launch("*/*")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            when (currentCategory) {
                                FileCategory.IMAGES -> "Import Image"
                                FileCategory.SOUNDS -> "Import Sound"
                                FileCategory.MODELS -> "Import Model"
                                else -> "Import File"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { refreshFiles() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(msg, fontSize = 11.sp, color = Color(0xFF4ADE80))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // File List
                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                when (currentCategory) {
                                    FileCategory.SOUNDS -> Icons.Default.Audiotrack
                                    FileCategory.IMAGES -> Icons.Default.Image
                                    else -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No ${currentCategory.label.lowercase()} found.\nUse the Import button to add files.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(files) { file ->
                            val isSelected = selectedFile == file
                            val isImage = file.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")
                            val isAudio = file.extension.lowercase() in listOf("mp3", "wav", "ogg", "m4a")
                            val isPlayingThis = isPlayingAudio == file.absolutePath

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF334155) else Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFile = file }
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color(0xFF38BDF8) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isImage) {
                                        val bitmap = remember(file.path) {
                                            try { BitmapFactory.decodeFile(file.path) } catch (e: Exception) { null }
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = file.name,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                                            )
                                        } else {
                                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(40.dp))
                                        }
                                    } else if (isAudio) {
                                        IconButton(
                                            onClick = {
                                                if (isPlayingThis) stopSound() else playSound(file)
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(if (isPlayingThis) Color(0xFFEF4444) else Color(0xFF2563EB), RoundedCornerShape(6.dp))
                                        ) {
                                            Icon(
                                                if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayingThis) "Pause" else "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            file.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFFF1F5F9),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${file.length() / 1024} KB • .${file.extension.uppercase()}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isImage) {
                                            Button(
                                                onClick = {
                                                    onFileSelected(file)
                                                    val bitmap = try { BitmapFactory.decodeFile(file.path) } catch (e: Exception) { null }
                                                    if (bitmap != null) {
                                                        val textureId = "custom_${file.nameWithoutExtension}"
                                                        BuiltInAssets.registerCustomTexture(textureId, bitmap)
                                                        onTextureImported(textureId)
                                                    }
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Apply", fontSize = 10.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = {
                                                file.delete()
                                                refreshFiles()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
