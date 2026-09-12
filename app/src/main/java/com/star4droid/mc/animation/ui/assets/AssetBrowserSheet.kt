package com.star4droid.mc.animation.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.star4droid.mc.animation.assets.BuiltInAssets
import com.star4droid.mc.animation.assets.SoundPlayer

data class TextureItemData(
    val id: String,
    val displayName: String,
    val color: Int,
    val customBitmap: android.graphics.Bitmap? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetBrowserSheet(
    onDismiss: () -> Unit,
    onApplyTexture: (String) -> Unit,
    onAddBlockWithTexture: (String) -> Unit,
    onAddPlaneWithTexture: ((String) -> Unit)? = null,
    onAddCharacterWithSkin: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Blocks & Textures", "Characters & Skins", "Audio SFX")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Asset Browser & Spawn Menu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFF1F5F9)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF22C55E),
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF22C55E)
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color(0xFF22C55E) else Color(0xFF94A3B8)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    // Block Textures & Spawning (Built-in + Custom Imported)
                    val allTextureItems = remember(BuiltInAssets.customBitmaps.size) {
                        val items = mutableListOf<TextureItemData>()
                        BuiltInAssets.customBitmaps.forEach { (customId, bmp) ->
                            items.add(TextureItemData(customId, customId, 0xFF0F172A.toInt(), bmp))
                        }
                        BuiltInAssets.BLOCK_TEXTURES.forEach { def ->
                            items.add(TextureItemData(def.id, def.displayName, def.sideColor, null))
                        }
                        items
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(340.dp)
                    ) {
                        items(allTextureItems) { item ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(item.color))
                                        .border(1.dp, Color(0xFF475569), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.customBitmap != null) {
                                        Image(
                                            bitmap = item.customBitmap.asImageBitmap(),
                                            contentDescription = item.displayName,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    item.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = { onApplyTexture(item.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(26.dp)
                                    ) {
                                        Text("Apply", fontSize = 8.sp, color = Color(0xFFE2E8F0))
                                    }
                                    Button(
                                        onClick = {
                                            onAddBlockWithTexture(item.id)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(26.dp)
                                    ) {
                                        Text("+Block", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    if (onAddPlaneWithTexture != null) {
                                        Button(
                                            onClick = {
                                                onAddPlaneWithTexture(item.id)
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                            modifier = Modifier.weight(1f).height(26.dp)
                                        ) {
                                            Text("+Plane", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Characters & Skins
                    val skins = listOf(
                        Triple("Steve", "Classic Blue Shirt & Jeans", "steve"),
                        Triple("Alex", "Classic Green Tunic", "alex"),
                        Triple("Zombie", "Green Undead Villager", "zombie"),
                        Triple("Knight", "Iron Armor Warrior", "knight"),
                        Triple("Miner", "Deepslate Miner with Helmet", "miner")
                    )
                    Column(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for ((name, desc, skinId) in skins) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        when (skinId) {
                                            "alex" -> "👩"
                                            "zombie" -> "🧟"
                                            "knight" -> "🛡️"
                                            "miner" -> "⛏️"
                                            else -> "🧑"
                                        },
                                        fontSize = 24.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        Text(desc, fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            onApplyTexture(skinId)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Apply Skin", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                    Button(
                                        onClick = {
                                            onAddCharacterWithSkin(skinId)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("+ Add Character", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // Audio SFX
                    val sounds = listOf(
                        Pair("Footstep", SoundPlayer.SoundType.STEP),
                        Pair("Block Pop", SoundPlayer.SoundType.POP),
                        Pair("Level Ding", SoundPlayer.SoundType.DING),
                        Pair("Swing Whoosh", SoundPlayer.SoundType.WHOOSH)
                    )
                    Column(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for ((name, type) in sounds) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.White)
                                Button(
                                    onClick = { SoundPlayer.playSound(type) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Preview", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
