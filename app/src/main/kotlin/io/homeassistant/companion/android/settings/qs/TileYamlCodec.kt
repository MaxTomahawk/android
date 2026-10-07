package io.homeassistant.companion.android.settings.qs

import io.github.timoptr.mdiicons.Mdi
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileTextPart
import io.homeassistant.companion.android.database.qs.TileTextSource
import io.homeassistant.companion.android.database.qs.TileType
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

internal object TileYamlCodec {

    private val loader = Yaml(SafeConstructor(LoaderOptions()))
    private val dumper = Yaml(
        DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            width = 100
        },
    )

    fun encode(state: ManageTilesState): String {
        val root = linkedMapOf<String, Any?>(
            "tile_type" to state.selectedTileType.storageValue,
            "entity" to state.selectedEntityId,
            "state_entity" to state.selectedStateEntityId,
            "label" to encodeParts(
                state.labelParts.ifEmpty {
                    listOf(legacyPart(state.labelSource, state.tileLabel, state.labelAttribute))
                },
            ),
            "subtitle" to encodeParts(
                state.subtitleParts.ifEmpty {
                    listOf(legacyPart(state.subtitleSource, state.tileSubtitle, state.subtitleAttribute))
                },
            ),
            "icon" to state.customIcon?.mdiName,
            "icon_rules" to state.iconRules.map { linkedMapOf("state" to it.state, "icon" to it.iconName) },
            "active_states" to state.activeStates.filter(String::isNotBlank),
            "tap_action" to encodeAction(
                state.selectedTapAction,
                state.actionDomain,
                state.actionName,
                state.tapActionFieldValues,
                state.tapTargetEntityId,
                state.tapTargetDeviceId,
                state.tapTargetAreaId,
                state.tapNavigationPath,
                state.tapUrl,
                state.actionDataTemplate,
            ),
            "hold_action" to encodeAction(
                state.selectedHoldAction,
                state.holdActionDomain,
                state.holdActionName,
                state.holdActionFieldValues,
                state.holdTargetEntityId,
                state.holdTargetDeviceId,
                state.holdTargetAreaId,
                state.holdNavigationPath,
                state.holdUrl,
                state.holdActionData,
            ),
            "templates" to linkedMapOf(
                "state" to state.tileStateTemplate.takeIf(String::isNotBlank),
                "state_description" to state.tileStateDescriptionTemplate.takeIf(String::isNotBlank),
                "icon" to state.tileIconTemplate.takeIf(String::isNotBlank),
                "content_description" to state.tileContentDescriptionTemplate.takeIf(String::isNotBlank),
            ).filterValues { it != null },
            "vibrate" to state.selectedShouldVibrate,
            "require_unlock" to state.tileAuthRequired,
        ).filterValues { value ->
            value != null &&
                value != "" &&
                value != emptyList<Any>() &&
                value != emptyMap<Any, Any>()
        }
        return dumper.dump(root)
    }

    fun decode(yaml: String, base: ManageTilesState): ManageTilesState {
        val loaded = loader.load<Any?>(yaml)
        require(loaded is Map<*, *>) { "Tile configuration must be a YAML mapping" }
        val root = loaded.asStringMap()
        val labelParts = decodeParts(root["label"])
        val subtitleParts = decodeParts(root["subtitle"])
        val tap = decodeAction(root["tap_action"], TileTapAction.Automatic)
        val hold = decodeAction(root["hold_action"], TileTapAction.MoreInfo)
        val templates = root["templates"].asStringMap()
        val iconRules = root["icon_rules"].asList().mapNotNull { item ->
            val map = item.asStringMap()
            val state = map["state"]?.toString().orEmpty()
            val icon = map["icon"]?.toString().orEmpty()
            if (state.isBlank() &&
                icon.isBlank()
            ) {
                null
            } else {
                io.homeassistant.companion.android.database.qs.TileIconRule(state, icon)
            }
        }
        val activeStates = root["active_states"].asList().mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }

        val firstLabel = labelParts.singleOrNull()
        val firstSubtitle = subtitleParts.singleOrNull()
        return base.copy(
            selectedTileType = root["tile_type"]?.toString()?.let(TileType::fromStorageValue)
                ?: base.selectedTileType,
            selectedEntityId = root["entity"]?.toString()?.takeIf(String::isNotBlank),
            selectedStateEntityId = root["state_entity"]?.toString()?.takeIf(String::isNotBlank),
            labelParts = labelParts,
            subtitleParts = subtitleParts,
            labelSource = firstLabel?.sourceType ?: base.labelSource,
            labelAttribute = firstLabel?.takeIf { it.sourceType == TileTextSource.ATTRIBUTE }?.value,
            subtitleSource = firstSubtitle?.sourceType ?: base.subtitleSource,
            subtitleAttribute = firstSubtitle?.takeIf { it.sourceType == TileTextSource.ATTRIBUTE }?.value,
            tileLabel = firstLabel?.takeIf { it.sourceType == TileTextSource.FIXED }?.value.orEmpty(),
            tileSubtitle = firstSubtitle?.takeIf { it.sourceType == TileTextSource.FIXED }?.value.orEmpty(),
            customIcon = root["icon"]?.toString()?.takeIf(String::isNotBlank)?.let(Mdi::fromHaName),
            iconRules = iconRules,
            activeStates = activeStates,
            selectedTapAction = tap.type,
            actionDomain = tap.domain,
            actionName = tap.action,
            actionDataTemplate = tap.dataTemplate,
            tapActionFieldValues = tap.data,
            tapTargetEntityId = tap.entityId,
            tapTargetDeviceId = tap.deviceId,
            tapTargetAreaId = tap.areaId,
            tapNavigationPath = tap.path,
            tapUrl = tap.url,
            selectedHoldAction = hold.type,
            holdActionDomain = hold.domain,
            holdActionName = hold.action,
            holdActionData = hold.dataTemplate,
            holdActionFieldValues = hold.data,
            holdTargetEntityId = hold.entityId,
            holdTargetDeviceId = hold.deviceId,
            holdTargetAreaId = hold.areaId,
            holdNavigationPath = hold.path,
            holdUrl = hold.url,
            tileStateTemplate = templates["state"]?.toString().orEmpty(),
            tileStateDescriptionTemplate = templates["state_description"]?.toString().orEmpty(),
            tileIconTemplate = templates["icon"]?.toString().orEmpty(),
            tileContentDescriptionTemplate = templates["content_description"]?.toString().orEmpty(),
            selectedShouldVibrate = root["vibrate"] as? Boolean ?: false,
            tileAuthRequired = root["require_unlock"] as? Boolean ?: false,
        )
    }

    private fun legacyPart(source: TileTextSource, fixed: String, attribute: String?): TileTextPart = when (source) {
        TileTextSource.FIXED -> TileTextPart.fixed(fixed)
        TileTextSource.NAME -> TileTextPart.name()
        TileTextSource.STATE -> TileTextPart.state()
        TileTextSource.ATTRIBUTE -> TileTextPart.attribute(attribute)
    }

    private fun encodeParts(parts: List<TileTextPart>): List<Map<String, Any?>> = parts.map { part ->
        linkedMapOf<String, Any?>(
            "type" to when (part.sourceType) {
                TileTextSource.FIXED -> "text"
                TileTextSource.NAME -> "name"
                TileTextSource.STATE -> "state"
                TileTextSource.ATTRIBUTE -> "attribute"
            },
            "value" to part.value,
        ).filterValues { it != null }
    }

    private fun decodeParts(raw: Any?): List<TileTextPart> = when (raw) {
        is String -> listOf(TileTextPart.fixed(raw))
        is List<*> -> raw.mapNotNull { item ->
            val map = item.asStringMap()
            when (map["type"]?.toString()) {
                "text", "fixed" -> TileTextPart.fixed(map["value"]?.toString().orEmpty())
                "name" -> TileTextPart.name()
                "state" -> TileTextPart.state()
                "attribute" -> TileTextPart.attribute(map["value"]?.toString())
                else -> null
            }
        }
        else -> emptyList()
    }

    private fun encodeAction(
        type: TileTapAction,
        domain: String,
        action: String,
        data: Map<String, String>,
        entityId: String?,
        deviceId: String?,
        areaId: String?,
        path: String,
        url: String,
        dataTemplate: String,
    ): Map<String, Any?> = linkedMapOf<String, Any?>(
        "action" to type.storageValue,
        "perform_action" to listOf(domain, action).takeIf { it.all(String::isNotBlank) }?.joinToString("."),
        "target" to linkedMapOf(
            "entity_id" to entityId,
            "device_id" to deviceId,
            "area_id" to areaId,
        ).filterValues { !it.isNullOrBlank() },
        "data" to data.filterValues(String::isNotBlank),
        "data_template" to dataTemplate.takeIf { it.isTileTemplate() },
        "path" to path.takeIf(String::isNotBlank),
        "url" to url.takeIf(String::isNotBlank),
    ).filterValues { value ->
        value != null &&
            value != "" &&
            value != emptyMap<Any, Any>()
    }

    private fun decodeAction(raw: Any?, defaultType: TileTapAction): DecodedAction {
        val map = raw.asStringMap()
        val type = map["action"]?.toString()?.let(TileTapAction::fromStorageValue) ?: defaultType
        val perform = map["perform_action"]?.toString().orEmpty().split(".", limit = 2)
        val target = map["target"].asStringMap()
        val data = map["data"].asStringMap().mapValues { (_, value) ->
            when (value) {
                is List<*> -> value.joinToString(", ") { it.toString() }
                null -> ""
                else -> value.toString()
            }
        }
        return DecodedAction(
            type = type,
            domain = perform.getOrElse(0) { "" },
            action = perform.getOrElse(1) { "" },
            data = data,
            dataTemplate = map["data_template"]?.toString().orEmpty(),
            entityId = target["entity_id"]?.toString(),
            deviceId = target["device_id"]?.toString(),
            areaId = target["area_id"]?.toString(),
            path = map["path"]?.toString().orEmpty(),
            url = map["url"]?.toString().orEmpty(),
        )
    }

    private data class DecodedAction(
        val type: TileTapAction,
        val domain: String,
        val action: String,
        val data: Map<String, String>,
        val dataTemplate: String,
        val entityId: String?,
        val deviceId: String?,
        val areaId: String?,
        val path: String,
        val url: String,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asStringMap(): Map<String, Any?> = (this as? Map<*, *>)
        ?.entries
        ?.associate { it.key.toString() to it.value }
        .orEmpty()

    private fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()

    private fun String.isTileTemplate(): Boolean = contains("{{") || contains("{%")
}
