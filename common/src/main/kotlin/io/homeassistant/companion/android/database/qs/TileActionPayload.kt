package io.homeassistant.companion.android.database.qs

private val tileActionTargetKeys = setOf("entity_id", "device_id", "area_id")

data class TileActionPayload(val serviceData: Map<String, Any?>, val target: Map<String, Any?>)

fun splitTileActionData(data: Map<String, Any?>): TileActionPayload {
    val target = data.filterKeys { it in tileActionTargetKeys }
    return TileActionPayload(
        serviceData = data.filterKeys { it !in tileActionTargetKeys },
        target = target,
    )
}
