# Minecraft Animation Studio (MCA) - Developer & AI Context Guide

This document provides a comprehensive technical overview of the **Minecraft Animation Studio (MCA)** codebase. It is designed to allow any developer or AI coding assistant to immediately understand the project architecture, rendering pipeline, character rigging, animation system, UI component structure, and state management.

---

## 1. Executive Summary & Tech Stack

**MCA** is a native 3D Minecraft animation studio for Android. It allows users to build 3D scenes, rig Minecraft characters (Steve, Alex, Zombie, Knight, Miner), create block-based and keyframe-based animations, place and configure custom lights and cameras, and export/import animation clips.

### Technology Stack:
- **Language**: Kotlin (1.9+)
- **UI Framework**: Jetpack Compose with Material 3 design system
- **3D Graphics Engine**: OpenGL ES 2.0 (`GLSurfaceView`, custom GLSL Shaders, Matrix math)
- **Architecture**: MVVM with Kotlin Flows (`EditorViewModel`, `EditorUiState`)
- **Persistence**: JSON-based project serialization via GSON/Kotlin serialization to local app storage (`ProjectRepository`)
- **Audio Engine**: Android `SoundPool` for UI micro-feedback sound effects (`SoundPlayer`)

---

## 2. Directory & Package Structure

```
app/src/main/java/com/star4droid/mc/animation/
├── ai/                     # Side AI Assistant logic & prompt parser
│   └── SideAiAssistant.kt
├── animation/              # Animation model, track keyframing & evaluation engine
│   ├── AnimationEvaluator.kt   # Core frame-by-frame timeline evaluator
│   ├── AnimationModel.kt       # Data classes (ActionBlock, AnimationTrack, Keyframe, TimelineAsset)
│   └── presets/                # Procedural preset generators (Walk, Run, Wave, Jump, Flip)
│       └── PresetGenerator.kt
├── assets/                 # Built-in procedural block textures, skin loader & sound player
│   ├── AssetModel.kt
│   ├── BuiltInAssets.kt
│   └── SoundPlayer.kt
├── character/              # Skeletal character factory & joint rigging
│   └── CharacterFactory.kt
├── engine/                 # Low-level 3D math, scene graph, camera, gizmo, rendering
│   ├── camera/
│   │   └── EditorCamera.kt     # Orbit, fly joystick, and scene camera controls
│   ├── gizmo/
│   │   └── GizmoController.kt  # 3D Move/Rotate/Scale gizmo hit-testing & dragging
│   ├── history/
│   │   └── HistoryManager.kt   # Undo/Redo command stack
│   ├── math/
│   │   ├── Mat4.kt             # 4x4 Transformation matrix math
│   │   ├── Ray.kt              # Screen-to-world raycasting for selection & grid placement
│   │   └── Vec3.kt             # 3D Vector operations (dot, cross, lerp, distance)
│   ├── rendering/
│   │   ├── Geometry.kt         # Mesh generators (Cube, Head, Grid, Wireframe, Camera Frustum)
│   │   ├── SceneRenderer.kt    # OpenGL ES 2.0 rendering loop & light icon renderer
│   │   ├── Shader.kt           # GLSL Shader compilation & uniform binder
│   │   └── TextureManager.kt   # OpenGL texture cache & bitmap binder
│   └── scene/
│       ├── SceneData.kt        # Enums & structs (SceneNodeType, CharacterPartType, LightType, LightData)
│       ├── SceneGraph.kt       # Hierarchical scene tree & world matrix calculator
│       ├── SceneNode.kt        # Unified 3D object node (block, character part, camera, light)
│       └── Transform.kt        # Position, rotation, scale, and pivot offset data
├── project/                # Project save/load repository & animation file exporter
│   ├── ProjectData.kt
│   └── ProjectRepository.kt
└── ui/                     # Jetpack Compose UI screens & overlay panels
    ├── MinecraftAnimationApp.kt # Navigation graph
    ├── ai/
    │   └── SideAiDialog.kt
    ├── assets/
    │   └── AssetBrowserSheet.kt
    ├── editor/
    │   ├── EditorScreen.kt     # Main studio UI container with top bar & resizable panels
    │   ├── EditorViewModel.kt  # Studio state holder & business logic controller
    │   └── Viewport3D.kt       # GLSurfaceView touch input handler & orbit/gizmo controller
    ├── files/
    │   └── FileBrowserDialog.kt
    ├── hierarchy/
    │   └── HierarchyPanel.kt   # Left scene tree panel with resizable width handle
    ├── inspector/
    │   └── InspectorPanel.kt   # Right transform & properties panel with numeric keypad dialog
    ├── scene/
    │   └── SceneManagerDialog.kt
    ├── timeline/
    │   ├── ActionBlockSettingsDialog.kt # Block property editor
    │   └── TimelinePanel.kt    # Bottom/Side block-based timeline track editor
    └── world/
        └── WorldBuildingOverlay.kt # World building mode overlay UI
```

---

## 3. Core Engine Subsystems

### A. Scene Graph & Rigging (`SceneGraph.kt`, `SceneNode.kt`, `CharacterFactory.kt`)
- All 3D items exist as `SceneNode` instances in a hierarchical tree.
- **Character Rigging**:
  - `CHARACTER_ROOT`: Root node positioned at base coordinates.
  - `BODY`: Torso node attached to Root.
  - `HEAD`: Head node attached to Body with pivot at neck base.
  - `RIGHT_ARM` & `RIGHT_FOREARM`: Upper arm attached to Body; lower arm (forearm) attached to upper arm at elbow joint for connected 2-segment arm bending like skeletal bones.
  - `LEFT_ARM` & `LEFT_FOREARM`: Upper arm attached to Body; lower arm (forearm) attached to upper arm at elbow joint.
  - `RIGHT_LEG` & `RIGHT_LOWER_LEG`: Thigh attached to Root; calf/foot attached to thigh at knee joint for connected 2-segment leg bending like skeletal bones.
  - `LEFT_LEG` & `LEFT_LOWER_LEG`: Thigh attached to Root; calf/foot attached to thigh at knee joint.
- **Supported Primitive & Item Types (`SceneNodeType`)**:
  - `BLOCK`: Standard 3D Minecraft voxel block.
  - `PLANE`: 2D textured Quad/Plane mesh with selectable built-in or custom app textures.
  - `CHARACTER_ROOT` / `CHARACTER_PART`: Character root and skeletal bone parts.
  - `CAMERA`: Scene Camera marker with FOV cone.
  - `LIGHT`: Sun, Point, or Spot light source with 3D light icon.
  - `GROUND`: Ground terrain plane.

### B. OpenGL ES 2.0 Renderer (`SceneRenderer.kt`)
- Renders:
  1. **Sky / Environment**: Dynamic background color according to `TimeOfDay` (Morning, Noon, Evening, Night).
  2. **Ground Grid**: Anti-aliased line grid plane.
  3. **Scene Nodes**: Blocks, character limb meshes with procedural textures or custom skin textures.
  4. **Light Markers**: Sun, Point, and Spot lights rendered as distinct 3D glowing light icons (bulbs, stars, cones).
  5. **Camera Markers**: Vintage camera model with wireframe perspective frustum cone radiating into 3D space.
  6. **3D Gizmo**: Move (arrows), Rotate (rings), and Scale (cubes) handles. Gizmo is hidden during Build Mode.

### C. Animation & Timeline Engine (`AnimationModel.kt`, `AnimationEvaluator.kt`)
- **Dual Animation System**:
  1. **Action Blocks**: Modular action blocks (`WALK`, `RUN`, `JUMP`, `WAVE`, `SLIDE_TO_POS`, `MOVE_TO_POS`, `SCALE`, `ANIMATION_CLIP`). Each item/node has its own separated list of action blocks in the timeline.
  2. **Keyframe Tracks**: Fine-grained animation tracks (`transform.position.x`, `transform.rotation.y`, `material.opacity`, etc.) with linear, step, and smooth cubic Hermite interpolation.
- **Animation Evaluator**: Evaluates action blocks and tracks frame-by-frame and updates `node.animatedTransform` without mutating `node.baseTransform`.

---

## 4. UI Layout & Resizable Panels Architecture

- **Orientation Awareness**:
  - **Portrait Mode**: System status bar visible; Timeline panel rendered at the bottom.
  - **Landscape Mode**: System status bar hidden; Timeline panel rendered on the left side vertically running top-to-bottom.
- **Resizable Panels (`PanelResizeHandle`)**:
  - Hierarchy Panel, Inspector Panel, and Timeline Panel include a centered drag handle icon.
  - Users can drag the handle to resize panel width or height.
  - Double-clicking the resize handle resets panel sizes to defaults (`hierarchyWidth = 260.dp`, `inspectorWidth = 280.dp`, `timelineHeight = 220.dp`).

---

## 5. Build Mode & Interactive Controls

- **World Building Mode**:
  - Grid-aligned block placement when tapping on ground (`y = 0`) or block faces.
  - 3D gizmo is hidden during Build Mode and automatically restored on exit.
- **Property Inspector Interactions**:
  - Horizontal drag/swipe on X, Y, Z transform fields increments/decrements values.
  - Clicking any field opens a compact, bounded numeric keypad dialog with `1-9`, `0`, `.`, `-`, `Backspace`, `Clear`, and `Confirm ✅`.
  - Opacity property min value is `0.0f` (range `0.0f..1.0f`).
  - Scale property has no upper limit cap of 20 (`.coerceAtLeast(0.05f)`).

---

## 6. Guidelines for Future AI Coding Assistants

1. **Preserve Scene Graph Matrix Hierarchy**: Always call `sceneGraph.updateWorldMatrices()` after modifying node transforms or parent relationships.
2. **Limb Joint Bending Constraints**: Use `clampRotationForPart()` when modifying character limb rotations to enforce realistic skeletal rotation boundaries.
3. **Timeline Item Filtering**: When rendering action blocks in `TimelinePanel`, always filter by `selectedNodeId` so each object manages its own separate animation blocks.
4. **Clean Code Structure**: Maintain clear separation between Compose UI layers, engine 3D math, and OpenGL rendering code.
