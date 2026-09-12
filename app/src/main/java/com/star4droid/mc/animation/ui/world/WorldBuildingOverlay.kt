package com.star4droid.mc.animation.ui.world

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.mc.animation.assets.BuiltInAssets

import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.star4droid.mc.animation.engine.scene.SceneNode

enum class WorldBuildingTool {
    ADD,
    REMOVE,
    SELECT
}

@Composable
fun WorldBuildingOverlay(
    activeTool: WorldBuildingTool,
    selectedTextureId: String,
    selectedParentId: String?,
    parentName: String,
    candidateParents: List<SceneNode>,
    onSelectTool: (WorldBuildingTool) -> Unit,
    onSelectTexture: (String) -> Unit,
    onSelectParent: (String?) -> Unit,
    onAddBlockAtCursor: () -> Unit,
    onOpenImportBrowser: () -> Unit,
    onExitMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isParentMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Top-Left Floating Minimalist Icons: Back, Add, Remove, Select, and Parent Selector
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xDD0F172A),
            tonalElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Back / Exit icon
                IconButton(
                    onClick = onExitMode,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Exit World Building",
                        tint = Color(0xFFF1F5F9),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(Color(0xFF334155))
                )

                // Add Mode Tool
                IconButton(
                    onClick = { onSelectTool(WorldBuildingTool.ADD) },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTool == WorldBuildingTool.ADD) Color(0xFF2563EB) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.AddBox,
                        contentDescription = "Add Mode (Tap in 3D scene to place)",
                        tint = if (activeTool == WorldBuildingTool.ADD) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Remove Mode Tool
                IconButton(
                    onClick = { onSelectTool(WorldBuildingTool.REMOVE) },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTool == WorldBuildingTool.REMOVE) Color(0xFFDC2626) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove Mode (Tap block in 3D scene to delete)",
                        tint = if (activeTool == WorldBuildingTool.REMOVE) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Select Tool
                IconButton(
                    onClick = { onSelectTool(WorldBuildingTool.SELECT) },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTool == WorldBuildingTool.SELECT) Color(0xFF10B981) else Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = "Select Mode (Tap object to set as Parent)",
                        tint = if (activeTool == WorldBuildingTool.SELECT) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(Color(0xFF334155))
                )

                // Parent Object Selector Chip
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedParentId != null) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                            .clickable { isParentMenuOpen = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountTree,
                            contentDescription = "Parent Hierarchy",
                            tint = if (selectedParentId != null) Color(0xFF60A5FA) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedParentId != null) "Parent: $parentName" else "Parent: Root",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isParentMenuOpen,
                        onDismissRequest = { isParentMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Scene Root)", fontWeight = if (selectedParentId == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onSelectParent(null)
                                isParentMenuOpen = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                            }
                        )
                        candidateParents.forEach { node ->
                            DropdownMenuItem(
                                text = { Text(node.name, fontWeight = if (selectedParentId == node.id) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onSelectParent(node.id)
                                    isParentMenuOpen = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF38BDF8))
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Center Bottom Guidance & Action HUD
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xDD0F172A),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hintText = when (activeTool) {
                    WorldBuildingTool.ADD -> "Tap on ground or block to place"
                    WorldBuildingTool.REMOVE -> "Tap any block to delete it"
                    WorldBuildingTool.SELECT -> "Tap an object to set as parent"
                }
                val hintTint = when (activeTool) {
                    WorldBuildingTool.ADD -> Color(0xFF60A5FA)
                    WorldBuildingTool.REMOVE -> Color(0xFFF87171)
                    WorldBuildingTool.SELECT -> Color(0xFF34D399)
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(hintTint)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(hintText, fontSize = 12.sp, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)

                if (activeTool == WorldBuildingTool.ADD) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2563EB),
                        modifier = Modifier.clickable { onAddBlockAtCursor() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Center", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Right-Side Texture Selection Bar (Custom & Built-in textures)
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            color = Color(0xDD0F172A),
            tonalElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.85f)
                .width(68.dp)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Import button at top of texture rail
                IconButton(
                    onClick = onOpenImportBrowser,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155))
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Import Texture",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(1.dp)
                        .background(Color(0xFF475569))
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable List of Block Textures
                val allTextures = remember(BuiltInAssets.customBitmaps.size) {
                    val list = BuiltInAssets.BLOCK_TEXTURES.map { it.id }.toMutableList()
                    BuiltInAssets.customBitmaps.keys.forEach { customId ->
                        if (!list.contains(customId)) list.add(0, customId)
                    }
                    list
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(allTextures) { texId ->
                        val isSelected = texId == selectedTextureId
                        val previewBitmap = remember(texId) {
                            BuiltInAssets.getCustomBitmap(texId) ?: run {
                                val def = BuiltInAssets.getTextureDef(texId)
                                BuiltInAssets.createProceduralBlockBitmap(def)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectTexture(texId) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = texId,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
