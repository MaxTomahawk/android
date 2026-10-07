package io.homeassistant.companion.android.database.qs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileTypeTest {

    @Test
    fun `Given stored tile type when reading then map to matching type`() {
        assertEquals(TileType.Basic, TileType.fromStorageValue("basic"))
        assertEquals(TileType.Entity, TileType.fromStorageValue("entity"))
        assertEquals(TileType.Template, TileType.fromStorageValue("template"))
    }

    @Test
    fun `Given unknown stored tile type when reading then fall back to basic`() {
        assertEquals(TileType.Basic, TileType.fromStorageValue("unknown"))
    }

    @Test
    fun `Given stored text source when reading then map to matching source`() {
        assertEquals(TileTextSource.FIXED, TileTextSource.fromStorageValue("fixed"))
        assertEquals(TileTextSource.NAME, TileTextSource.fromStorageValue("name"))
        assertEquals(TileTextSource.STATE, TileTextSource.fromStorageValue("state"))
        assertEquals(TileTextSource.ATTRIBUTE, TileTextSource.fromStorageValue("attribute"))
        assertEquals(TileTextSource.FIXED, TileTextSource.fromStorageValue("unknown"))
    }

    @Test
    fun `Given stored tap action when reading then map to matching action`() {
        assertEquals(TileTapAction.Automatic, TileTapAction.fromStorageValue("automatic"))
        assertEquals(TileTapAction.Toggle, TileTapAction.fromStorageValue("toggle"))
        assertEquals(TileTapAction.MoreInfo, TileTapAction.fromStorageValue("more_info"))
        assertEquals(TileTapAction.Controls, TileTapAction.fromStorageValue("controls"))
        assertEquals(TileTapAction.PerformAction, TileTapAction.fromStorageValue("perform_action"))
        assertEquals(TileTapAction.PerformAction, TileTapAction.fromStorageValue("custom"))
        assertEquals(TileTapAction.Navigate, TileTapAction.fromStorageValue("navigate"))
        assertEquals(TileTapAction.Url, TileTapAction.fromStorageValue("url"))
        assertEquals(TileTapAction.Assist, TileTapAction.fromStorageValue("assist"))
        assertEquals(TileTapAction.None, TileTapAction.fromStorageValue("none"))
        assertEquals(TileTapAction.Automatic, TileTapAction.fromStorageValue("unknown"))
    }

    @Test
    fun `Given basic tile without entity when checking setup then reject it`() {
        assertFalse(tile(type = TileType.Basic, entityId = "").isSetup)
    }

    @Test
    fun `Given visual entity tile with dynamic name and blank stored label when checking setup then accept it`() {
        assertTrue(
            tile(
                type = TileType.Entity,
                entityId = "light.kitchen",
                label = "",
                labelSource = TileTextSource.NAME,
            ).isSetup,
        )
    }

    @Test
    fun `Given entity tile with multipart label when checking setup then accept it`() {
        assertTrue(
            tile(
                type = TileType.Entity,
                entityId = "sensor.climate",
                label = "",
                labelPartsJson = encodeTileTextParts(
                    listOf(
                        TileTextPart.fixed("Temp: "),
                        TileTextPart.state(),
                    ),
                ),
            ).isSetup,
        )
    }

    @Test
    fun `Given template tile without entity but with state template when checking setup then accept it`() {
        assertTrue(
            tile(
                type = TileType.Template,
                entityId = "",
                stateTemplate = "{{ states('sensor.temperature') }}",
                tapAction = TileTapAction.None,
            ).isSetup,
        )
    }

    private fun tile(
        type: TileType,
        entityId: String,
        label: String = "Test",
        labelSource: TileTextSource = TileTextSource.FIXED,
        stateTemplate: String? = null,
        tapAction: TileTapAction = TileTapAction.Automatic,
        labelPartsJson: String? = null,
    ) = TileEntity(
        tileId = "tile_1",
        added = true,
        serverId = 1,
        iconName = null,
        entityId = entityId,
        label = label,
        subtitle = null,
        labelSource = labelSource.storageValue,
        shouldVibrate = false,
        authRequired = false,
        tileType = type.storageValue,
        labelPartsJson = labelPartsJson,
        stateTemplate = stateTemplate,
        tapAction = tapAction.storageValue,
    )
}
