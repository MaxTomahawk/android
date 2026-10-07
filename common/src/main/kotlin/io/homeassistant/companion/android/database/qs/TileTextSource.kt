package io.homeassistant.companion.android.database.qs

enum class TileTextSource(val storageValue: String) {
    FIXED("fixed"),
    NAME("name"),
    STATE("state"),
    ATTRIBUTE("attribute"),
    ;

    companion object {
        fun fromStorageValue(value: String): TileTextSource = entries.firstOrNull { it.storageValue == value } ?: FIXED
    }
}
