package io.homeassistant.companion.android.database.qs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileAdvancedConfigTest {

    @Test
    fun `Given text parts when encoding and decoding then preserve order and values`() {
        val parts = listOf(
            TileTextPart.fixed("Temp: "),
            TileTextPart.state(),
            TileTextPart.fixed(" · "),
            TileTextPart.attribute("humidity"),
        )

        assertEquals(parts, decodeTileTextParts(encodeTileTextParts(parts)))
    }

    @Test
    fun `Given icon rules when encoding and decoding then preserve rules`() {
        val rules = listOf(
            TileIconRule(state = "on", iconName = "lightbulb-on"),
            TileIconRule(state = "off", iconName = "lightbulb-off"),
        )

        assertEquals(rules, decodeTileIconRules(encodeTileIconRules(rules)))
    }

    @Test
    fun `Given active states when encoding and decoding then ignore blank entries`() {
        val encoded = encodeStringList(listOf("on", "", "playing"))
        assertEquals(listOf("on", "playing"), decodeStringList(encoded))
    }
}
