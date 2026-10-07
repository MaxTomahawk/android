package io.homeassistant.companion.android.database.qs

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class TileControlDialogConfig(
    val mode: TileControlDialogMode = TileControlDialogMode.AUTOMATIC,
    val controls: List<TileControlItem> = emptyList(),
)

@Serializable
enum class TileControlDialogMode {
    AUTOMATIC,
    CUSTOM,
}

@Serializable
data class TileControlItem(
    val type: TileControlType = TileControlType.ENTITY_STATE,
    val entityId: String? = null,
    val label: String? = null,
    val attribute: String? = null,
    val actionDomain: String? = null,
    val actionName: String? = null,
    val actionField: String? = null,
    val actionData: Map<String, String> = emptyMap(),
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val unit: String? = null,
)

@Serializable
enum class TileControlType {
    ENTITY_STATE,
    TOGGLE,
    SLIDER,
    COLOR,
    COLOR_TEMPERATURE,
    SELECT,
    ACTION,
    GROUP_MEMBERS,
}

fun encodeTileControlDialogConfig(config: TileControlDialogConfig): String = kotlinJsonMapper.encodeToString(config)

fun decodeTileControlDialogConfig(value: String?): TileControlDialogConfig = if (value.isNullOrBlank()) {
    TileControlDialogConfig()
} else {
    runCatching { kotlinJsonMapper.decodeFromString<TileControlDialogConfig>(value) }
        .getOrDefault(TileControlDialogConfig())
}
