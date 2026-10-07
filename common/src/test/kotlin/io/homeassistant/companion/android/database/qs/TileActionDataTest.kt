package io.homeassistant.companion.android.database.qs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileActionDataTest {

    @Test
    fun `Given mixed action data when splitting then move only native target keys to target`() {
        val payload = splitTileActionData(
            mapOf(
                "entity_id" to "light.kitchen",
                "device_id" to "device-1",
                "area_id" to "kitchen",
                "brightness_pct" to 42,
                "transition" to 1.5,
            ),
        )

        assertEquals(
            mapOf(
                "entity_id" to "light.kitchen",
                "device_id" to "device-1",
                "area_id" to "kitchen",
            ),
            payload.target,
        )
        assertEquals(
            mapOf(
                "brightness_pct" to 42,
                "transition" to 1.5,
            ),
            payload.serviceData,
        )
    }
}
