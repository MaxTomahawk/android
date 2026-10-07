package io.homeassistant.companion.android.database.qs

import kotlinx.serialization.Serializable

@Serializable
data class TileIconRule(val state: String = "", val iconName: String = "")
