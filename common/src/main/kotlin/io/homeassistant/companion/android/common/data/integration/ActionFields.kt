package io.homeassistant.companion.android.common.data.integration

import io.homeassistant.companion.android.common.util.AnySerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ActionFields(
    val name: String? = null,
    val description: String? = null,
    @Serializable(with = AnySerializer::class)
    val example: Any? = null,
    val values: List<String>? = null,
    val required: Boolean? = null,
    val selector: JsonObject? = null,
    val filter: JsonObject? = null,
    val collapsed: Boolean? = null,
    val fields: Map<String, ActionFields>? = null,
)
