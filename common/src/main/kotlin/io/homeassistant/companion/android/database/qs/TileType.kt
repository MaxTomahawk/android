package io.homeassistant.companion.android.database.qs

sealed interface TileType {
    val storageValue: String

    data object Basic : TileType {
        override val storageValue = "basic"
    }

    data object Entity : TileType {
        override val storageValue = "entity"
    }

    data object Template : TileType {
        override val storageValue = "template"
    }

    companion object {
        fun fromStorageValue(value: String): TileType = when (value) {
            Entity.storageValue -> Entity
            Template.storageValue -> Template
            else -> Basic
        }
    }
}
