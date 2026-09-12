package com.star4droid.mc.animation.engine.scene

enum class SceneNodeType {
    BLOCK,
    GROUP,
    CHARACTER_ROOT,
    CHARACTER_PART,
    CAMERA,
    LIGHT,
    GROUND,
    PLANE
}

enum class CharacterPartType {
    ROOT,
    HEAD,
    BODY,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_FOREARM,
    RIGHT_FOREARM,
    LEFT_LEG,
    RIGHT_LEG,
    LEFT_LOWER_LEG,
    RIGHT_LOWER_LEG
}

enum class TimeOfDay {
    MORNING,
    NOON,
    EVENING,
    NIGHT
}

enum class LightType {
    SUN,
    POINT,
    SPOT
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
    val lightType: LightType = LightType.POINT,
    val color: Int = 0xFFFFF2D4.toInt(),
    val intensity: Float = 1.2f,
    val range: Float = 15.0f,
    val coneAngle: Float = 45.0f,
    val timeOfDay: TimeOfDay = TimeOfDay.NOON,
    val shadows: Boolean = true,
    val showHelperLines: Boolean = false
)

