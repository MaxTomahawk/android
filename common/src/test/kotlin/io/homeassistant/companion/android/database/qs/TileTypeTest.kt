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
    fun `Given stored tap action when reading then map to matching action`() {
        assertEquals(TileTapAction.Automatic, TileTapAction.fromStorageValue("automatic"))
        assertEquals(TileTapAction.MoreInfo, TileTapAction.fromStorageValue("more_info"))
        assertEquals(TileTapAction.Custom, TileTapAction.fromStorageValue("custom"))
        assertEquals(TileTapAction.None, TileTapAction.fromStorageValue("none"))
        assertEquals(TileTapAction.Automatic, TileTapAction.fromStorageValue("unknown"))
    }

    @Test
    fun `Given basic tile without entity when checking setup then reject it`() {
        assertFalse(tile(type = TileType.Basic, entityId = "").isSetup)
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
        stateTemplate: String? = null,
        tapAction: TileTapAction = TileTapAction.Automatic,
    ) = TileEntity(
        tileId = "tile_1",
        added = true,
        serverId = 1,
        iconName = null,
        entityId = entityId,
        label = "Test",
        subtitle = null,
        shouldVibrate = false,
        authRequired = false,
        tileType = type.storageValue,
        stateTemplate = stateTemplate,
        tapAction = tapAction.storageValue,
    )
}
