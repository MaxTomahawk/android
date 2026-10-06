package io.homeassistant.companion.android.settings.qs

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.github.timoptr.mdiicons.MdiIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileType

/** A tile slot with the label of its configured tile, ready to be displayed in the slot picker. */
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
    val selectedTileType: TileType = TileType.Basic,
    val selectedTapAction: TileTapAction = TileTapAction.Automatic,
    val tileLabel: String = "",
    val tileSubtitle: String = "",
    val tileStateTemplate: String = "",
    val tileStateDescriptionTemplate: String = "",
    val tileIconTemplate: String = "",
    val tileContentDescriptionTemplate: String = "",
    val actionDomain: String = "",
    val actionName: String = "",
    val actionDataTemplate: String = "",
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

    private val actionValid = when (selectedTapAction) {
        TileTapAction.Automatic, TileTapAction.MoreInfo -> selectedEntityId != null
        TileTapAction.Custom -> actionDomain.isNotBlank() && actionName.isNotBlank()
        TileTapAction.None -> true
    }

    val submitEnabled = tileLabel.isNotBlank() &&
        serversDropdownItems.any { it.key == selectedServerId } &&
        selectedEntityExists &&
        selectedStateEntityExists &&
        actionValid &&
        when (selectedTileType) {
            TileType.Basic, TileType.Entity -> selectedEntityId != null
            TileType.Template ->
                selectedEntityId != null ||
                    tileStateTemplate.isNotBlank() ||
                    selectedTapAction == TileTapAction.Custom ||
                    selectedTapAction == TileTapAction.None
        }

    companion object {
        fun ManageTilesState.changeServer(serverId: Int): ManageTilesState = copy(
            selectedServerId = serverId,
            selectedEntityId = null,
            selectedStateEntityId = null,
            entityDisplayState = EntityDisplayState.Loading,
        )
    }
}
