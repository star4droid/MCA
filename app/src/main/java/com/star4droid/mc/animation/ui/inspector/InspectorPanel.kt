package com.star4droid.mc.animation.ui.inspector

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.animation.presets.PresetType
import com.star4droid.mc.animation.assets.BuiltInAssets
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType
import com.star4droid.mc.animation.engine.scene.Transform

fun clampRotationForPart(partType: com.star4droid.mc.animation.engine.scene.CharacterPartType?, axis: Int, value: Float): Float {
    return when (partType) {
        com.star4droid.mc.animation.engine.scene.CharacterPartType.HEAD -> when (axis) {
            0 -> value.coerceIn(-60f, 50f)
            1 -> value.coerceIn(-85f, 85f)
            else -> value.coerceIn(-35f, 35f)
        }
        com.star4droid.mc.animation.engine.scene.CharacterPartType.RIGHT_ARM -> when (axis) {
            0 -> value.coerceIn(-180f, 90f)
            1 -> value.coerceIn(-45f, 45f)
            else -> value.coerceIn(-135f, 15f)
        }
        com.star4droid.mc.animation.engine.scene.CharacterPartType.LEFT_ARM -> when (axis) {
            0 -> value.coerceIn(-180f, 90f)
            1 -> value.coerceIn(-45f, 45f)
            else -> value.coerceIn(-15f, 135f)
        }
        com.star4droid.mc.animation.engine.scene.CharacterPartType.RIGHT_LEG -> when (axis) {
            0 -> value.coerceIn(-85f, 80f)
            1 -> value.coerceIn(-25f, 25f)
            else -> value.coerceIn(-30f, 20f)
        }
        com.star4droid.mc.animation.engine.scene.CharacterPartType.LEFT_LEG -> when (axis) {
            0 -> value.coerceIn(-85f, 80f)
            1 -> value.coerceIn(-25f, 25f)
            else -> value.coerceIn(-20f, 30f)
        }
        com.star4droid.mc.animation.engine.scene.CharacterPartType.BODY -> when (axis) {
            0 -> value.coerceIn(-35f, 35f)
            1 -> value.coerceIn(-45f, 45f)
            else -> value.coerceIn(-25f, 25f)
        }
        else -> value
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InspectorPanel(
    node: SceneNode?,
    allNodes: List<SceneNode>,
    isSelectionLocked: Boolean,
    onToggleSelectionLock: () -> Unit,
    onRenameNode: (String) -> Unit,
    onReparentNode: (newParentId: String?) -> Unit,
    onUpdateTransform: (Transform) -> Unit,
    onAddKeyframe: (propertyPath: String, value: Float) -> Unit,
    onKeyframeAll: () -> Unit,
    onApplyPreset: (PresetType) -> Unit,
    onUpdateMaterial: (textureAssetId: String, opacity: Float) -> Unit,
    onClose: () -> Unit,
    onSelectNode: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showParentDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Color(0xEE1E293B))
            .padding(12.dp)
    ) {
        // Header with Lock Selection and Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Inspector",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Lock Selection Button
                IconButton(
                    onClick = onToggleSelectionLock,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isSelectionLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isSelectionLocked) "Selection Locked" else "Selection Unlocked",
                        tint = if (isSelectionLocked) Color(0xFFF59E0B) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }
        }

        if (node == null) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(
                "No object selected.\nTap an object in viewport or hierarchy.",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                lineHeight = 18.sp
            )
            return
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Name Property & Type (with Rename Dialog trigger)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRenameDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF3B82F6))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(node.type.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            node.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFF1F5F9),
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Parent Property (with Scrollable Tree / List selection)
            val parentNode = node.parentId?.let { pId -> allNodes.firstOrNull { it.id == pId } }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showParentDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Parent", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Text(
                                parentNode?.name ?: "None (Scene Root)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFFF1F5F9)
                            )
                        }
                    }
                    Text("Change", fontSize = 11.sp, color = Color(0xFF38BDF8))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Keyframe All Button
            Button(
                onClick = onKeyframeAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Keyframe Transform", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Transform Section
            Text("Transform", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(6.dp))

            val t = node.animatedTransform

            // Position
            if (node.type == SceneNodeType.CHARACTER_PART) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Anchored to Skeleton Joint", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Limb is anchored to socket. Move character root to reposition in scene.", fontSize = 10.sp, color = Color(0xFF64748B))
                    if (node.parentId != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onSelectNode(node.parentId!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Select Character Root", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            } else {
                TransformChannelGroup(
                    title = "Position",
                    values = listOf(t.position.x, t.position.y, t.position.z),
                    labels = listOf("X", "Y", "Z"),
                    colors = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6)),
                    step = 0.5f,
                    onValueChange = { idx, newVal ->
                        val newPos = when (idx) {
                            0 -> t.position.copy(x = newVal)
                            1 -> t.position.copy(y = newVal)
                            else -> t.position.copy(z = newVal)
                        }
                        onUpdateTransform(t.copy(position = newPos))
                    },
                    onKeyframe = { idx ->
                        val path = when (idx) {
                            0 -> "transform.position.x"
                            1 -> "transform.position.y"
                            else -> "transform.position.z"
                        }
                        val v = when (idx) {
                            0 -> t.position.x
                            1 -> t.position.y
                            else -> t.position.z
                        }
                        onAddKeyframe(path, v)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rotation
            TransformChannelGroup(
                title = if (node.type == SceneNodeType.CHARACTER_PART) "Rotation (Skeletal Limits)" else "Rotation",
                values = listOf(t.rotation.x, t.rotation.y, t.rotation.z),
                labels = listOf("X", "Y", "Z"),
                colors = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6)),
                step = 15f,
                onValueChange = { idx, newVal ->
                    val clampedVal = if (node.type == SceneNodeType.CHARACTER_PART) {
                        clampRotationForPart(node.characterPartType, idx, newVal)
                    } else {
                        newVal
                    }
                    val newRot = when (idx) {
                        0 -> t.rotation.copy(x = clampedVal)
                        1 -> t.rotation.copy(y = clampedVal)
                        else -> t.rotation.copy(z = clampedVal)
                    }
                    onUpdateTransform(t.copy(rotation = newRot))
                },
                onKeyframe = { idx ->
                    val path = when (idx) {
                        0 -> "transform.rotation.x"
                        1 -> "transform.rotation.y"
                        else -> "transform.rotation.z"
                    }
                    val v = when (idx) {
                        0 -> t.rotation.x
                        1 -> t.rotation.y
                        else -> t.rotation.z
                    }
                    onAddKeyframe(path, v)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scale
            if (node.type == SceneNodeType.CHARACTER_PART) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scale Entire Body Only", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Individual limb scaling is locked. Scale the character root to resize the entire body.", fontSize = 10.sp, color = Color(0xFF64748B))
                    if (node.parentId != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onSelectNode(node.parentId!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Select Character to Scale", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            } else {
                TransformChannelGroup(
                    title = if (node.type == SceneNodeType.CHARACTER_ROOT) "Scale (Entire Character)" else "Scale",
                    values = listOf(t.scale.x, t.scale.y, t.scale.z),
                    labels = listOf("X", "Y", "Z"),
                    colors = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6)),
                    step = 0.2f,
                    onValueChange = { idx, newVal ->
                        val coerced = newVal.coerceAtLeast(0.05f)
                        val newScale = if (node.type == SceneNodeType.CHARACTER_ROOT) {
                            Vec3(coerced, coerced, coerced)
                        } else {
                            when (idx) {
                                0 -> t.scale.copy(x = coerced)
                                1 -> t.scale.copy(y = coerced)
                                else -> t.scale.copy(z = coerced)
                            }
                        }
                        onUpdateTransform(t.copy(scale = newScale))
                    },
                    onKeyframe = { idx ->
                        val path = when (idx) {
                            0 -> "transform.scale.x"
                            1 -> "transform.scale.y"
                            else -> "transform.scale.z"
                        }
                        val v = when (idx) {
                            0 -> t.scale.x
                            1 -> t.scale.y
                            else -> t.scale.z
                        }
                        onAddKeyframe(path, v)
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets (for Character)
            if (node.type == SceneNodeType.CHARACTER_ROOT || node.type == SceneNodeType.CHARACTER_PART) {
                Text("Animation Presets", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PresetType.values().forEach { preset ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF334155))
                                .clickable { onApplyPreset(preset) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                preset.displayName,
                                fontSize = 11.sp,
                                color = Color(0xFFF1F5F9),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Material / Block Texture
            if (node.type == SceneNodeType.BLOCK || node.type == SceneNodeType.GROUND) {
                Text("Block Texture", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BuiltInAssets.BLOCK_TEXTURES.forEach { def ->
                        val isSelected = node.material.textureAssetId == def.id
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(def.sideColor))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF22C55E) else Color(0xFF475569),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onUpdateMaterial(def.id, node.material.opacity) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Opacity
                Text("Opacity: ${(node.material.opacity * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Slider(
                    value = node.material.opacity,
                    onValueChange = { onUpdateMaterial(node.material.textureAssetId, it) },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF3B82F6), activeTrackColor = Color(0xFF3B82F6))
                )
            }
        }
    }

    // 1. Rename Node Dialog ("add 'name' property (input dialog)")
    if (showRenameDialog && node != null) {
        var newNameText by remember { mutableStateOf(node.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Object", color = Color(0xFFE2E8F0)) },
            text = {
                OutlinedTextField(
                    value = newNameText,
                    onValueChange = { newNameText = it },
                    label = { Text("Object Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNameText.isNotBlank()) {
                            onRenameNode(newNameText.trim())
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // 2. Parent Selection Dialog ("add 'parent' property (select from list, scrollable tree)")
    if (showParentDialog && node != null) {
        Dialog(onDismissRequest = { showParentDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Select Parent Object",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFF1F5F9)
                        )
                        IconButton(onClick = { showParentDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option: No parent (Root)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onReparentNode(null)
                                showParentDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("None (Scene Root)", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Or select another object:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Scrollable List / Tree of all eligible nodes
                    val eligibleNodes = remember(allNodes, node) {
                        allNodes.filter { it.id != node.id && !node.children.contains(it.id) }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(eligibleNodes) { itemNode ->
                            val isCurrentParent = node.parentId == itemNode.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrentParent) Color(0xFF1E3A8A) else Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onReparentNode(itemNode.id)
                                        showParentDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        when (itemNode.type) {
                                            SceneNodeType.CHARACTER_ROOT -> Icons.Default.Person
                                            SceneNodeType.BLOCK -> Icons.Default.ViewInAr
                                            else -> Icons.Default.AccountTree
                                        },
                                        contentDescription = null,
                                        tint = if (isCurrentParent) Color(0xFF60A5FA) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            itemNode.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFFF1F5F9)
                                        )
                                        Text(
                                            itemNode.type.name,
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
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

@Composable
fun TransformChannelGroup(
    title: String,
    values: List<Float>,
    labels: List<String>,
    colors: List<Color>,
    step: Float,
    onValueChange: (Int, Float) -> Unit,
    onKeyframe: (Int) -> Unit
) {
    Column {
        Text(title, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEachIndexed { idx, v ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        labels[idx],
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors[idx]
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        String.format("%.1f", v),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f)
                    )

                    // Keyframe icon button
                    IconButton(
                        onClick = { onKeyframe(idx) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Keyframe",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
