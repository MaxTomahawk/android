package io.homeassistant.companion.android.settings.qs

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.isUsableInTile
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileType
import io.homeassistant.companion.android.database.qs.getHighestInUse
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.database.qs.numberedId
import io.homeassistant.companion.android.database.qs.tapActionType
import io.homeassistant.companion.android.database.qs.type
import io.homeassistant.companion.android.qs.Tile1Service
import io.homeassistant.companion.android.settings.qs.ManageTilesState.Companion.changeServer
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TILE_ICON_SIZE_DP = 48

@HiltViewModel
internal class ManageTilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    private val tileDao: TileDao,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ManageTilesState(
            showSubtitle = SdkVersion.isAtLeast(Build.VERSION_CODES.Q),
        ),
    )
    val state: StateFlow<ManageTilesState> = _state.asStateFlow()

    private val _tileInfoSnackbar = MutableSharedFlow<Int>(replay = 1)
    val tileInfoSnackbar = _tileInfoSnackbar.asSharedFlow()

    private var loadEntitiesJob: Job? = null

    init {
        // Initialize fields based on the tile_1 TileEntity
        savedStateHandle.get<String>("id")?.let { id ->
            selectTile(TileId(id))
            viewModelScope.launch {
                _tileInfoSnackbar.emit(commonR.string.tile_data_missing)
            }
        } ?: run {
            selectTile()
        }

        viewModelScope.launch {
            val loadedServers = serverManager.servers()
            _state.update {
                it.copy(
                    serversDropdownItems = loadedServers.map { server ->
                        HADropdownItem(key = server.id, label = server.friendlyName)
                    },
                )
            }
        }

        viewModelScope.launch {
            tileDao.getAllFlow().collect { tiles ->
                val labels = tiles.filter { it.label.isNotBlank() }.associate { TileId(it.tileId) to it.label }
                val slotItems = tileSlots.map { TileSlotItem(tileSlot = it, label = labels[it.id]) }
                _state.update { it.copy(tileSlotItems = slotItems) }
            }
        }
    }

    fun selectTile(id: TileId? = null) {
        viewModelScope.launch {
            val tile = tileSlots.find { it.id == id } ?: tileSlots.first()
            val entity = tileDao.get(tile.id.value)
            val setupEntity = entity?.takeIf { it.isSetup }
            val serverId = if (entity?.serverId == null || entity.serverId == 0) {
                serverManager.getServer()?.id ?: 0
            } else {
                entity.serverId
            }
            _state.update {
                it.copy(
                    selectedTileId = tile.id,
                    selectedServerId = serverId,
                    selectedShouldVibrate = entity?.shouldVibrate ?: false,
                    tileAuthRequired = entity?.authRequired ?: false,
                    tileLabel = setupEntity?.label.orEmpty(),
                    tileSubtitle = setupEntity?.subtitle.orEmpty(),
                    selectedEntityId = setupEntity?.entityId?.takeIf { it.isNotBlank() },
                    selectedStateEntityId = setupEntity?.stateEntityId?.takeIf { it.isNotBlank() },
                    selectedTileType = setupEntity?.type ?: TileType.Basic,
                    selectedTapAction = setupEntity?.tapActionType ?: TileTapAction.Automatic,
                    tileStateTemplate = setupEntity?.stateTemplate.orEmpty(),
                    tileStateDescriptionTemplate = setupEntity?.stateDescriptionTemplate.orEmpty(),
                    tileIconTemplate = setupEntity?.iconTemplate.orEmpty(),
                    tileContentDescriptionTemplate = setupEntity?.contentDescriptionTemplate.orEmpty(),
                    actionDomain = setupEntity?.actionDomain.orEmpty(),
                    actionName = setupEntity?.actionName.orEmpty(),
                    actionDataTemplate = setupEntity?.actionDataTemplate.orEmpty(),
                    customIcon = setupEntity?.iconName?.let { name -> Mdi.fromHaName(name) },
                    submitButtonLabel = if (!SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU) ||
                        entity?.added == true
                    ) {
                        commonR.string.tile_save
                    } else {
                        commonR.string.tile_add
                    },
                )
            }
            loadEntities(serverId)
        }
    }

    fun selectServerId(serverId: Int) {
        if (serverId == _state.value.selectedServerId) return
        viewModelScope.launch {
            _state.update { it.changeServer(serverId = serverId) }
            loadEntities(serverId)
        }
    }

    fun selectEntityId(entityId: String?) {
        _state.update { it.copy(selectedEntityId = entityId) }
    }

    fun selectStateEntityId(entityId: String?) {
        _state.update { it.copy(selectedStateEntityId = entityId) }
    }

    fun selectTapAction(action: TileTapAction) {
        _state.update { it.copy(selectedTapAction = action) }
    }

    fun selectTileType(tileType: TileType) {
        _state.update { it.copy(selectedTileType = tileType) }
        loadEntities(_state.value.selectedServerId)
    }

    /** Sets the custom icon of the tile, or clears it so the icon of the selected entity is used instead. */
    fun selectIcon(icon: MdiIcon?) {
        _state.update { it.copy(customIcon = icon) }
    }

    fun addTile(context: Context) {
        val context = context.applicationContext
        viewModelScope.launch {
            val current = _state.value
            val existing = tileDao.get(current.selectedTileId.value)
            val tileData = current.toTileEntity(existing)
            val insertedId = tileDao.add(tileData)

            val highestInUse = tileDao.getHighestInUse()?.numberedId ?: 0
            updateActiveTileServices(highestInUse, context)

            if (SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU) && existing?.added != true) {
                requestAddTileToSystem(context, tileData.copy(id = insertedId.toInt()), current.selectedIcon)
            } else {
                _tileInfoSnackbar.emit(commonR.string.tile_updated)
            }
        }
    }

    fun setTileLabel(value: String) = _state.update { it.copy(tileLabel = value) }

    fun setTileSubtitle(value: String) = _state.update { it.copy(tileSubtitle = value) }

    fun setTileStateTemplate(value: String) = _state.update { it.copy(tileStateTemplate = value) }

    fun setTileStateDescriptionTemplate(value: String) = _state.update { it.copy(tileStateDescriptionTemplate = value) }

    fun setTileIconTemplate(value: String) = _state.update { it.copy(tileIconTemplate = value) }

    fun setTileContentDescriptionTemplate(value: String) =
        _state.update { it.copy(tileContentDescriptionTemplate = value) }

    fun setActionDomain(value: String) = _state.update { it.copy(actionDomain = value) }

    fun setActionName(value: String) = _state.update { it.copy(actionName = value) }

    fun setActionDataTemplate(value: String) = _state.update { it.copy(actionDataTemplate = value) }

    fun setShouldVibrate(value: Boolean) = _state.update { it.copy(selectedShouldVibrate = value) }

    fun setAuthRequired(value: Boolean) = _state.update { it.copy(tileAuthRequired = value) }

    private fun loadEntities(serverId: Int) {
        loadEntitiesJob?.cancel()
        loadEntitiesJob = viewModelScope.launch {
            val filter = if (_state.value.selectedTileType == TileType.Basic) {
                { entity: Entity -> entity.isUsableInTile() }
            } else {
                { _: Entity -> true }
            }
            entitiesForDisplayManager.snapshotInContext(serverId, filter).collect { state ->
                _state.update { it.copy(entityDisplayState = state) }
            }
        }
    }

    /** Asks the system to add [tileData] to the quick settings panel; the result is handled by [onSystemTileAddResult]. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTileToSystem(context: Context, tileData: TileEntity, icon: MdiIcon?) {
        val service = tileSlots.find { it.id.value == tileData.tileId }?.serviceClass
            ?: Tile1Service::class.java
        val tileIcon = icon?.let {
            Icon.createWithBitmap(it.toBitmap(context, TILE_ICON_SIZE_DP, Color.WHITE))
        } ?: Icon.createWithResource(context, commonR.drawable.ic_stat_ic_notification)

        context.getSystemService<StatusBarManager>()?.requestAddTileService(
            ComponentName(context, service),
            tileData.label,
            tileIcon,
            Executors.newSingleThreadExecutor(),
        ) { result -> onSystemTileAddResult(result, tileData) }
    }

    private fun onSystemTileAddResult(result: Int, tileData: TileEntity) {
        viewModelScope.launch {
            Timber.d("Adding quick settings tile, system returned: $result")
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                _tileInfoSnackbar.emit(commonR.string.tile_added)
                tileDao.add(tileData.copy(added = true))
                _state.update { it.copy(submitButtonLabel = commonR.string.tile_save) }
            } else { // Silently ignore error, database was still updated
                _tileInfoSnackbar.emit(commonR.string.tile_updated)
            }
        }
    }

    /** Snapshot of the state as a [TileEntity], keeping the database id and added flag of [existing]. */
    private fun ManageTilesState.toTileEntity(existing: TileEntity?) = TileEntity(
        id = existing?.id ?: 0,
        tileId = selectedTileId.value,
        serverId = selectedServerId,
        added = existing?.added ?: false,
        iconName = customIcon?.mdiName,
        entityId = selectedEntityId.orEmpty(),
        label = tileLabel,
        subtitle = tileSubtitle.ifBlank { null },
        shouldVibrate = selectedShouldVibrate,
        authRequired = tileAuthRequired,
        tileType = selectedTileType.storageValue,
        stateEntityId = selectedStateEntityId,
        stateTemplate = tileStateTemplate.ifBlank { null },
        stateDescriptionTemplate = tileStateDescriptionTemplate.ifBlank { null },
        iconTemplate = tileIconTemplate.ifBlank { null },
        contentDescriptionTemplate = tileContentDescriptionTemplate.ifBlank { null },
        tapAction = selectedTapAction.storageValue,
        actionDomain = actionDomain.ifBlank { null },
        actionName = actionName.ifBlank { null },
        actionDataTemplate = actionDataTemplate.ifBlank { null },
    )
}
