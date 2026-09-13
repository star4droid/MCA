# Project Skills

This skill defines the complete JSON specifications, folder structures, scene graph hierarchy, 3D block geometry types, character rigging, and animation timeline schemas for the Minecraft 3D Animator (MCA) application.

---

## 1. Project Folder Architecture

Each project lives in its dedicated directory under:
`Android/data/<package_name>/files/<projectName>/`

Subfolder layout:
- `project.json`: Metadata (id, name, creation timestamp, nodeCount, timelineCount).
- `scenes/scene.json`: Scene Graph tree containing all nodes, transforms, materials, lights, and parameters.
- `animations/timeline.json`: Multi-track timeline containing action blocks, custom keyframes, sound tracks, and track rows.
- `custom_blocks/`: Custom JSON action blocks specific to the project.
- `models/`: Custom 3D `.obj` models and mesh assets.
- `textures/`: Custom texture images (`.png`/`.jpg`).
- `sounds/`: Keyframe sound files (`.mp3`/`.wav`).

---

## 2. `project.json` Specification

```json
{
  "id": "sample_village_city",
  "name": "Sample Large Village City",
  "version": 1,
  "createdAt": 1726200000000,
  "updatedAt": 1726200000000,
  "nodeCount": 120,
  "timelineCount": 1
}
```

---

## 3. `scenes/scene.json` Specification

The `scene.json` file contains `timeOfDay` (`MORNING`, `NOON`, `EVENING`, `NIGHT`) and an array of `nodes`:

```json
{
  "timeOfDay": "NOON",
  "nodes": [
    {
      "id": "node_uuid",
      "name": "Wall Block",
      "type": "BLOCK",
      "parentId": null,
      "children": [],
      "baseTransform": {
        "position": { "x": 0.0, "y": 0.5, "z": 0.0 },
        "rotation": { "x": 0.0, "y": 0.0, "z": 0.0 },
        "scale": { "x": 1.0, "y": 1.0, "z": 1.0 },
        "pivot": { "x": 0.0, "y": 0.0, "z": 0.0 }
      },
      "material": {
        "color": -1,
        "textureAssetId": "brick",
        "opacity": 1.0
      },
      "boxDimensions": { "x": 1.0, "y": 1.0, "z": 1.0 }
    }
  ]
}
```

### Supported `type` Values:
- `BLOCK`: Standard 1x1x1 Minecraft cube block.
- `HALF_BLOCK`: Slab half-height block (1.0 x 0.5 x 1.0).
- `STEP_BLOCK`: Stair step block (lower slab 1.0x0.5x1.0 + upper step 1.0x0.5x0.5).
- `PLANE`: Thin 2D surface plane.
- `GROUND`: Ground terrain platform.
- `CHARACTER_ROOT`: Root parent node for a character rig.
- `CHARACTER_PART`: Skeletal joint node (`HEAD`, `BODY`, `LEFT_ARM`, `RIGHT_ARM`, `LEFT_FOREARM`, `RIGHT_FOREARM`, `LEFT_LEG`, `RIGHT_LEG`, `LEFT_LOWER_LEG`, `RIGHT_LOWER_LEG`).
- `CAMERA`: Scene camera node.
- `LIGHT`: Sun light, point light, or spot light node.
- `GROUP`: Container folder node.

### Built-in Texture Identifiers (`textureAssetId`):
`grass`, `dirt`, `stone`, `cobblestone`, `oak_planks`, `brick`, `diamond`, `gold_block`, `iron_block`, `crafting_table`, `tnt`, `bookshelf`, `obsidian`, `sand`, `glass`.

---

## 4. `animations/timeline.json` Specification

`timeline.json` stores the timeline duration (e.g. 30.0s), action blocks, and explicit node keyframe tracks:

```json
{
  "duration": 30.0,
  "timelines": [
    {
      "id": "timeline_main",
      "name": "Main Timeline",
      "duration": 30.0,
      "actionBlocks": [
        {
          "id": "block_walk_01",
          "type": "WALK",
          "targetNodeId": "character_root_id",
          "startTime": 0.0,
          "duration": 5.0,
          "speed": 1.0,
          "stepSize": 1.0,
          "trackRow": 0,
          "isDeltaBased": true
        },
        {
          "id": "block_wave_01",
          "type": "WAVE",
          "targetNodeId": "character_root_id",
          "startTime": 5.2,
          "duration": 3.0,
          "speed": 1.0,
          "trackRow": 0
        }
      ],
      "tracks": [
        {
          "targetNodeId": "camera_main_id",
          "propertyName": "transform.position.x",
          "keyframes": [
            { "time": 0.0, "value": -15.0, "interpolation": "SMOOTH" },
            { "time": 10.0, "value": 0.0, "interpolation": "SMOOTH" },
            { "time": 20.0, "value": 15.0, "interpolation": "SMOOTH" },
            { "time": 30.0, "value": 0.0, "interpolation": "SMOOTH" }
          ]
        }
      ]
    }
  ]
}
```

---

## 5. How AI Models Construct or Edit MCA Projects

1. **Hierarchy Building**:
   - Create roads using `BLOCK` with `"cobblestone"` or `"stone"`.
   - Build house walls using `BLOCK` with `"brick"` or `"oak_planks"`.
   - Add roofs using `STEP_BLOCK` (stairs) and `HALF_BLOCK` (slabs).
   - Place guard towers with `STEP_BLOCK` battlements.
   - Add street lamps using `BLOCK` + `LIGHT` with `"POINT"` type.
2. **Character Placement**:
   - Add `CHARACTER_ROOT` and 10 child joint nodes (`HEAD`, `BODY`, `LEFT_ARM`, `RIGHT_ARM`, `LEFT_FOREARM`, `RIGHT_FOREARM`, `LEFT_LEG`, `RIGHT_LEG`, `LEFT_LOWER_LEG`, `RIGHT_LOWER_LEG`).
3. **Cinematic 30-Second Camera Track**:
   - Create keyframes on `CAMERA` node spanning 0.0s to 30.0s for smooth position/rotation travel through the village.
