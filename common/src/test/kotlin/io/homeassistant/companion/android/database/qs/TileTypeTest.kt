package io.homeassistant.companion.android.database.qs

import org.junit.jupiter.api.Assertions.assertEquals
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
}
