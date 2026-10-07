package io.homeassistant.companion.android.settings.qs

import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Lightbulb
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.qs.TileIconRule
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileTextPart
import io.homeassistant.companion.android.database.qs.TileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TileYamlCodecTest {

    @Test
    fun `Given advanced entity config when round tripping YAML then preserve visual configuration`() {
        val original = ManageTilesState(
            selectedTileType = TileType.Entity,
            selectedEntityId = "light.kitchen",
            selectedStateEntityId = "binary_sensor.presence",
            labelParts = listOf(
                TileTextPart.fixed("Kitchen · "),
                TileTextPart.state(),
            ),
            subtitleParts = listOf(
                TileTextPart.attribute("brightness"),
                TileTextPart.fixed(" %"),
            ),
            customIcon = Mdi.Lightbulb,
            iconRules = listOf(
                TileIconRule("on", "mdi:lightbulb-on"),
                TileIconRule("off", "mdi:lightbulb-off"),
            ),
            activeStates = listOf("on", "opening"),
            selectedTapAction = TileTapAction.PerformAction,
            actionDomain = "light",
            actionName = "turn_on",
            tapActionFieldValues = mapOf(
                "brightness_pct" to "42",
                "transition" to "1.5",
            ),
            tapTargetEntityId = "light.kitchen",
            tapTargetDeviceId = "device-1",
            tapTargetAreaId = "kitchen",
            selectedHoldAction = TileTapAction.Navigate,
            holdNavigationPath = "/lovelace/lights",
            tileStateTemplate = "{{ is_state('binary_sensor.presence', 'on') }}",
            tileStateDescriptionTemplate = "{{ states('binary_sensor.presence') }}",
            tileIconTemplate = "{{ 'mdi:lightbulb' }}",
            tileContentDescriptionTemplate = "{{ state_attr('light.kitchen', 'friendly_name') }}",
            selectedShouldVibrate = true,
            tileAuthRequired = true,
        )

        val decoded = TileYamlCodec.decode(TileYamlCodec.encode(original), ManageTilesState())

        assertEquals(TileType.Entity, decoded.selectedTileType)
        assertEquals(original.selectedEntityId, decoded.selectedEntityId)
        assertEquals(original.selectedStateEntityId, decoded.selectedStateEntityId)
        assertEquals(original.labelParts, decoded.labelParts)
        assertEquals(original.subtitleParts, decoded.subtitleParts)
        assertEquals(Mdi.Lightbulb.mdiName, decoded.customIcon?.mdiName)
        assertEquals(original.iconRules, decoded.iconRules)
        assertEquals(original.activeStates, decoded.activeStates)
        assertEquals(original.selectedTapAction, decoded.selectedTapAction)
        assertEquals(original.actionDomain, decoded.actionDomain)
        assertEquals(original.actionName, decoded.actionName)
        assertEquals(original.tapActionFieldValues, decoded.tapActionFieldValues)
        assertEquals(original.tapTargetEntityId, decoded.tapTargetEntityId)
        assertEquals(original.tapTargetDeviceId, decoded.tapTargetDeviceId)
        assertEquals(original.tapTargetAreaId, decoded.tapTargetAreaId)
        assertEquals(original.selectedHoldAction, decoded.selectedHoldAction)
        assertEquals(original.holdNavigationPath, decoded.holdNavigationPath)
        assertEquals(original.tileStateTemplate, decoded.tileStateTemplate)
        assertEquals(original.tileStateDescriptionTemplate, decoded.tileStateDescriptionTemplate)
        assertEquals(original.tileIconTemplate, decoded.tileIconTemplate)
        assertEquals(original.tileContentDescriptionTemplate, decoded.tileContentDescriptionTemplate)
        assertEquals(original.selectedShouldVibrate, decoded.selectedShouldVibrate)
        assertEquals(original.tileAuthRequired, decoded.tileAuthRequired)
    }

    @Test
    fun `Given legacy action data template when round tripping YAML then preserve template`() {
        val template = """{"brightness_pct": {{ states('sensor.brightness') }}}"""
        val original = ManageTilesState(
            selectedTileType = TileType.Template,
            selectedEntityId = "light.kitchen",
            tileLabel = "Kitchen",
            selectedTapAction = TileTapAction.PerformAction,
            actionDomain = "light",
            actionName = "turn_on",
            actionDataTemplate = template,
        )

        val decoded = TileYamlCodec.decode(TileYamlCodec.encode(original), ManageTilesState())

        assertEquals(template, decoded.actionDataTemplate)
        assertEquals(TileTapAction.PerformAction, decoded.selectedTapAction)
    }

    @Test
    fun `Given scalar YAML when decoding then reject configuration`() {
        assertThrows(IllegalArgumentException::class.java) {
            TileYamlCodec.decode("not-a-mapping", ManageTilesState())
        }
    }

    @Test
    fun `Given missing hold action when decoding then use modern more-info default`() {
        val decoded = TileYamlCodec.decode(
            """
            tile_type: entity
            entity: light.kitchen
            label:
              - type: name
            """.trimIndent(),
            ManageTilesState(),
        )

        assertEquals(TileTapAction.Automatic, decoded.selectedTapAction)
        assertEquals(TileTapAction.MoreInfo, decoded.selectedHoldAction)
    }
}
