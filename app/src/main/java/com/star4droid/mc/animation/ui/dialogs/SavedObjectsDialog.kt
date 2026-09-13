package com.star4droid.mc.animation.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import com.star4droid.mc.animation.objects.SavedObjectItem
import com.star4droid.mc.animation.objects.SavedObjectsRepository
import com.star4droid.mc.animation.ui.editor.EditorViewModel

@Composable
fun SavedObjectsDialog(
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var savedObjects by remember { mutableStateOf(SavedObjectsRepository.getSavedObjects(context)) }
    var itemToRename by remember { mutableStateOf<SavedObjectItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<SavedObjectItem?>(null) }
    var showSaveSelectionConfirm by remember { mutableStateOf(false) }

    fun refresh() {
        savedObjects = SavedObjectsRepository.getSavedObjects(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Bar with Title and Add Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Saved Objects",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Top Add Icon: Add selected node to saved library
                        IconButton(
                            onClick = {
                                val selectedNodeId = viewModel.uiState.value.selectedNodeId
                                val node = selectedNodeId?.let { viewModel.sceneGraph.getNode(it) }
                                if (node != null) {
                                    showSaveSelectionConfirm = true
                                } else {
                                    Toast.makeText(context, "Select an object in scene to save it, or use AI Studio!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Selected to Saved Objects",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Text(
                    "Add saved 3D objects to your scene, manage, rename or delete.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (savedObjects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No saved objects yet.\nUse AI Studio or select an object in scene to save!",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    ) {
                        items(savedObjects, key = { it.id }) { item ->
                            val itemColor = try {
                                if (!item.colorHex.isNullOrBlank()) Color(android.graphics.Color.parseColor(item.colorHex)) else Color(0xFF38BDF8)
                            } catch (e: Exception) {
                                Color(0xFF38BDF8)
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(itemColor.copy(alpha = 0.2f))
                                                .border(1.dp, itemColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Category,
                                                contentDescription = null,
                                                tint = itemColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                "3D Model (.obj)",
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    // Action Buttons AS ICONS: Add, Rename, Delete
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // 1. Add Icon Button
                                        IconButton(
                                            onClick = {
                                                viewModel.addSavedObjectToScene(item)
                                                Toast.makeText(context, "Added '${item.name}' to scene!", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AddCircle,
                                                contentDescription = "Add to scene",
                                                tint = Color(0xFF22C55E),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // 2. Rename Icon Button
                                        IconButton(
                                            onClick = {
                                                itemToRename = item
                                                renameText = item.name
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 3. Delete Icon Button
                                        IconButton(
                                            onClick = {
                                                itemToDelete = item
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
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

    // Rename Dialog
    itemToRename?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename Object", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF64748B)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        SavedObjectsRepository.renameObject(context, item.id, renameText.trim())
                        refresh()
                        itemToRename = null
                    }
                }) {
                    Text("Save", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Delete Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Object", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Delete '${item.name}' from saved objects library?", color = Color(0xFFCBD5E1)) },
            confirmButton = {
                TextButton(onClick = {
                    SavedObjectsRepository.deleteObject(context, item.id)
                    refresh()
                    itemToDelete = null
                }) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Save Selection Confirm Dialog
    if (showSaveSelectionConfirm) {
        val selectedNodeId = viewModel.uiState.value.selectedNodeId
        val node = selectedNodeId?.let { viewModel.sceneGraph.getNode(it) }
        var saveName by remember { mutableStateOf(node?.name ?: "Saved Object") }

        AlertDialog(
            onDismissRequest = { showSaveSelectionConfirm = false },
            title = { Text("Save Current Object", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a name to save '${node?.name}' to your library:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF22C55E),
                            unfocusedBorderColor = Color(0xFF64748B)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (node != null && saveName.isNotBlank()) {
                        SavedObjectsRepository.saveNodeAsObject(context, node, saveName.trim())
                        refresh()
                        showSaveSelectionConfirm = false
                        Toast.makeText(context, "Saved '$saveName' to library!", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save to Library", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveSelectionConfirm = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}
