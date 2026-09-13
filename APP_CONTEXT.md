# Minecraft 3D Animator & Rigging Studio (MCA)

## Overview
MCA is a native Android application built with **Jetpack Compose** and **OpenGL ES 2.0**. It provides a 3D animation, rigging, and world-building editor specifically designed for Minecraft characters (Steve, Alex, Zombie, Knight, Miner) and block scenes, complete with an AI Animation Studio powered by Gemini 3.5 Flash and a Custom Action Blocks Manager.

---

## Technical Stack & Architecture

- **Language & Runtime**: Kotlin, Android SDK (Min API 24 / Target API 35)
- **UI Framework**: Jetpack Compose (Material3)
- **3D Graphics Engine**: Custom OpenGL ES 2.0 Renderer (`SceneRenderer`, `Shader`, `Geometry`, `Mesh`)
- **State Management**: Kotlin `StateFlow`, `EditorViewModel`, Compose State
- **Data Persistence**: Structured per-project folder repository in `Android/data/<package>/files/<projectName>/` with `models/`, `sounds/`, `textures/`, `scenes/`, `animations/` subfolders (`ProjectRepository`), SAF export/import, Custom Blocks Repository (`CustomBlocksRepository`)
- **AI Integration**: Gemini REST API (`gemini-3.5-flash` default) with keyframe track generation and iterative prompt refinement
- **3D Math Engine**: Custom vectors (`Vec3`), 4x4 Matrices (`Mat4`), Rays & AABB raycasting (`Ray`)

---

## Key Modules & Core Structure

### 1. 3D Rendering Engine (`com.star4droid.mc.animation.engine.rendering`)
- **`SceneRenderer.kt`**: Main OpenGL renderer handling depth testing, clear colors (TimeOfDay lighting), camera view-projection matrices, scene nodes, selection highlights, ground grid, point/spot light sources, gizmo controls, custom 3D OBJ mesh rendering, and half/step block meshes.
- **`Shader.kt`**: GLSL Vertex and Fragment shaders supporting texture mapping, ambient/sun directional light, 4 dynamic point/spot lights, and selection tinting.
- **`Geometry.kt`**: Procedural mesh generators for cubes (`createCubeMesh`), half blocks / slabs (`createHalfBlockMesh`), step blocks / stairs (`createStepBlockMesh`), heads, planes, grids, bounding wireframes, camera frustums, and custom `ObjTriangle` 3D meshes (`createObjMesh`).

### 2. Scene Graph & Rigging (`com.star4droid.mc.animation.engine.scene`)
- **`SceneNode.kt`**: Node object containing base/animated transforms, parent/child relationships, node type (`CHARACTER_ROOT`, `CHARACTER_PART`, `BLOCK`, `HALF_BLOCK`, `STEP_BLOCK`, `PLANE`, `CAMERA`, `LIGHT`, `GROUND`), material, bounding box dimensions, and optional 3D OBJ mesh data (`objModelData`).
- **`CharacterFactory.kt`**: Constructs hierarchically rigged Minecraft characters (Steve, Alex, Zombie, Knight, Miner) with 11 parent-child joint nodes (Torso -> Head, Upper Arm -> Forearm, Thigh -> Lower Leg / Calf).

### 3. Timeline & Block-Based Animation System (`com.star4droid.mc.animation.animation`)
- **`ActionBlock.kt` / `ActionBlockType.kt`**: 22+ block-based animation actions (`WALK`, `RUN`, `JUMP`, `WAVE`, `ROTATE`, `TILT_HEAD`, `PUNCH`, `LOOK_LEFT`, `LOOK_RIGHT`, `BACKFLIP`, `JUMP_FRONT`, `SIT_DOWN`, `STAND_UP`, `KICK`, `NOD_HEAD`, `SHAKE_HEAD`, `CLAP`, `CHEER`, `SHRUG`, `CROSS_ARMS`, `BOW`, `DEATH_FALL`, `SNEAK_WALK`, `SPIN_ATTACK`, `BLOCK_SHIELD`, `TAUNT`).
- **`AnimationEvaluator.kt`**: Evaluates timeline tracks per node independently at time `t`, accumulating skeletal joint rotations and root spatial displacement.

### 4. AI Animation Studio & Gemini API (`com.star4droid.mc.animation.ui.ai_studio` & `com.star4droid.mc.animation.ai`)
- **`AiAnimationStudioScreen.kt`**: Dedicated 3D studio viewport screen with top character/block switchers, lighting, circular chat FAB button, replay overlay, and direct timeline integration.
- **`AiAnimationChatOverlay.kt`**: Drawer chat with action icons (History, New Chat, Clear, Settings, Close), smooth rotating star loader (`Icons.Default.AutoAwesome`), and interactive `[Animation Created - Play]` cards.
- **`GeminiApiService.kt`**: REST API client calling Gemini (`gemini-3.5-flash` default model), generating explicit keyframes per target node (`head`, `body`, `rightArm`, `rightForearm`, `leftArm`, `leftForearm`, `rightLeg`/`rightThigh`, `rightLowerLeg`/`rightCalf`, `leftLeg`/`leftThigh`, `leftLowerLeg`/`leftCalf`, `root`, `block`), with prompt refinement context (passing previous animation JSON for iterative editing).
- **`GeminiSettingsDialog.kt`**: Dialog for configuring API Key and model selection.

### 5. Custom Action Blocks Manager (`com.star4droid.mc.animation.ui.blocks`)
- **`CustomBlocksManagerDialog.kt`**: Accessible via top scrollable panel icon (`Icons.Default.Extension`). Allows users to search, filter, preview, delete, export, and import custom JSON animation block presets into the timeline.
- **`CustomBlocksRepository.kt`**: Local file repository managing custom JSON animation presets.

### 6. Interactive Editor & Controls (`com.star4droid.mc.animation.ui`)
- **`EditorScreen.kt`**: Standardized top scrollable panel with icon-only buttons (Rotate Screen, Lock, Select, Move, Rotate, Scale, Camera, Build, AI Studio, Custom Blocks Manager, Save), with separated panel toggles (Tree, Inspect, Timeline) placed cleanly under the top bar. Restructured Add Item menu includes `Add Block 🧊`, `Add Half Block (Slab) 🧱`, `Add Step Block (Stairs) 🪜`, `Add Character 👤`, `Add Light 💡`, `Add Plane 🗺️`, `Import 3D Model 📦`.
- **`Viewport3D.kt` / `EditorGLSurfaceView.kt`**: Handles 3D touch input (Camera Orbit/Pan/Unlimited Zoom, Gizmo drag, Build Mode face-snapped block placement/deletion).
- **`InspectorPanel.kt`**: Object property editor with horizontal touch-swipe transform fields, numeric keypad popup, and `formatFloatValue` flexible precision (up to 4 decimals, supporting values like `0.001` or `0.25`).
- **`HierarchyPanel.kt`**: Scene graph tree view with dual horizontal and vertical scrolling.
- **`TimelinePanel.kt`**: Multi-track timeline canvas supporting block dragging, track row organization, block settings dialogs, and instant block filtering per selected object.

### 7. Project File Storage Architecture (`com.star4droid.mc.animation.project`)
- **`ProjectRepository.kt`**: Each project has its dedicated folder under `Android/data/<package_name>/files/<projectName>/` with the following subdirectories:
  - `models/`: Custom OBJ models and 3D meshes
  - `sounds/`: Audio assets and keyframe sound tracks
  - `textures/`: Custom block skins and character textures
  - `scenes/`: `scene.json` scene graph files
  - `animations/`: Timeline action block and keyframe preset JSON files
  - `project.json`: Project metadata and timestamp logs

### 8. App Assets & Templates (`app/src/main/assets/`)
- **`Sample/`**: Complete 30.0-second animated village city project template containing houses, watchtower with battlements, street lamps, roads, and 6 animated character rigs (Steve, Alex, Zombie, Knight Guard, Knight Patrol, Miner Joe) with cinematic camera tracks.

### 9. Skills Documentation (`skills/`)
- **`skills/Block Animation Skills.md`**: JSON keyframe specification and instructions for block animations.
- **`skills/Character Animations.skill.md`**: Multi-limb keyframe specification and limb hierarchy for character animations.
- **`skills/Project Skills.skill.md`**: Complete technical guide for AI agents to inspect, modify, build, and serialize projects, scene graphs, 3D nodes (Cube, Slab, Stairs, Plane, Ground, Camera, Light, Character), and timeline keyframes.

---

## Guidance for AI Assistants & Developers

1. **Transform Matrices & Hierarchy**: Node transforms are evaluated recursively in `SceneGraph.updateWorldMatrices()`: `WorldMatrix = ParentWorldMatrix * LocalMatrix`.
2. **Character Joints**: Limb parts (`rightForearm`, `leftForearm`, `rightLowerLeg`/`rightCalf`, `leftLowerLeg`/`leftCalf`, etc.) are child nodes anchored at their parent joint hinges. Do NOT separate forearm/leg nodes from upper limbs.
3. **AI Animations**: AI generates keyframe-based JSON targeting all 11 character limb nodes or block nodes. Keyframes specify `time`, `position`, `rotation`, and `scale`.
4. **Per-Project Directory Structure**: All project files are stored in `Android/data/<package>/files/<projectName>/` with `models/`, `sounds/`, `textures/`, `scenes/`, `animations/` subfolders.
5. **Top Toolbar Styling**: Top bar controls are strictly icon-only without text labels for clean visual hierarchy.
6. **Scale & Numeric Formatting**: Scale supports micro-scaling down to `0.00001f`. Always format float displays with `formatFloatValue` (or Locale.US decimal point) to avoid rounding small values like `0.001` or `0.25`.
