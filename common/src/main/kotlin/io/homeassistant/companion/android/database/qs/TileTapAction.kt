package io.homeassistant.companion.android.database.qs

sealed interface TileTapAction {
    val storageValue: String

    data object Automatic : TileTapAction {
        override val storageValue = "automatic"
    }

    data object Toggle : TileTapAction {
        override val storageValue = "toggle"
    }

    data object MoreInfo : TileTapAction {
        override val storageValue = "more_info"
    }

    data object PerformAction : TileTapAction {
        override val storageValue = "perform_action"
    }

    data object Navigate : TileTapAction {
        override val storageValue = "navigate"
    }

    data object Url : TileTapAction {
        override val storageValue = "url"
    }

    data object Assist : TileTapAction {
        override val storageValue = "assist"
    }

    data object None : TileTapAction {
        override val storageValue = "none"
    }

    companion object {
        fun fromStorageValue(value: String): TileTapAction = when (value) {
            Toggle.storageValue -> Toggle
            MoreInfo.storageValue -> MoreInfo
            PerformAction.storageValue, "custom" -> PerformAction
            Navigate.storageValue -> Navigate
            Url.storageValue -> Url
            Assist.storageValue -> Assist
            None.storageValue -> None
            else -> Automatic
        }
    }
}
