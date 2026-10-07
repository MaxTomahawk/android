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
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.isUsableInTile
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.qs.TileControlDialogMode
import io.homeassistant.companion.android.database.qs.TileControlItem
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.TileIconRule
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileTextPart
import io.homeassistant.companion.android.database.qs.TileTextSource
import io.homeassistant.companion.android.database.qs.TileType
import io.homeassistant.companion.android.database.qs.decodeStringList
import io.homeassistant.companion.android.database.qs.decodeTileControlDialogConfig
import io.homeassistant.companion.android.database.qs.decodeTileIconRules
import io.homeassistant.companion.android.database.qs.decodeTileTextParts
import io.homeassistant.companion.android.database.qs.encodeStringList
import io.homeassistant.companion.android.database.qs.encodeTileControlDialogConfig
import io.homeassistant.companion.android.database.qs.encodeTileIconRules
import io.homeassistant.companion.android.database.qs.encodeTileTextParts
import io.homeassistant.companion.android.database.qs.getHighestInUse
import io.homeassistant.companion.android.database.qs.holdActionType
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.database.qs.labelSourceType
import io.homeassistant.companion.android.database.qs.numberedId
import io.homeassistant.companion.android.database.qs.subtitleSourceType
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import timber.log.Timber

private const val TILE_ICON_SIZE_DP = 48
private val actionTargetKeys = setOf("entity_id", "device_id", "area_id")

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
            val tapFields = decodeActionFields(setupEntity?.actionDataTemplate)
            val holdFields = decodeActionFields(setupEntity?.holdActionData)
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
                    selectedTileType = setupEntity?.type ?: TileType.Entity,
                    editorMode = if (setupEntity?.type == TileType.Template) {
                        TileEditorMode.YAML
                    } else {
                        TileEditorMode.VISUAL
                    },
                    yamlConfig = "",
                    yamlError = null,
                    selectedTapAction = setupEntity?.tapActionType ?: TileTapAction.Controls,
                    selectedHoldAction = setupEntity?.holdActionType ?: TileTapAction.Automatic,
                    controlDialogConfig = decodeTileControlDialogConfig(setupEntity?.controlDialogJson),
                    labelSource = setupEntity?.labelSourceType ?: TileTextSource.NAME,
                    labelAttribute = setupEntity?.labelAttribute,
                    subtitleSource = setupEntity?.subtitleSourceType ?: TileTextSource.STATE,
                    subtitleAttribute = setupEntity?.subtitleAttribute,
                    entityAttributes = emptyList(),
                    entityAttributeValues = emptyMap(),
                    labelParts = decodeTileTextParts(setupEntity?.labelPartsJson),
                    subtitleParts = decodeTileTextParts(setupEntity?.subtitlePartsJson),
                    iconRules = decodeTileIconRules(setupEntity?.iconRulesJson),
                    activeStates = decodeStringList(setupEntity?.activeStatesJson),
                    tileStateTemplate = setupEntity?.stateTemplate.orEmpty(),
                    tileStateDescriptionTemplate = setupEntity?.stateDescriptionTemplate.orEmpty(),
                    tileIconTemplate = setupEntity?.iconTemplate.orEmpty(),
                    tileContentDescriptionTemplate = setupEntity?.contentDescriptionTemplate.orEmpty(),
                    actionDomain = setupEntity?.actionDomain.orEmpty(),
                    actionName = setupEntity?.actionName.orEmpty(),
                    actionDataTemplate = setupEntity?.actionDataTemplate.orEmpty(),
                    holdActionDomain = setupEntity?.holdActionDomain.orEmpty(),
                    holdActionName = setupEntity?.holdActionName.orEmpty(),
                    holdActionData = setupEntity?.holdActionData.orEmpty(),
                    tapActionFieldValues = tapFields.filterKeys { it !in actionTargetKeys },
                    holdActionFieldValues = holdFields.filterKeys { it !in actionTargetKeys },
                    tapTargetEntityId = tapFields["entity_id"],
                    tapTargetDeviceId = tapFields["device_id"],
                    tapTargetAreaId = tapFields["area_id"],
                    holdTargetEntityId = holdFields["entity_id"],
                    holdTargetDeviceId = holdFields["device_id"],
                    holdTargetAreaId = holdFields["area_id"],
                    tapNavigationPath = setupEntity?.tapNavigationPath.orEmpty(),
                    tapUrl = setupEntity?.tapUrl.orEmpty(),
                    holdNavigationPath = setupEntity?.holdNavigationPath.orEmpty(),
                    holdUrl = setupEntity?.holdUrl.orEmpty(),
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
            if (_state.value.editorMode == TileEditorMode.YAML) {
                _state.update { state -> state.copy(yamlConfig = TileYamlCodec.encode(state)) }
            }
            loadEntities(serverId)
            loadActions(serverId)
            loadActionTargets(serverId)
            loadSelectedEntityAttributes(serverId, setupEntity?.entityId)
        }
    }

    fun selectServerId(serverId: Int) {
        if (serverId == _state.value.selectedServerId) return
        viewModelScope.launch {
            _state.update { it.changeServer(serverId = serverId) }
            loadEntities(serverId)
            loadActions(serverId)
            loadActionTargets(serverId)
        }
    }

    fun selectEntityId(entityId: String?) {
        _state.update {
            it.copy(
                selectedEntityId = entityId,
                labelAttribute = null,
                subtitleAttribute = null,
                entityAttributes = emptyList(),
                entityAttributeValues = emptyMap(),
            )
        }
        loadSelectedEntityAttributes(_state.value.selectedServerId, entityId)
    }

    fun selectLabelSource(source: TileTextSource) {
        _state.update { it.copy(labelSource = source, labelAttribute = null) }
    }

    fun selectLabelAttribute(attribute: String) {
        _state.update { it.copy(labelAttribute = attribute) }
    }

    fun selectSubtitleSource(source: TileTextSource) {
        _state.update { it.copy(subtitleSource = source, subtitleAttribute = null) }
    }

    fun selectSubtitleAttribute(attribute: String) {
        _state.update { it.copy(subtitleAttribute = attribute) }
    }

    fun selectStateEntityId(entityId: String?) {
        _state.update { it.copy(selectedStateEntityId = entityId) }
    }

    fun selectTapAction(action: TileTapAction) {
        _state.update { it.copy(selectedTapAction = action) }
    }

    fun selectHoldAction(action: TileTapAction) {
        _state.update { it.copy(selectedHoldAction = action) }
    }

    fun setControlDialogMode(mode: TileControlDialogMode) = _state.update {
        it.copy(controlDialogConfig = it.controlDialogConfig.copy(mode = mode))
    }

    fun addControlDialogItem() = _state.update {
        it.copy(
            controlDialogConfig = it.controlDialogConfig.copy(
                mode = TileControlDialogMode.CUSTOM,
                controls = it.controlDialogConfig.controls + TileControlItem(),
            ),
        )
    }

    fun removeControlDialogItem(index: Int) = _state.update {
        it.copy(
            controlDialogConfig = it.controlDialogConfig.copy(
                controls = it.controlDialogConfig.controls.filterIndexed { i, _ -> i != index },
            ),
        )
    }

    fun moveControlDialogItem(index: Int, offset: Int) = _state.update {
        it.copy(
            controlDialogConfig = it.controlDialogConfig.copy(
                controls = it.controlDialogConfig.controls.moveItem(index, index + offset),
            ),
        )
    }

    fun updateControlDialogItem(index: Int, item: TileControlItem) = _state.update {
        it.copy(
            controlDialogConfig = it.controlDialogConfig.copy(
                controls = it.controlDialogConfig.controls.mapIndexed { i, current ->
                    if (i ==
                        index
                    ) {
                        item
                    } else {
                        current
                    }
                },
            ),
        )
    }

    fun selectTapPerformAction(actionKey: String) {
        val parts = actionKey.split(".", limit = 2)
        _state.update {
            it.copy(
                actionDomain = parts.getOrElse(0) { "" },
                actionName = parts.getOrElse(1) { "" },
                tapActionFieldValues = emptyMap(),
                tapTargetEntityId = null,
                tapTargetDeviceId = null,
                tapTargetAreaId = null,
            )
        }
    }

    fun selectHoldPerformAction(actionKey: String) {
        val parts = actionKey.split(".", limit = 2)
        _state.update {
            it.copy(
                holdActionDomain = parts.getOrElse(0) { "" },
                holdActionName = parts.getOrElse(1) { "" },
                holdActionFieldValues = emptyMap(),
                holdTargetEntityId = null,
                holdTargetDeviceId = null,
                holdTargetAreaId = null,
            )
        }
    }

    fun setTapActionField(key: String, value: String) {
        _state.update { state ->
            state.copy(
                tapActionFieldValues = state.tapActionFieldValues.toMutableMap().apply {
                    if (value.isBlank()) remove(key) else put(key, value)
                },
            )
        }
    }

    fun setHoldActionField(key: String, value: String) {
        _state.update { state ->
            state.copy(
                holdActionFieldValues = state.holdActionFieldValues.toMutableMap().apply {
                    if (value.isBlank()) remove(key) else put(key, value)
                },
            )
        }
    }

    fun setTapTargetEntity(entityId: String?) {
        _state.update { it.copy(tapTargetEntityId = entityId) }
    }

    fun setTapTargetDevice(deviceId: String?) {
        _state.update { it.copy(tapTargetDeviceId = deviceId) }
    }

    fun setTapTargetArea(areaId: String?) {
        _state.update { it.copy(tapTargetAreaId = areaId) }
    }

    fun setHoldTargetEntity(entityId: String?) {
        _state.update { it.copy(holdTargetEntityId = entityId) }
    }

    fun setHoldTargetDevice(deviceId: String?) {
        _state.update { it.copy(holdTargetDeviceId = deviceId) }
    }

    fun setHoldTargetArea(areaId: String?) {
        _state.update { it.copy(holdTargetAreaId = areaId) }
    }

    fun setTapNavigationPath(value: String) = _state.update { it.copy(tapNavigationPath = value) }

    fun setTapUrl(value: String) = _state.update { it.copy(tapUrl = value) }

    fun setHoldNavigationPath(value: String) = _state.update { it.copy(holdNavigationPath = value) }

    fun setHoldUrl(value: String) = _state.update { it.copy(holdUrl = value) }

    fun addLabelPart() = _state.update { state ->
        val initial = when (state.labelSource) {
            TileTextSource.FIXED -> TileTextPart.fixed(state.tileLabel)
            TileTextSource.NAME -> TileTextPart.name()
            TileTextSource.STATE -> TileTextPart.state()
            TileTextSource.ATTRIBUTE -> TileTextPart.attribute(state.labelAttribute)
        }
        state.copy(
            labelParts = if (state.labelParts.isEmpty()) {
                listOf(initial)
            } else {
                state.labelParts +
                    TileTextPart.fixed(" ")
            },
        )
    }

    fun addSubtitlePart() = _state.update { state ->
        val initial = when (state.subtitleSource) {
            TileTextSource.FIXED -> TileTextPart.fixed(state.tileSubtitle)
            TileTextSource.NAME -> TileTextPart.name()
            TileTextSource.STATE -> TileTextPart.state()
            TileTextSource.ATTRIBUTE -> TileTextPart.attribute(state.subtitleAttribute)
        }
        state.copy(
            subtitleParts = if (state.subtitleParts.isEmpty()) {
                listOf(initial)
            } else {
                state.subtitleParts +
                    TileTextPart.fixed(" ")
            },
        )
    }

    fun removeLabelPart(index: Int) = _state.update {
        it.copy(
            labelParts = it.labelParts.filterIndexed { i, _ ->
                i !=
                    index
            },
        )
    }

    fun removeSubtitlePart(index: Int) =
        _state.update { it.copy(subtitleParts = it.subtitleParts.filterIndexed { i, _ -> i != index }) }

    fun updateLabelPart(index: Int, part: TileTextPart) = _state.update {
        it.copy(
            labelParts = it.labelParts.mapIndexed { i, current ->
                if (i ==
                    index
                ) {
                    part
                } else {
                    current
                }
            },
        )
    }

    fun updateSubtitlePart(index: Int, part: TileTextPart) = _state.update {
        it.copy(
            subtitleParts = it.subtitleParts.mapIndexed { i, current ->
                if (i ==
                    index
                ) {
                    part
                } else {
                    current
                }
            },
        )
    }

    fun moveLabelPart(index: Int, offset: Int) = _state.update { state ->
        state.copy(labelParts = state.labelParts.moveItem(index, index + offset))
    }

    fun moveSubtitlePart(index: Int, offset: Int) = _state.update { state ->
        state.copy(subtitleParts = state.subtitleParts.moveItem(index, index + offset))
    }

    fun addIconRule() = _state.update { it.copy(iconRules = it.iconRules + TileIconRule()) }

    fun removeIconRule(index: Int) =
        _state.update { it.copy(iconRules = it.iconRules.filterIndexed { i, _ -> i != index }) }

    fun updateIconRuleState(index: Int, value: String) = _state.update { state ->
        state.copy(
            iconRules = state.iconRules.mapIndexed { i, rule ->
                if (i ==
                    index
                ) {
                    rule.copy(state = value)
                } else {
                    rule
                }
            },
        )
    }

    fun updateIconRuleIcon(index: Int, iconName: String) = _state.update { state ->
        state.copy(
            iconRules = state.iconRules.mapIndexed { i, rule ->
                if (i ==
                    index
                ) {
                    rule.copy(iconName = iconName)
                } else {
                    rule
                }
            },
        )
    }

    fun addActiveState() = _state.update { it.copy(activeStates = it.activeStates + "") }

    fun removeActiveState(index: Int) =
        _state.update { it.copy(activeStates = it.activeStates.filterIndexed { i, _ -> i != index }) }

    fun updateActiveState(index: Int, value: String) = _state.update {
        it.copy(activeStates = it.activeStates.mapIndexed { i, state -> if (i == index) value else state })
    }

    fun selectEditorMode(mode: TileEditorMode) {
        _state.update { state ->
            when (mode) {
                TileEditorMode.VISUAL -> state.copy(
                    editorMode = mode,
                    yamlConfig = TileYamlCodec.encode(state),
                    yamlError = null,
                )
                TileEditorMode.YAML -> state.copy(
                    editorMode = mode,
                    yamlConfig = TileYamlCodec.encode(state),
                    yamlError = null,
                )
            }
        }
    }

    fun setYamlConfig(value: String) {
        val current = _state.value
        val previousEntityId = current.selectedEntityId
        val parsed = runCatching { TileYamlCodec.decode(value, current) }
        _state.update { state ->
            parsed.fold(
                onSuccess = { decoded ->
                    decoded.copy(
                        editorMode = TileEditorMode.YAML,
                        yamlConfig = value,
                        yamlError = null,
                        entityDisplayState = state.entityDisplayState,
                        entityAttributes = if (decoded.selectedEntityId == state.selectedEntityId) {
                            state.entityAttributes
                        } else {
                            emptyList()
                        },
                        availableActions = state.availableActions,
                        availableAreas = state.availableAreas,
                        availableDevices = state.availableDevices,
                        serversDropdownItems = state.serversDropdownItems,
                        tileSlotItems = state.tileSlotItems,
                        submitButtonLabel = state.submitButtonLabel,
                        showSubtitle = state.showSubtitle,
                    )
                },
                onFailure = { error -> state.copy(yamlConfig = value, yamlError = error.message ?: "Invalid YAML") },
            )
        }
        val newEntityId = parsed.getOrNull()?.selectedEntityId
        if (newEntityId != previousEntityId) {
            loadSelectedEntityAttributes(_state.value.selectedServerId, newEntityId)
        }
    }

    fun selectTileType(tileType: TileType) {
        _state.update {
            if (tileType == TileType.Entity &&
                it.selectedTileType != TileType.Entity &&
                it.tileLabel.isBlank() &&
                it.tileSubtitle.isBlank()
            ) {
                it.copy(
                    selectedTileType = tileType,
                    labelSource = TileTextSource.NAME,
                    subtitleSource = TileTextSource.STATE,
                )
            } else {
                it.copy(selectedTileType = tileType)
            }
        }
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

    private fun loadSelectedEntityAttributes(serverId: Int, entityId: String?) {
        if (entityId.isNullOrBlank()) return
        viewModelScope.launch {
            val entity = runCatching {
                serverManager.integrationRepository(serverId).getEntity(entityId)
            }.getOrElse {
                Timber.w(it, "Unable to load attributes for $entityId")
                null
            }
            val attributes = entity?.attributes?.keys?.sorted().orEmpty()
            val attributeValues = entity?.attributes.orEmpty().mapValues { (_, value) -> value?.toString().orEmpty() }
            _state.update { state ->
                if (state.selectedEntityId == entityId) {
                    state.copy(
                        entityAttributes = attributes,
                        entityAttributeValues = attributeValues,
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun loadActions(serverId: Int) {
        viewModelScope.launch {
            val actions = runCatching {
                serverManager.integrationRepository(serverId).getServices().orEmpty()
                    .sortedBy { "${it.domain}.${it.action}" }
            }.getOrElse {
                Timber.w(it, "Unable to load Home Assistant actions")
                emptyList()
            }
            _state.update { state ->
                if (state.selectedServerId == serverId) state.copy(availableActions = actions) else state
            }
        }
    }

    private fun loadActionTargets(serverId: Int) {
        viewModelScope.launch {
            val registry = runCatching {
                val webSocket = serverManager.webSocketRepository(serverId)
                val areas = webSocket.getAreaRegistry().orEmpty()
                    .map { ActionTargetOption(it.areaId, it.name) }
                    .sortedBy { it.name.lowercase() }
                val devices = webSocket.getDeviceRegistry().orEmpty()
                    .map { device ->
                        ActionTargetOption(
                            device.id,
                            device.nameByUser ?: device.name ?: device.id,
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                areas to devices
            }.getOrElse {
                Timber.w(it, "Unable to load Home Assistant action targets")
                emptyList<ActionTargetOption>() to emptyList()
            }
            _state.update { state ->
                if (state.selectedServerId == serverId) {
                    state.copy(availableAreas = registry.first, availableDevices = registry.second)
                } else {
                    state
                }
            }
        }
    }

    private fun decodeActionFields(value: String?): Map<String, String> {
        if (value.isNullOrBlank() || value.contains("{{") || value.contains("{%")) return emptyMap()
        return runCatching {
            kotlinJsonMapper.decodeFromString<Map<String, Any?>>(MapAnySerializer, value)
                .mapValues { (_, fieldValue) ->
                    when (fieldValue) {
                        is List<*> -> fieldValue.joinToString(", ")
                        null -> ""
                        else -> fieldValue.toString()
                    }
                }
        }.getOrDefault(emptyMap())
    }

    private fun encodeActionFields(
        values: Map<String, String>,
        targetEntityId: String?,
        targetDeviceId: String?,
        targetAreaId: String?,
    ): String? {
        val data = values
            .filterValues { it.isNotBlank() }
            .mapValues { (_, value) -> value.toJsonValue() }
            .toMutableMap()
        targetEntityId?.takeIf { it.isNotBlank() }?.let { data["entity_id"] = it }
        targetDeviceId?.takeIf { it.isNotBlank() }?.let { data["device_id"] = it }
        targetAreaId?.takeIf { it.isNotBlank() }?.let { data["area_id"] = it }
        return data.takeIf { it.isNotEmpty() }?.let { kotlinJsonMapper.encodeToString(MapAnySerializer, it) }
    }

    private fun String.toJsonValue(): Any = trim().let { value ->
        when {
            value.equals("true", true) -> true
            value.equals("false", true) -> false
            value.toIntOrNull() != null -> value.toInt()
            value.toDoubleOrNull() != null -> value.toDouble()
            value.contains(",") -> value.split(",").map(String::trim).filter(String::isNotBlank)
            else -> value
        }
    }

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
    private fun <T> List<T>.moveItem(fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this
        return toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
    }

    private fun ManageTilesState.toTileEntity(existing: TileEntity?): TileEntity {
        val displayName = selectedEntityId?.let { entityId ->
            (entityDisplayState as? EntityDisplayState.Loaded)
                ?.entity(entityId)
                ?.name
        }
        val storedLabel = if (selectedTileType == TileType.Entity && labelSource != TileTextSource.FIXED) {
            displayName ?: selectedEntityId.orEmpty()
        } else {
            tileLabel
        }

        return TileEntity(
            id = existing?.id ?: 0,
            tileId = selectedTileId.value,
            serverId = selectedServerId,
            added = existing?.added ?: false,
            iconName = customIcon?.mdiName,
            entityId = selectedEntityId.orEmpty(),
            label = storedLabel,
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
            actionDataTemplate = encodeActionFields(
                tapActionFieldValues,
                tapTargetEntityId,
                tapTargetDeviceId,
                tapTargetAreaId,
            ) ?: actionDataTemplate.ifBlank { null },
            labelSource = labelSource.storageValue,
            labelAttribute = labelAttribute,
            subtitleSource = subtitleSource.storageValue,
            subtitleAttribute = subtitleAttribute,
            labelPartsJson = encodeTileTextParts(labelParts),
            subtitlePartsJson = encodeTileTextParts(subtitleParts),
            iconRulesJson = encodeTileIconRules(iconRules),
            activeStatesJson = encodeStringList(activeStates),
            holdAction = selectedHoldAction.storageValue,
            holdActionDomain = holdActionDomain.ifBlank { null },
            holdActionName = holdActionName.ifBlank { null },
            holdActionData = encodeActionFields(
                holdActionFieldValues,
                holdTargetEntityId,
                holdTargetDeviceId,
                holdTargetAreaId,
            ) ?: holdActionData.ifBlank { null },
            tapNavigationPath = tapNavigationPath.ifBlank { null },
            tapUrl = tapUrl.ifBlank { null },
            holdNavigationPath = holdNavigationPath.ifBlank { null },
            holdUrl = holdUrl.ifBlank { null },
            controlDialogJson = encodeTileControlDialogConfig(controlDialogConfig),
        )
    }
}
