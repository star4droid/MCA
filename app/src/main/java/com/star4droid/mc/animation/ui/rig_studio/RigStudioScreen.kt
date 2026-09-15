package com.star4droid.mc.animation.ui.rig_studio

import android.opengl.GLSurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.viewinterop.AndroidView
import com.star4droid.mc.animation.engine.camera.EditorCamera
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.rendering.SceneRenderer
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.skeleton.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.star4droid.mc.animation.ui.files.FileBrowserDialog
import com.star4droid.mc.animation.ui.files.FileCategory
import com.star4droid.mc.animation.utils.ObjImporter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RigStudioScreen(
    projectId: String? = null,
    projectName: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedModelFile by remember { mutableStateOf<File?>(null) }
    var isFileBrowserOpen by remember { mutableStateOf(false) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }

    var skeleton by remember { mutableStateOf(Skeleton.createStandardHumanoidSkeleton()) }
    var selectedBoneIdx by remember { mutableStateOf(-1) }
    var modelAlpha by remember { mutableStateOf(0.6f) }
    var isBoneRemapOpen by remember { mutableStateOf(false) }

    val camera = remember {
        EditorCamera().apply {
            target = Vec3(0f, 1f, 0f)
            distance = 4.0f
            pitch = 10f
            yaw = 45f
        }
    }
    val sceneGraph = remember { SceneGraph() }
    val historyManager = remember { com.star4droid.mc.animation.engine.history.HistoryManager() }
    val gizmoController = remember { com.star4droid.mc.animation.engine.gizmo.GizmoController(sceneGraph, historyManager) }
    val renderer = remember {
        SceneRenderer(context, sceneGraph, camera, gizmoController).apply {
            activeSkeleton = skeleton
            modelAlpha = 0.6f
        }
    }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    // Synchronize skeleton and alpha to renderer
    LaunchedEffect(skeleton, selectedBoneIdx, modelAlpha) {
        renderer.activeSkeleton = skeleton
        renderer.selectedBoneIndex = selectedBoneIdx
        renderer.modelAlpha = modelAlpha
        glSurfaceViewRef?.requestRender()
    }

    // Load model and .meta when file changes
    LaunchedEffect(selectedModelFile) {
        val file = selectedModelFile ?: return@LaunchedEffect
        try {
            sceneGraph.nodes.clear()
            sceneGraph.rootNodeIds.clear()

            val node = if (file.extension.equals("obj", ignoreCase = true)) {
                ObjImporter.parseObjFile(file)
            } else {
                // Future GLTF/GLB fallback node
                ObjImporter.parseObjFile(file)
            }
            sceneGraph.addNode(node)

            // Check if saved .meta exists
            val existingMeta = RigMetadata.loadForModel(file)
            if (existingMeta != null) {
                skeleton = RigMetadata.metadataToSkeleton(existingMeta)
                modelAlpha = existingMeta.transparency
            } else {
                // Auto-rig on initial load
                val objData = node.objModelData
                skeleton = if (objData != null) {
                    AutoRigEstimator.estimateFromObj(objData)
                } else {
                    Skeleton.createStandardHumanoidSkeleton()
                }
            }
            skeleton.updateWorldMatrices()
            renderer.activeSkeleton = skeleton
            glSurfaceViewRef?.requestRender()
            Toast.makeText(context, "Loaded model: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            modelLoadError = "Failed to load model '${file.name}':\n\n${e.localizedMessage ?: e.toString()}\n\n" +
                e.stackTrace.take(6).joinToString("\n") { "  at $it" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bones & Rigging Studio 🦴", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            selectedModelFile?.name ?: "No model selected (Click folder to choose)",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isFileBrowserOpen = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Open 3D Model", tint = Color(0xFF38BDF8))
                    }
                    IconButton(
                        onClick = {
                            val file = selectedModelFile
                            if (file != null) {
                                val meta = RigMetadata.skeletonToMetadata(skeleton, file.name, modelAlpha)
                                val ok = RigMetadata.saveForModel(file, meta)
                                if (ok) {
                                    Toast.makeText(context, "Rig metadata saved (${file.name}.meta) 💾", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save .meta", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Select a model first", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save Rig", tint = Color(0xFF22C55E))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0B101B))
        ) {
            // 1. OpenGL 3D Viewport
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        glSurfaceViewRef = this
                    }
                }
            )

            // 2. Transparency Slider Controller Overlay (Top Right)
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .width(220.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD1E293B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Model Transparency", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text("${(modelAlpha * 100).toInt()}%", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = modelAlpha,
                        onValueChange = { modelAlpha = it },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8)
                        )
                    )
                }
            }

            // 3. Floating Left Tool Panel: Bones list & Auto-Rig
            Card(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(12.dp)
                    .width(230.dp)
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE0F172A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Bones Hierarchy (${skeleton.bones.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Action buttons: Auto-Rig Any Pose & Reset
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                val node = sceneGraph.nodes.values.firstOrNull()
                                val objData = node?.objModelData
                                if (objData != null) {
                                    skeleton = AutoRigEstimator.estimateFromObj(objData)
                                    skeleton.updateWorldMatrices()
                                    Toast.makeText(context, "Auto-Rig calculated for pose! ⚡", Toast.LENGTH_SHORT).show()
                                } else {
                                    skeleton = Skeleton.createStandardHumanoidSkeleton()
                                    skeleton.updateWorldMatrices()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                        ) {
                            Text("Auto-Rig ⚡", fontSize = 10.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { isBoneRemapOpen = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1))
                        ) {
                            Text("Remap 🔄", fontSize = 10.sp, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(skeleton.bones) { idx, bone ->
                            val isSelected = selectedBoneIdx == idx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFFF59E0B).copy(alpha = 0.3f) else Color(0xFF1E293B))
                                    .clickable { selectedBoneIdx = idx }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Adjust,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    bone.name,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color(0xFFF59E0B) else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Bone control actions (Add / Remove)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                val parentIdx = selectedBoneIdx.takeIf { it >= 0 } ?: 0
                                val newBone = Bone(name = "bone_${skeleton.bones.size}", parentIndex = parentIdx)
                                newBone.localTransform = newBone.localTransform.copy(position = Vec3(0f, 0.25f, 0f))
                                skeleton.addBone(newBone)
                                skeleton.updateWorldMatrices()
                                val copy = skeleton.copySkeleton()
                                skeleton = copy
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Text("+ Bone", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                if (selectedBoneIdx in 0 until skeleton.bones.size) {
                                    skeleton.bones.removeAt(selectedBoneIdx)
                                    selectedBoneIdx = -1
                                    skeleton.updateWorldMatrices()
                                    val copy = skeleton.copySkeleton()
                                    skeleton = copy
                                }
                            },
                            enabled = selectedBoneIdx >= 0,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Text("Delete", fontSize = 10.sp)
                        }
                    }
                }
            }

            // Bone Remapping Dialog
            if (isBoneRemapOpen) {
                AlertDialog(
                    onDismissRequest = { isBoneRemapOpen = false },
                    title = { Text("Standard Bone Remapping", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Map model bones to standard humanoid animation roles:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(skeleton.bones) { _, bone ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(bone.name, fontSize = 12.sp, color = Color.White)
                                        val autoRole = StandardBoneRole.fromString(bone.name)?.name ?: "NONE"
                                        Text(
                                            bone.standardRole?.name ?: "AUTO ($autoRole)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { isBoneRemapOpen = false }) {
                            Text("Done", color = Color(0xFF22C55E))
                        }
                    }
                )
            }

            // File Browser Dialog for picking 3D model
            if (isFileBrowserOpen) {
                FileBrowserDialog(
                    projectId = projectId,
                    projectName = projectName,
                    onDismiss = { isFileBrowserOpen = false },
                    onFileSelected = { file ->
                        isFileBrowserOpen = false
                        selectedModelFile = file
                    }
                )
            }

            // Model Loading Error Dialog with Copy and Cancel
            modelLoadError?.let { errText ->
                AlertDialog(
                    onDismissRequest = { modelLoadError = null },
                    icon = {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
                    },
                    title = {
                        Text("Model Loading Failed", fontWeight = FontWeight.Bold, color = Color(0xFFF87171), fontSize = 16.sp)
                    },
                    text = {
                        Column {
                            Text(
                                "An error occurred while parsing the 3D model file:",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    item {
                                        Text(
                                            errText,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFFFCA5A5)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("MCA Model Error", errText)
                                cm.setPrimaryClip(clip)
                                Toast.makeText(context, "Error copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                                modelLoadError = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Error", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { modelLoadError = null }) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                    },
                    containerColor = Color(0xFF1E293B)
                )
            }
        }
    }
}
