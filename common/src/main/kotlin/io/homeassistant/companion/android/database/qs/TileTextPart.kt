package io.homeassistant.companion.android.database.qs

import kotlinx.serialization.Serializable

@Serializable
data class TileTextPart(val source: String, val value: String? = null) {
    val sourceType: TileTextSource
        get() = TileTextSource.fromStorageValue(source)

    companion object {
        fun fixed(text: String = "") = TileTextPart(TileTextSource.FIXED.storageValue, text)
        fun name() = TileTextPart(TileTextSource.NAME.storageValue)
        fun state() = TileTextPart(TileTextSource.STATE.storageValue)
        fun attribute(name: String? = null) = TileTextPart(TileTextSource.ATTRIBUTE.storageValue, name)
    }
}
