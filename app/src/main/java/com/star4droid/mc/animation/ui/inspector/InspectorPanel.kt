package com.star4droid.mc.animation.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.animation.presets.PresetType
import com.star4droid.mc.animation.assets.BuiltInAssets
import com.star4droid.mc.animation.engine.math.Vec3
import com.star4droid.mc.animation.engine.scene.LightData
import com.star4droid.mc.animation.engine.scene.LightType
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
        com.star4droid.mc.animation.engine.scene.CharacterPartType.RIGHT_FOREARM,
        com.star4droid.mc.animation.engine.scene.CharacterPartType.LEFT_FOREARM -> when (axis) {
            0 -> value.coerceIn(0f, 145f)
            1 -> value.coerceIn(-25f, 25f)
            else -> value.coerceIn(-25f, 25f)
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
        com.star4droid.mc.animation.engine.scene.CharacterPartType.RIGHT_LOWER_LEG,
        com.star4droid.mc.animation.engine.scene.CharacterPartType.LEFT_LOWER_LEG -> when (axis) {
            0 -> value.coerceIn(-135f, 0f)
            1 -> value.coerceIn(-20f, 20f)
            else -> value.coerceIn(-20f, 20f)
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
    onUpdateLightData: ((LightData) -> Unit)? = null,
    onOpenTextureBrowser: (() -> Unit)? = null,
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
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // Name Property & Type
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

            // Parent Property
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
                        Text("Anchored to Joint Hinge", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Limb joint is anchored. Move character root to reposition.", fontSize = 10.sp, color = Color(0xFF64748B))
                    if (node.parentId != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onSelectNode(node.parentId!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Select Parent Hinge", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            } else {
                TransformChannelGroup(
                    title = "Position",
                    values = listOf(t.position.x, t.position.y, t.position.z),
                    labels = listOf("X", "Y", "Z"),
                    colors = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6)),
                    sensitivity = 0.05f,
                    onValueChange = { idx, newVal ->
                        val newPos = when (idx) {
                            0 -> t.position.copy(x = newVal)
                            1 -> t.position.copy(y = newVal)
                            else -> t.position.copy(z = newVal)
                        }
                        onUpdateTransform(t.copy(position = newPos))
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Rotation
            TransformChannelGroup(
                title = if (node.type == SceneNodeType.CHARACTER_PART) "Rotation (Skeletal Joint Limits)" else "Rotation",
                values = listOf(t.rotation.x, t.rotation.y, t.rotation.z),
                labels = listOf("X", "Y", "Z"),
                colors = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6)),
                sensitivity = 1.0f,
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
                }
            } else {
                TransformChannelGroup(
                    title = if (node.type == SceneNodeType.CHARACTER_ROOT) "Scale (Entire Character)" else "Scale",
                    values = listOf(t.scale.x, t.scale.y, t.scale.z),
                    labels = listOf("X", "Y", "Z"),
                    colors = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6)),
                    sensitivity = 0.02f,
                    onValueChange = { idx, newVal ->
                        val coerced = newVal.coerceAtLeast(0.00001f)
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
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Light Properties Section
            if (node.type == SceneNodeType.LIGHT) {
                Text("Light Properties", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(6.dp))

                val light = node.lightData ?: LightData()

                // Light Type Selection Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LightType.values().forEach { lt ->
                        val isSel = light.lightType == lt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) Color(0xFF2563EB) else Color(0xFF334155))
                                .clickable {
                                    onUpdateLightData?.invoke(light.copy(lightType = lt))
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                lt.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Light Color Presets
                Text("Light Color", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(4.dp))
                val lightColors = listOf(
                    0xFFFFF2D4.toInt(), // Sun Gold
                    0xFFFFFFFF.toInt(), // Pure White
                    0xFF38BDF8.toInt(), // Cool Cyan
                    0xFFF59E0B.toInt(), // Warm Amber
                    0xFFEF4444.toInt(), // Crimson Red
                    0xFF22C55E.toInt()  // Emerald Green
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lightColors.forEach { c ->
                        val isSelected = light.color == c
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(c))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    onUpdateLightData?.invoke(light.copy(color = c))
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Intensity
                Text("Intensity: ${String.format("%.1f", light.intensity)}x", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Slider(
                    value = light.intensity,
                    onValueChange = { onUpdateLightData?.invoke(light.copy(intensity = it)) },
                    valueRange = 0.0f..5.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B), activeTrackColor = Color(0xFFF59E0B))
                )

                if (light.lightType != LightType.SUN) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Range Distance: ${String.format("%.1f", light.range)}m", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Slider(
                        value = light.range,
                        onValueChange = { onUpdateLightData?.invoke(light.copy(range = it)) },
                        valueRange = 1.0f..50.0f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF38BDF8), activeTrackColor = Color(0xFF38BDF8))
                    )
                }

                if (light.lightType == LightType.SPOT) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Spot Cone Angle: ${light.coneAngle.toInt()}°", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Slider(
                        value = light.coneAngle,
                        onValueChange = { onUpdateLightData?.invoke(light.copy(coneAngle = it)) },
                        valueRange = 10.0f..120.0f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFA855F7), activeTrackColor = Color(0xFFA855F7))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show Light Direction & Range Wireframe Lines Toggle
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Direction & Range Lines", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White)
                            Text("Draw wireframe direction rays & distance bounds", fontSize = 9.sp, color = Color(0xFF94A3B8))
                        }
                        androidx.compose.material3.Switch(
                            checked = light.showHelperLines,
                            onCheckedChange = { onUpdateLightData?.invoke(light.copy(showHelperLines = it)) },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Color(0xFF38BDF8))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Material / Block / Plane Texture
            if (node.type == SceneNodeType.BLOCK || node.type == SceneNodeType.HALF_BLOCK || node.type == SceneNodeType.STEP_BLOCK || node.type == SceneNodeType.GROUND || node.type == SceneNodeType.PLANE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Texture Asset", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF94A3B8))
                    if (onOpenTextureBrowser != null) {
                        TextButton(
                            onClick = onOpenTextureBrowser,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Texture", fontSize = 11.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Built-in textures
                    BuiltInAssets.BLOCK_TEXTURES.forEach { def ->
                        val isSelected = node.material.textureAssetId == def.id
                        val bmp = remember(def.id) {
                            BuiltInAssets.createProceduralBlockBitmap(def).asImageBitmap()
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(def.sideColor))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF22C55E) else Color(0xFF475569),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onUpdateMaterial(def.id, node.material.opacity) },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = def.displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                filterQuality = FilterQuality.None
                            )
                        }
                    }

                    // Custom Imported textures
                    BuiltInAssets.customBitmaps.forEach { (customId, bitmap) ->
                        val isSelected = node.material.textureAssetId == customId
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0F172A))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF22C55E) else Color(0xFF475569),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onUpdateMaterial(customId, node.material.opacity) },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = customId,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Opacity: Allow min value 0.0f
                Text("Opacity: ${(node.material.opacity * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Slider(
                    value = node.material.opacity,
                    onValueChange = { onUpdateMaterial(node.material.textureAssetId, it.coerceIn(0.0f, 1.0f)) },
                    valueRange = 0.0f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF3B82F6), activeTrackColor = Color(0xFF3B82F6))
                )
            }
        }
    }

    // Rename Node Dialog
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

    // Parent Selection Dialog
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
    sensitivity: Float,
    onValueChange: (Int, Float) -> Unit
) {
    var activeFieldIndex by remember { mutableStateOf<Int?>(null) }
    val currentValues by rememberUpdatedState(values)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Column {
        Text(title, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            values.forEachIndexed { idx, v ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                        .pointerInput(idx) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var isDragging = false
                                    var totalDragAmount = 0f

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break

                                        if (change.changedToUp()) {
                                            if (!isDragging) {
                                                activeFieldIndex = idx
                                            }
                                            break
                                        } else if (change.pressed) {
                                            val dragDelta = change.positionChange().x
                                            totalDragAmount += dragDelta

                                            if (kotlin.math.abs(totalDragAmount) > 6f || isDragging) {
                                                isDragging = true
                                                change.consume()
                                                val delta = dragDelta * sensitivity
                                                currentOnValueChange(idx, currentValues[idx] + delta)
                                            }
                                        } else {
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            labels[idx],
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors[idx]
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            formatFloatValue(v),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }

    // Compact Numeric Keypad Dialog on click
    if (activeFieldIndex != null) {
        val targetIdx = activeFieldIndex!!
        val initialVal = values[targetIdx]
        NumericKeypadDialog(
            title = "${title} ${labels[targetIdx]}",
            initialValue = initialVal,
            onDismiss = { activeFieldIndex = null },
            onConfirm = { newVal ->
                onValueChange(targetIdx, newVal)
                activeFieldIndex = null
            }
        )
    }
}

fun formatFloatValue(value: Float): String {
    if (value == 0f) return "0"
    val formatted = String.format(java.util.Locale.US, "%.4f", value)
    return formatted.dropLastWhile { it == '0' }.dropLastWhile { it == '.' }
}

@Composable
fun NumericKeypadDialog(
    title: String,
    initialValue: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var textState by remember { mutableStateOf(formatFloatValue(initialValue)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E293B),
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(260.dp)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.height(6.dp))

                // Display Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        textState.ifEmpty { "0" },
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Keypad grid with Del, Erase, Set buttons
                val keys = listOf(
                    listOf("1", "2", "3", "Del"),
                    listOf("4", "5", "6", "Erase"),
                    listOf("7", "8", "9", "-"),
                    listOf(".", "0", "Set")
                )

                keys.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { key ->
                            Surface(
                                onClick = {
                                    when (key) {
                                        "Del" -> if (textState.isNotEmpty()) textState = textState.dropLast(1)
                                        "Erase" -> textState = ""
                                        "Set" -> {
                                            val parsed = textState.toFloatOrNull() ?: initialValue
                                            onConfirm(parsed)
                                        }
                                        "-" -> {
                                            textState = if (textState.startsWith("-")) textState.drop(1) else "-$textState"
                                        }
                                        else -> textState += key
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = when (key) {
                                    "Set" -> Color(0xFF22C55E)
                                    "Erase" -> Color(0xFFEF4444)
                                    "Del" -> Color(0xFFDC2626)
                                    else -> Color(0xFF334155)
                                },
                                modifier = Modifier
                                    .weight(if (key == "Set") 2f else 1f)
                                    .height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when (key) {
                                        "Del" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Del", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        "Erase" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Clear, contentDescription = "Erase", tint = Color.White, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Erase", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        "Set" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Check, contentDescription = "Set", tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Set", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        else -> Text(key, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
