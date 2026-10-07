package io.homeassistant.companion.android.qs

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.qs.TileControlType
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuickSettingsControlDialogTest {

    @Test
    fun `Given capable light group when building automatic controls then expose rich light controls and members`() {
        val entity = Entity(
            entityId = "light.downstairs",
            state = "on",
            attributes = mapOf(
                "friendly_name" to "Downstairs",
                "supported_color_modes" to listOf("color_temp", "hs"),
                "brightness" to 128,
                "color_mode" to "hs",
                "hs_color" to listOf(120.0, 50.0),
                "min_color_temp_kelvin" to 2000,
                "max_color_temp_kelvin" to 6500,
                "effect_list" to listOf("None", "Relax"),
                "entity_id" to listOf("light.table", "light.floor"),
            ),
            lastChanged = LocalDateTime.now(),
            lastUpdated = LocalDateTime.now(),
        )

        assertEquals(
            listOf(
                TileControlType.TOGGLE,
                TileControlType.SLIDER,
                TileControlType.COLOR,
                TileControlType.COLOR_TEMPERATURE,
                TileControlType.SELECT,
                TileControlType.GROUP_MEMBERS,
            ),
            automaticControls(entity).map { it.type },
        )
    }
}
