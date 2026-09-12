# Minecraft 3D Animator & Rigging Studio (MCA)

## Overview
MCA is a native Android application built with **Jetpack Compose** and **OpenGL ES 2.0**. It provides a 3D animation, rigging, and world-building editor specifically designed for Minecraft characters (Steve, Alex, Zombie, Knight, Miner) and block scenes.

---

## Technical Stack & Architecture

- **Language & Runtime**: Kotlin, Android SDK (Min API 24 / Target API 35)
- **UI Framework**: Jetpack Compose (Material3)
- **3D Graphics Engine**: Custom OpenGL ES 2.0 Renderer (`SceneRenderer`, `Shader`, `Geometry`, `Mesh`)
- **State Management**: Kotlin `StateFlow`, `EditorViewModel`, Compose State
- **Data Persistence**: JSON-based project repository (`ProjectRepository`, `SceneData`), SAF export/import
- **3D Math Engine**: Custom vectors (`Vec3`), 4x4 Matrices (`Mat4`), Rays & AABB raycasting (`Ray`)

---

## Key Modules & Core Structure

### 1. 3D Rendering Engine (`com.star4droid.mc.animation.engine.rendering`)
- **`SceneRenderer.kt`**: Main OpenGL renderer handling depth testing, clear colors (TimeOfDay lighting), camera view-projection matrices, scene nodes, selection highlights, ground grid, point/spot light sources, and gizmo controls.
- **`Shader.kt`**: GLSL Vertex and Fragment shaders supporting texture mapping, ambient/sun directional light, 4 dynamic point/spot lights, and selection tinting.
- **`Geometry.kt`**: Procedural mesh generators for cubes, heads, planes, grids, bounding wireframes, and camera frustum visualizations.

### 2. Scene Graph & Rigging (`com.star4droid.mc.animation.engine.scene`)
- **`SceneNode.kt`**: Node object containing base/animated transforms, parent/child relationships, node type (`CHARACTER_ROOT`, `CHARACTER_PART`, `BLOCK`, `PLANE`, `CAMERA`, `LIGHT`, `GROUND`), material, and bounding box dimensions.
- **`CharacterFactory.kt`**: Constructs hierarchically rigged Minecraft characters (Steve, Alex, Zombie, Knight, Miner) with parent-child joints (Torso -> Head, Upper Arm -> Forearm, Thigh -> Lower Leg).

### 3. Timeline & Block-Based Animation System (`com.star4droid.mc.animation.animation`)
- **`ActionBlock.kt` / `ActionBlockType.kt`**: 22+ block-based animation actions (`WALK`, `RUN`, `JUMP`, `WAVE`, `ROTATE`, `TILT_HEAD`, `PUNCH`, `LOOK_LEFT`, `LOOK_RIGHT`, `BACKFLIP`, `JUMP_FRONT`, `SIT_DOWN`, `STAND_UP`, `KICK`, `NOD_HEAD`, `SHAKE_HEAD`, `CLAP`, `CHEER`, `SHRUG`, `CROSS_ARMS`, `BOW`, `DEATH_FALL`, `SNEAK_WALK`, `SPIN_ATTACK`, `BLOCK_SHIELD`, `TAUNT`).
- **`AnimationEvaluator.kt`**: Evaluates timeline tracks per node independently at time `t`, accumulating skeletal joint rotations and root spatial displacement.

### 4. Interactive Editor & Controls (`com.star4droid.mc.animation.ui`)
- **`Viewport3D.kt` / `EditorGLSurfaceView.kt`**: Handles 3D touch input (Camera Orbit/Pan/Pinch Zoom, Gizmo drag, Build Mode face-snapped block placement/deletion).
- **`InspectorPanel.kt`**: Object property editor with horizontal touch-swipe transform fields and numeric keypad popup.
- **`HierarchyPanel.kt`**: Scene graph tree view with dual horizontal and vertical scrolling.
- **`TimelinePanel.kt`**: Multi-track timeline canvas supporting block dragging, track row organization, block settings dialogs, and instant block filtering per selected object.
- **`WorldBuildingOverlay.kt`**: World-building palette interface for placing textures, planes, and blocks.

### 5. Utilities & File Format Support (`com.star4droid.mc.animation.utils`)
- **`ObjExporter.kt`**: Exports character models and scene geometry to `.obj` and `.mtl` formats via Storage Access Framework (SAF).
- **`ObjImporter.kt`**: Parses 3D `.obj` and `.mtl` files into `SceneNode` geometries.

---

## Guidance for AI Assistants & Developers

1. **Transform Matrices & Hierarchy**: Node transforms are evaluated recursively in `SceneGraph.updateWorldMatrices()`: `WorldMatrix = ParentWorldMatrix * LocalMatrix`.
2. **Character Joints**: Limb parts (`RIGHT_FOREARM`, `LEFT_LOWER_LEG`, etc.) are child nodes anchored at their parent joint hinges. Do NOT separate forearm/leg nodes from upper limbs.
3. **Block Placement**: In Build Mode, new 1x1x1 blocks snap to the clicked face of targeted blocks based on face normal & bounding dimensions (`extentX`, `extentY`, `extentZ`).
4. **Timeline Filtering**: `TimelinePanel` filters action blocks by `targetNodeId == selectedNodeId` so that each scene node maintains its own independent action blocks.
