package io.homeassistant.companion.android.database.qs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileControlDialogConfigTest {

    @Test
    fun `Given custom control dialog when encoding and decoding then preserve controls`() {
        val config = TileControlDialogConfig(
            mode = TileControlDialogMode.CUSTOM,
            controls = listOf(
                TileControlItem(
                    type = TileControlType.SLIDER,
                    entityId = "light.kitchen",
                    label = "Brightness",
                    attribute = "brightness",
                    actionDomain = "light",
                    actionName = "turn_on",
                    actionField = "brightness_pct",
                    min = 0f,
                    max = 100f,
                    step = 1f,
                    unit = "%",
                ),
                TileControlItem(
                    type = TileControlType.GROUP_MEMBERS,
                    entityId = "light.downstairs",
                ),
            ),
        )

        assertEquals(config, decodeTileControlDialogConfig(encodeTileControlDialogConfig(config)))
    }
}
