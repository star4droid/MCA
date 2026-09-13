package com.star4droid.mc.animation.ui.hierarchy

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star4droid.mc.animation.engine.scene.SceneGraph
import com.star4droid.mc.animation.engine.scene.SceneNode
import com.star4droid.mc.animation.engine.scene.SceneNodeType

@Composable
fun HierarchyPanel(
    sceneGraph: SceneGraph,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
    onDeleteNode: () -> Unit,
    onDuplicateNode: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(Color(0xEE1E293B))
            .padding(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Hierarchy",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFFE2E8F0)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedNodeId != null) {
                    IconButton(onClick = onDuplicateNode, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Duplicate", tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDeleteNode, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tree List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            for (rootId in sceneGraph.rootNodeIds) {
                val rootNode = sceneGraph.getNode(rootId) ?: continue
                HierarchyNodeItem(
                    sceneGraph = sceneGraph,
                    node = rootNode,
                    depth = 0,
                    selectedNodeId = selectedNodeId,
                    expandedState = expandedState,
                    onSelectNode = onSelectNode
                )
            }
        }
    }
}

@Composable
fun HierarchyNodeItem(
    sceneGraph: SceneGraph,
    node: SceneNode,
    depth: Int,
    selectedNodeId: String?,
    expandedState: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    onSelectNode: (String) -> Unit
) {
    val isExpanded = expandedState.getOrPut(node.id) { true }
    val isSelected = (node.id == selectedNodeId)
    val hasChildren = node.children.isNotEmpty()

    val typeIcon = when (node.type) {
        SceneNodeType.CHARACTER_ROOT -> "🧑"
        SceneNodeType.CHARACTER_PART -> "🦴"
        SceneNodeType.BLOCK -> "🧱"
        SceneNodeType.CAMERA -> "🎥"
        SceneNodeType.LIGHT -> "☀️"
        SceneNodeType.GROUND -> "🟩"
        SceneNodeType.PLANE -> "🗺️"
        SceneNodeType.GROUP -> "📁"
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 14).dp, top = 2.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) Color(0xFF2563EB).copy(alpha = 0.45f) else Color.Transparent)
                .clickable { onSelectNode(node.id) }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { expandedState[node.id] = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(20.dp))
            }

            Text(typeIcon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = node.name,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Eye visibility icon
            Text(
                text = if (node.visible) "👁️" else "🕶️",
                fontSize = 12.sp,
                modifier = Modifier.clickable {
                    node.visible = !node.visible
                }
            )
        }

        if (hasChildren && isExpanded) {
            for (childId in node.children) {
                val child = sceneGraph.getNode(childId) ?: continue
                HierarchyNodeItem(
                    sceneGraph = sceneGraph,
                    node = child,
                    depth = depth + 1,
                    selectedNodeId = selectedNodeId,
                    expandedState = expandedState,
                    onSelectNode = onSelectNode
                )
            }
        }
    }
}
