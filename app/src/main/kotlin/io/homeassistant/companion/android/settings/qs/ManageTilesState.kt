package io.homeassistant.companion.android.settings.qs

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.github.timoptr.mdiicons.MdiIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.qs.TileIconRule
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileTextPart
import io.homeassistant.companion.android.database.qs.TileTextSource
import io.homeassistant.companion.android.database.qs.TileType

/** A tile slot with the label of its configured tile, ready to be displayed in the slot picker. */
internal data class ActionTargetOption(val id: String, val name: String)

internal data class TileSlotItem(val id: TileId, @StringRes val nameRes: Int, val label: String?) {
    constructor(tileSlot: TileSlot, label: String? = null) : this(
        id = tileSlot.id,
        nameRes = tileSlot.nameRes,
        label = label,
    )
}

@Stable
internal data class ManageTilesState(
    val selectedTileId: TileId = tileSlots.first().id,
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val entityDisplayState: EntityDisplayState<EntityDisplayWithContext> = EntityDisplayState.Loading,
    val customIcon: MdiIcon? = null,
    val selectedEntityId: String? = null,
    val selectedStateEntityId: String? = null,
    val selectedTileType: TileType = TileType.Entity,
    val editorMode: TileEditorMode = TileEditorMode.VISUAL,
    val yamlConfig: String = "",
    val yamlError: String? = null,
    val selectedTapAction: TileTapAction = TileTapAction.Automatic,
    val selectedHoldAction: TileTapAction = TileTapAction.MoreInfo,
    val availableActions: List<Action> = emptyList(),
    val tapActionFieldValues: Map<String, String> = emptyMap(),
    val holdActionFieldValues: Map<String, String> = emptyMap(),
    val availableAreas: List<ActionTargetOption> = emptyList(),
    val availableDevices: List<ActionTargetOption> = emptyList(),
    val tapTargetEntityId: String? = null,
    val tapTargetDeviceId: String? = null,
    val tapTargetAreaId: String? = null,
    val holdTargetEntityId: String? = null,
    val holdTargetDeviceId: String? = null,
    val holdTargetAreaId: String? = null,
    val tapNavigationPath: String = "",
    val tapUrl: String = "",
    val holdNavigationPath: String = "",
    val holdUrl: String = "",
    val labelSource: TileTextSource = TileTextSource.FIXED,
    val labelAttribute: String? = null,
    val subtitleSource: TileTextSource = TileTextSource.FIXED,
    val subtitleAttribute: String? = null,
    val entityAttributes: List<String> = emptyList(),
    val entityAttributeValues: Map<String, String> = emptyMap(),
    val labelParts: List<TileTextPart> = emptyList(),
    val subtitleParts: List<TileTextPart> = emptyList(),
    val iconRules: List<TileIconRule> = emptyList(),
    val activeStates: List<String> = emptyList(),
    val tileLabel: String = "",
    val tileSubtitle: String = "",
    val tileStateTemplate: String = "",
    val tileStateDescriptionTemplate: String = "",
    val tileIconTemplate: String = "",
    val tileContentDescriptionTemplate: String = "",
    val actionDomain: String = "",
    val actionName: String = "",
    val actionDataTemplate: String = "",
    val holdActionDomain: String = "",
    val holdActionName: String = "",
    val holdActionData: String = "",
    val submitButtonLabel: Int = commonR.string.tile_save,
    val selectedShouldVibrate: Boolean = false,
    val tileAuthRequired: Boolean = false,
    val showSubtitle: Boolean = false,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val tileSlotItems: List<TileSlotItem> = tileSlots.map(::TileSlotItem),
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { server -> server.key == selectedServerId }

    /** Icon shown for the tile: the user-selected [customIcon], or the icon of the selected entity once loaded. */
    val selectedIcon = customIcon
        ?: selectedEntityId?.let { (entityDisplayState as? EntityDisplayState.Loaded)?.entity(it)?.icon }

    val showResetIcon = customIcon != null && !selectedEntityId.isNullOrBlank()

    private val selectedEntityExists =
        selectedEntityId == null ||
            (entityDisplayState as? EntityDisplayState.Loaded)?.entity(selectedEntityId) != null

    private val selectedStateEntityExists =
        selectedStateEntityId == null ||
            (entityDisplayState as? EntityDisplayState.Loaded)?.entity(selectedStateEntityId) != null

    private fun actionValid(
        action: TileTapAction,
        domain: String,
        name: String,
        navigationPath: String,
        url: String,
        fieldValues: Map<String, String>,
    ) = when (action) {
        TileTapAction.Automatic, TileTapAction.Toggle, TileTapAction.MoreInfo -> selectedEntityId != null
        TileTapAction.PerformAction ->
            domain.isNotBlank() &&
                name.isNotBlank() &&
                requiredActionFieldsPresent(domain, name, fieldValues)
        TileTapAction.Navigate -> navigationPath.isNotBlank()
        TileTapAction.Url -> url.isNotBlank()
        TileTapAction.Assist, TileTapAction.None -> true
    }

    private fun requiredActionFieldsPresent(domain: String, name: String, fieldValues: Map<String, String>): Boolean {
        val fields = availableActions
            .firstOrNull { it.domain == domain && it.action == name }
            ?.actionData
            ?.fields
            .orEmpty()
        return fields.all { (key, field) -> field.required != true || !fieldValues[key].isNullOrBlank() }
    }

    private val tapActionValid = actionValid(
        selectedTapAction,
        actionDomain,
        actionName,
        tapNavigationPath,
        tapUrl,
        tapActionFieldValues,
    )

    private val holdActionValid = actionValid(
        selectedHoldAction,
        holdActionDomain,
        holdActionName,
        holdNavigationPath,
        holdUrl,
        holdActionFieldValues,
    )

    private val labelValid = if (labelParts.isNotEmpty()) {
        labelParts.all { it.sourceType != TileTextSource.ATTRIBUTE || !it.value.isNullOrBlank() }
    } else {
        selectedTileType != TileType.Entity ||
            labelSource != TileTextSource.ATTRIBUTE ||
            labelAttribute != null
    }

    private val subtitleValid = if (subtitleParts.isNotEmpty()) {
        subtitleParts.all { it.sourceType != TileTextSource.ATTRIBUTE || !it.value.isNullOrBlank() }
    } else {
        selectedTileType != TileType.Entity ||
            subtitleSource != TileTextSource.ATTRIBUTE ||
            subtitleAttribute != null
    }

    private val requiredLabelPresent = when {
        labelParts.isNotEmpty() -> labelParts.any { part ->
            part.sourceType != TileTextSource.FIXED || !part.value.isNullOrBlank()
        }
        selectedTileType == TileType.Entity && labelSource != TileTextSource.FIXED -> true
        else -> tileLabel.isNotBlank()
    }

    val submitEnabled = yamlError == null &&
        requiredLabelPresent &&
        serversDropdownItems.any { it.key == selectedServerId } &&
        labelValid &&
        subtitleValid &&
        selectedEntityExists &&
        selectedStateEntityExists &&
        tapActionValid &&
        holdActionValid &&
        when (selectedTileType) {
            TileType.Basic, TileType.Entity -> selectedEntityId != null
            TileType.Template ->
                selectedEntityId != null ||
                    tileStateTemplate.isNotBlank() ||
                    selectedTapAction == TileTapAction.PerformAction ||
                    selectedTapAction == TileTapAction.Navigate ||
                    selectedTapAction == TileTapAction.Url ||
                    selectedTapAction == TileTapAction.Assist ||
                    selectedTapAction == TileTapAction.None
        }

    companion object {
        fun ManageTilesState.changeServer(serverId: Int): ManageTilesState = copy(
            selectedServerId = serverId,
            selectedEntityId = null,
            selectedStateEntityId = null,
            entityAttributes = emptyList(),
            entityAttributeValues = emptyMap(),
            availableAreas = emptyList(),
            availableDevices = emptyList(),
            tapTargetEntityId = null,
            tapTargetDeviceId = null,
            tapTargetAreaId = null,
            holdTargetEntityId = null,
            holdTargetDeviceId = null,
            holdTargetAreaId = null,
            entityDisplayState = EntityDisplayState.Loading,
        )
    }
}
