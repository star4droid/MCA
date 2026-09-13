# Block Animation Skills

This skill defines the JSON specification and instructions for generating custom 3D Block keyframe animations for the Minecraft 3D Animator (MCA) app.

## Overview
AI creates keyframe animations for single blocks or custom block models by manipulating their transform properties (`position`, `rotation`, `scale`) over time using explicit keyframes on target node tracks.

## JSON Schema

```json
{
  "name": "Block Shake & Bounce",
  "description": "Block shakes rapidly left and right while bouncing up and down",
  "category": "BLOCK",
  "duration": 1.5,
  "tracks": [
    {
      "targetNode": "block",
      "keyframes": [
        { "time": 0.0, "position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0], "scale": [1.0, 1.0, 1.0] },
        { "time": 0.25, "position": [-0.2, 0.3, 0.0], "rotation": [0.0, 0.0, -15.0] },
        { "time": 0.5, "position": [0.2, 0.6, 0.0], "rotation": [0.0, 0.0, 15.0] },
        { "time": 0.75, "position": [-0.1, 0.3, 0.0], "rotation": [0.0, 0.0, -10.0] },
        { "time": 1.0, "position": [0.1, 0.0, 0.0], "rotation": [0.0, 0.0, 5.0] },
        { "time": 1.5, "position": [0.0, 0.0, 0.0], "rotation": [0.0, 0.0, 0.0] }
      ]
    }
  ]
}
```

## Track & Keyframe Properties
- `targetNode`: `"block"` or `"root"` (the target block object).
- `keyframes`: Array of keyframe objects sorted by `time`.
  - `time`: Time offset in seconds (e.g. `0.0`, `0.5`, `1.0`).
  - `position`: `[x, y, z]` positional offset relative to base position.
  - `rotation`: `[rx, ry, rz]` rotation angles in degrees (Euler angles).
  - `scale`: `[sx, sy, sz]` scaling factors (default `[1.0, 1.0, 1.0]`).

## Custom Blocks Manager Integration
Export or copy this JSON format to share or import directly into the **Custom Action Blocks Manager** in MCA to create a new reusable keyframe action block preset.
