package com.star4droid.mc.animation.engine.scene

enum class SceneNodeType {
    BLOCK,
    GROUP,
    CHARACTER_ROOT,
    CHARACTER_PART,
    CAMERA,
    LIGHT,
    GROUND
}

enum class CharacterPartType {
    ROOT,
    HEAD,
    BODY,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_LEG,
    RIGHT_LEG
}

enum class TimeOfDay {
    MORNING,
    NOON,
    EVENING,
    NIGHT
}

data class Material(
    val textureAssetId: String = "grass",
    val color: Int = 0xFFFFFFFF.toInt(),
    val opacity: Float = 1.0f
)

data class CameraData(
    val fov: Float = 60f,
    val near: Float = 0.1f,
    val far: Float = 1000f,
    val enabled: Boolean = true
)

data class LightData(
    val color: Int = 0xFFFFF2D4.toInt(),
    val intensity: Float = 1.2f,
    val timeOfDay: TimeOfDay = TimeOfDay.NOON,
    val shadows: Boolean = true
)
