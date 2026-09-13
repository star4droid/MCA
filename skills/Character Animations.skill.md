# Character Animation Skill

This skill defines the JSON keyframe specification and limb hierarchy for generating custom multi-limb 3D Character animations for Minecraft characters (Steve, Alex, Zombie, Knight, Miner) in the Minecraft 3D Animator (MCA) app.

## Character Limb Hierarchy & Target Nodes
The character model consists of 11 hierarchical target nodes that can be independently animated with keyframes:
- `root`: Character base position and orientation
- `head`: Head rotation (pitch, yaw, roll)
- `body`: Torso / chest rotation and position
- `rightArm`: Right shoulder / upper arm rotation
- `rightForearm`: Right elbow bend angle / forearm rotation
- `leftArm`: Left shoulder / upper arm rotation
- `leftForearm`: Left elbow bend angle / forearm rotation
- `rightLeg` (or `rightThigh`): Right hip / upper leg rotation
- `rightLowerLeg` (or `rightCalf`): Right knee bend angle / lower leg rotation
- `leftLeg` (or `leftThigh`): Left hip / upper leg rotation
- `leftLowerLeg` (or `leftCalf`): Left knee bend angle / lower leg rotation

## JSON Schema Example

```json
{
  "name": "Full Body Walk & Punch",
  "description": "Character walks forward while bending elbow and punching with right forearm",
  "category": "CHARACTER",
  "duration": 2.0,
  "tracks": [
    {
      "targetNode": "head",
      "keyframes": [
        { "time": 0.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 0.5, "rotation": [5.0, 10.0, 0.0] },
        { "time": 1.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 1.5, "rotation": [5.0, -10.0, 0.0] },
        { "time": 2.0, "rotation": [0.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "rightArm",
      "keyframes": [
        { "time": 0.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 0.5, "rotation": [-75.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [-15.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [0.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "rightForearm",
      "keyframes": [
        { "time": 0.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 0.5, "rotation": [-45.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [0.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "leftArm",
      "keyframes": [
        { "time": 0.0, "rotation": [30.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [-30.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [30.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "leftForearm",
      "keyframes": [
        { "time": 0.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [-20.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [0.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "rightLeg",
      "keyframes": [
        { "time": 0.0, "rotation": [30.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [-30.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [30.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "rightLowerLeg",
      "keyframes": [
        { "time": 0.0, "rotation": [15.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [15.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "leftLeg",
      "keyframes": [
        { "time": 0.0, "rotation": [-30.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [30.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [-30.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "leftLowerLeg",
      "keyframes": [
        { "time": 0.0, "rotation": [0.0, 0.0, 0.0] },
        { "time": 1.0, "rotation": [15.0, 0.0, 0.0] },
        { "time": 2.0, "rotation": [0.0, 0.0, 0.0] }
      ]
    },
    {
      "targetNode": "root",
      "keyframes": [
        { "time": 0.0, "position": [0.0, 0.0, 0.0] },
        { "time": 2.0, "position": [0.0, 0.0, 2.0] }
      ]
    }
  ]
}
```

## Keyframe Format
Each keyframe inside a target node track specifies:
- `time`: Floating point timestamp in seconds (0.0 to duration).
- `position`: Optional `[x, y, z]` vector for translation offset.
- `rotation`: Optional `[rx, ry, rz]` vector in degrees for rotation angles.
- `scale`: Optional `[sx, sy, sz]` scaling vector.

## Custom Action Blocks Manager Integration
Export or copy this JSON format to share or import directly into the **Custom Action Blocks Manager** in MCA to create a new reusable keyframe action block preset.
