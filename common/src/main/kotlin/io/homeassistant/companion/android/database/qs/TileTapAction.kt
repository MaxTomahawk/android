package io.homeassistant.companion.android.database.qs

sealed interface TileTapAction {
    val storageValue: String

    data object Automatic : TileTapAction {
        override val storageValue = "automatic"
    }

    data object MoreInfo : TileTapAction {
        override val storageValue = "more_info"
    }

    data object Custom : TileTapAction {
        override val storageValue = "custom"
    }

    data object None : TileTapAction {
        override val storageValue = "none"
    }

    companion object {
        fun fromStorageValue(value: String): TileTapAction = when (value) {
            MoreInfo.storageValue -> MoreInfo
            Custom.storageValue -> Custom
            None.storageValue -> None
            else -> Automatic
        }
    }
}
