package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.Undo
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileTextPart
import io.homeassistant.companion.android.database.qs.TileTextSource
import io.homeassistant.companion.android.database.qs.TileType
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel
import io.homeassistant.companion.android.settings.qs.TileEditorMode
import io.homeassistant.companion.android.settings.qs.TileId
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.safeBottomWindowInsets

@Composable
internal fun ManageTilesScreen(viewModel: ManageTilesViewModel, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var iconDialogTarget by remember { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.tileInfoSnackbar.collect { resId ->
            snackbarHostState.showSnackbar(resources.getString(resId))
        }
    }

    if (iconDialogTarget != null) {
        // TODO Migrate IconDialog to Material 3 https://github.com/home-assistant/android/issues/7156
        HomeAssistantAppTheme {
            IconDialog(
                onSelect = { icon ->
                    val target = iconDialogTarget
                    if (target == -1) {
                        viewModel.selectIcon(icon)
                    } else if (target != null) {
                        viewModel.updateIconRuleIcon(target, icon.mdiName)
                    }
                    iconDialogTarget = null
                },
                onDismissRequest = { iconDialogTarget = null },
            )
        }
    }

    ManageTilesContent(
        snackbarHostState = snackbarHostState,
        state = state,
        submitEnabled = state.submitEnabled,
        onTileSelected = viewModel::selectTile,
        onTileTypeSelected = viewModel::selectTileType,
        onEditorModeSelected = viewModel::selectEditorMode,
        onYamlConfigChange = viewModel::setYamlConfig,
        onServerSelected = viewModel::selectServerId,
        onTileLabelChange = viewModel::setTileLabel,
        onTileSubtitleChange = viewModel::setTileSubtitle,
        onLabelSourceSelected = viewModel::selectLabelSource,
        onLabelAttributeSelected = viewModel::selectLabelAttribute,
        onSubtitleSourceSelected = viewModel::selectSubtitleSource,
        onSubtitleAttributeSelected = viewModel::selectSubtitleAttribute,
        onSelectionChanged = viewModel::selectEntityId,
        onStateEntityChanged = viewModel::selectStateEntityId,
        onStateTemplateChange = viewModel::setTileStateTemplate,
        onStateDescriptionTemplateChange = viewModel::setTileStateDescriptionTemplate,
        onIconTemplateChange = viewModel::setTileIconTemplate,
        onContentDescriptionTemplateChange = viewModel::setTileContentDescriptionTemplate,
        onTapActionSelected = viewModel::selectTapAction,
        onActionDomainChange = viewModel::setActionDomain,
        onActionNameChange = viewModel::setActionName,
        onActionDataTemplateChange = viewModel::setActionDataTemplate,
        onShowIconDialog = { iconDialogTarget = -1 },
        advancedCallbacks = AdvancedTileCallbacks(
            addLabelPart = viewModel::addLabelPart,
            addSubtitlePart = viewModel::addSubtitlePart,
            removeLabelPart = viewModel::removeLabelPart,
            removeSubtitlePart = viewModel::removeSubtitlePart,
            updateLabelPart = viewModel::updateLabelPart,
            updateSubtitlePart = viewModel::updateSubtitlePart,
            moveLabelPart = viewModel::moveLabelPart,
            moveSubtitlePart = viewModel::moveSubtitlePart,
            addIconRule = viewModel::addIconRule,
            removeIconRule = viewModel::removeIconRule,
            updateIconRuleState = viewModel::updateIconRuleState,
            showIconRulePicker = { iconDialogTarget = it },
            addActiveState = viewModel::addActiveState,
            removeActiveState = viewModel::removeActiveState,
            updateActiveState = viewModel::updateActiveState,
            selectTapAction = viewModel::selectTapAction,
            selectHoldAction = viewModel::selectHoldAction,
            selectTapPerformAction = viewModel::selectTapPerformAction,
            selectHoldPerformAction = viewModel::selectHoldPerformAction,
            setTapActionField = viewModel::setTapActionField,
            setHoldActionField = viewModel::setHoldActionField,
            setTapTargetEntity = viewModel::setTapTargetEntity,
            setTapTargetDevice = viewModel::setTapTargetDevice,
            setTapTargetArea = viewModel::setTapTargetArea,
            setHoldTargetEntity = viewModel::setHoldTargetEntity,
            setHoldTargetDevice = viewModel::setHoldTargetDevice,
            setHoldTargetArea = viewModel::setHoldTargetArea,
            setTapNavigationPath = viewModel::setTapNavigationPath,
            setTapUrl = viewModel::setTapUrl,
            setHoldNavigationPath = viewModel::setHoldNavigationPath,
            setHoldUrl = viewModel::setHoldUrl,
            setControlDialogMode = viewModel::setControlDialogMode,
            addControlDialogItem = viewModel::addControlDialogItem,
            removeControlDialogItem = viewModel::removeControlDialogItem,
            moveControlDialogItem = viewModel::moveControlDialogItem,
            updateControlDialogItem = viewModel::updateControlDialogItem,
        ),
        onResetIcon = { viewModel.selectIcon(null) },
        onShouldVibrateChange = viewModel::setShouldVibrate,
        onAuthRequiredChange = viewModel::setAuthRequired,
        onSubmit = { viewModel.addTile(context) },
        modifier = modifier,
    )
}

@Composable
internal fun ManageTilesContent(
    snackbarHostState: SnackbarHostState,
    state: ManageTilesState,
    submitEnabled: Boolean,
    onTileSelected: (id: TileId) -> Unit,
    onTileTypeSelected: (TileType) -> Unit,
    onEditorModeSelected: (TileEditorMode) -> Unit,
    onYamlConfigChange: (String) -> Unit,
    onServerSelected: (Int) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
    onLabelSourceSelected: (TileTextSource) -> Unit,
    onLabelAttributeSelected: (String) -> Unit,
    onSubtitleSourceSelected: (TileTextSource) -> Unit,
    onSubtitleAttributeSelected: (String) -> Unit,
    onSelectionChanged: (String?) -> Unit,
    onStateEntityChanged: (String?) -> Unit,
    onStateTemplateChange: (String) -> Unit,
    onStateDescriptionTemplateChange: (String) -> Unit,
    onIconTemplateChange: (String) -> Unit,
    onContentDescriptionTemplateChange: (String) -> Unit,
    onTapActionSelected: (TileTapAction) -> Unit,
    onActionDomainChange: (String) -> Unit,
    onActionNameChange: (String) -> Unit,
    onActionDataTemplateChange: (String) -> Unit,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    onShouldVibrateChange: (Boolean) -> Unit,
    onAuthRequiredChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    advancedCallbacks: AdvancedTileCallbacks = NoopAdvancedTileCallbacks,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
            )
        },
        contentWindowInsets = safeBottomWindowInsets(applyHorizontal = true),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(all = HADimens.SPACE4),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            TileLabelContent(
                state = state,
                onTileSelected = onTileSelected,
                onTileTypeSelected = onTileTypeSelected,
                onEditorModeSelected = onEditorModeSelected,
                onTileLabelChange = onTileLabelChange,
                onTileSubtitleChange = onTileSubtitleChange,
            )

            if (state.showServerSelector) {
                HADropdownMenu(
                    items = state.serversDropdownItems,
                    selectedKey = state.selectedServerId,
                    onItemSelected = onServerSelected,
                    label = stringResource(commonR.string.tile_server),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.editorMode == TileEditorMode.YAML) {
                HATextField(
                    value = state.yamlConfig,
                    onValueChange = onYamlConfigChange,
                    label = { Text(stringResource(commonR.string.tile_yaml_configuration)) },
                    supportingText = {
                        Text(
                            state.yamlError ?: stringResource(commonR.string.tile_yaml_configuration_hint),
                        )
                    },
                    isError = state.yamlError != null,
                    minLines = 18,
                    maxLines = 32,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TileConfigContent(
                    state = state,
                    onSelectionChanged = onSelectionChanged,
                    onLabelSourceSelected = onLabelSourceSelected,
                    onLabelAttributeSelected = onLabelAttributeSelected,
                    onSubtitleSourceSelected = onSubtitleSourceSelected,
                    onSubtitleAttributeSelected = onSubtitleAttributeSelected,
                    onTileLabelChange = onTileLabelChange,
                    onTileSubtitleChange = onTileSubtitleChange,
                    onStateEntityChanged = onStateEntityChanged,
                    onStateTemplateChange = onStateTemplateChange,
                    onStateDescriptionTemplateChange = onStateDescriptionTemplateChange,
                    onIconTemplateChange = onIconTemplateChange,
                    onContentDescriptionTemplateChange = onContentDescriptionTemplateChange,
                    onTapActionSelected = onTapActionSelected,
                    onActionDomainChange = onActionDomainChange,
                    onActionNameChange = onActionNameChange,
                    onActionDataTemplateChange = onActionDataTemplateChange,
                    onAuthRequiredChange = onAuthRequiredChange,
                    onShowIconDialog = onShowIconDialog,
                    advancedCallbacks = advancedCallbacks,
                    onResetIcon = onResetIcon,
                    onShouldVibrateChange = onShouldVibrateChange,
                )
            }

            HAFilledButton(
                text = stringResource(state.submitButtonLabel),
                onClick = onSubmit,
                enabled = submitEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ColumnScope.TileLabelContent(
    state: ManageTilesState,
    onTileSelected: (id: TileId) -> Unit,
    onTileTypeSelected: (TileType) -> Unit,
    onEditorModeSelected: (TileEditorMode) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
) {
    val res = LocalResources.current
    val tiles = remember(state.tileSlotItems) {
        state.tileSlotItems.map { slot ->
            HADropdownItem(
                key = slot.id,
                label = res.getString(
                    commonR.string.tile_slot_label,
                    res.getString(slot.nameRes),
                    slot.label ?: res.getString(commonR.string.not_set),
                ),
            )
        }
    }

    HADropdownMenu(
        items = tiles,
        selectedKey = state.selectedTileId,
        onItemSelected = onTileSelected,
        label = stringResource(commonR.string.tile_select),
        modifier = Modifier.fillMaxWidth(),
    )

    HAHorizontalDivider()

    if (state.selectedTileType != TileType.Entity) {
        val tileTypes: List<HADropdownItem<TileType>> = listOf(
            HADropdownItem(TileType.Basic, stringResource(commonR.string.tile_type_basic)),
            HADropdownItem(TileType.Entity, stringResource(commonR.string.tile_type_entity)),
            HADropdownItem(TileType.Template, stringResource(commonR.string.tile_type_template)),
        )
        HADropdownMenu(
            items = tileTypes,
            selectedKey = state.selectedTileType,
            onItemSelected = onTileTypeSelected,
            label = stringResource(commonR.string.tile_type),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val editorModes = listOf(
        HADropdownItem(TileEditorMode.VISUAL, stringResource(commonR.string.tile_editor_visual)),
        HADropdownItem(TileEditorMode.YAML, stringResource(commonR.string.tile_editor_yaml)),
    )
    HADropdownMenu(
        items = editorModes,
        selectedKey = state.editorMode,
        onItemSelected = onEditorModeSelected,
        label = stringResource(commonR.string.tile_editor_mode),
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(commonR.string.tile_required_field_hint),
        style = HATextStyle.BodyMedium,
        color = LocalHAColorScheme.current.colorTextSecondary,
    )

    if (state.editorMode == TileEditorMode.VISUAL && state.selectedTileType != TileType.Entity) {
        HATextField(
            value = state.tileLabel,
            onValueChange = onTileLabelChange,
            label = { Text(text = stringResource(commonR.string.tile_label)) },
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.showSubtitle) {
            HATextField(
                value = state.tileSubtitle,
                onValueChange = onTileSubtitleChange,
                label = { Text(text = stringResource(commonR.string.tile_subtitle)) },
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.TileConfigContent(
    state: ManageTilesState,
    onSelectionChanged: (String?) -> Unit,
    onLabelSourceSelected: (TileTextSource) -> Unit,
    onLabelAttributeSelected: (String) -> Unit,
    onSubtitleSourceSelected: (TileTextSource) -> Unit,
    onSubtitleAttributeSelected: (String) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
    onStateEntityChanged: (String?) -> Unit,
    onStateTemplateChange: (String) -> Unit,
    onStateDescriptionTemplateChange: (String) -> Unit,
    onIconTemplateChange: (String) -> Unit,
    onContentDescriptionTemplateChange: (String) -> Unit,
    onTapActionSelected: (TileTapAction) -> Unit,
    onActionDomainChange: (String) -> Unit,
    onActionNameChange: (String) -> Unit,
    onActionDataTemplateChange: (String) -> Unit,
    onShowIconDialog: () -> Unit,
    advancedCallbacks: AdvancedTileCallbacks,
    onResetIcon: () -> Unit,
    onShouldVibrateChange: (Boolean) -> Unit,
    onAuthRequiredChange: (Boolean) -> Unit,
) {
    EntityPicker(
        displayState = state.entityDisplayState,
        selectedEntityId = state.selectedEntityId,
        onSelectionChanged = onSelectionChanged,
        addButtonText = stringResource(commonR.string.tile_entity),
    )

    if (state.selectedTileType == TileType.Entity) {
        val textSources: List<HADropdownItem<TileTextSource>> = listOf(
            HADropdownItem(TileTextSource.NAME, stringResource(commonR.string.tile_text_source_name)),
            HADropdownItem(TileTextSource.STATE, stringResource(commonR.string.tile_text_source_state)),
            HADropdownItem(TileTextSource.ATTRIBUTE, stringResource(commonR.string.tile_text_source_attribute)),
            HADropdownItem(TileTextSource.FIXED, stringResource(commonR.string.tile_text_source_fixed)),
        )
        val attributes = state.entityAttributes.map { HADropdownItem(it, it) }

        if (state.labelParts.isEmpty()) {
            HADropdownMenu(
                items = textSources,
                selectedKey = state.labelSource,
                onItemSelected = onLabelSourceSelected,
                label = stringResource(commonR.string.tile_label_content),
                modifier = Modifier.fillMaxWidth(),
            )
            when (state.labelSource) {
                TileTextSource.FIXED -> HATextField(
                    value = state.tileLabel,
                    onValueChange = onTileLabelChange,
                    label = { Text(text = stringResource(commonR.string.tile_fixed_text)) },
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                TileTextSource.ATTRIBUTE -> HADropdownMenu(
                    items = attributes,
                    selectedKey = state.labelAttribute,
                    onItemSelected = onLabelAttributeSelected,
                    label = stringResource(commonR.string.tile_attribute),
                    placeholder = stringResource(commonR.string.select),
                    enabled = attributes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> Unit
            }
        }

        if (state.showSubtitle && state.subtitleParts.isEmpty()) {
            HADropdownMenu(
                items = textSources,
                selectedKey = state.subtitleSource,
                onItemSelected = onSubtitleSourceSelected,
                label = stringResource(commonR.string.tile_subtitle_content),
                modifier = Modifier.fillMaxWidth(),
            )
            when (state.subtitleSource) {
                TileTextSource.FIXED -> HATextField(
                    value = state.tileSubtitle,
                    onValueChange = onTileSubtitleChange,
                    label = { Text(text = stringResource(commonR.string.tile_fixed_text)) },
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                TileTextSource.ATTRIBUTE -> HADropdownMenu(
                    items = attributes,
                    selectedKey = state.subtitleAttribute,
                    onItemSelected = onSubtitleAttributeSelected,
                    label = stringResource(commonR.string.tile_attribute),
                    placeholder = stringResource(commonR.string.select),
                    enabled = attributes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> Unit
            }
        }
    }

    if (state.selectedTileType != TileType.Basic) {
        EntityPicker(
            displayState = state.entityDisplayState,
            selectedEntityId = state.selectedStateEntityId,
            onSelectionChanged = onStateEntityChanged,
            addButtonText = stringResource(commonR.string.tile_state_entity),
        )

        val templateHint = @Composable {
            Text(text = stringResource(commonR.string.tile_template_hint))
        }
        if (state.selectedTileType == TileType.Template) {
            HATextField(
                value = state.tileStateTemplate,
                onValueChange = onStateTemplateChange,
                label = { Text(text = stringResource(commonR.string.tile_state_template)) },
                supportingText = templateHint,
                maxLines = 3,
            )
            HATextField(
                value = state.tileStateDescriptionTemplate,
                onValueChange = onStateDescriptionTemplateChange,
                label = { Text(text = stringResource(commonR.string.tile_state_description_template)) },
                supportingText = templateHint,
                maxLines = 3,
            )
            HATextField(
                value = state.tileIconTemplate,
                onValueChange = onIconTemplateChange,
                label = { Text(text = stringResource(commonR.string.tile_icon_template)) },
                supportingText = templateHint,
                maxLines = 3,
            )
            HATextField(
                value = state.tileContentDescriptionTemplate,
                onValueChange = onContentDescriptionTemplateChange,
                label = { Text(text = stringResource(commonR.string.tile_content_description_template)) },
                supportingText = templateHint,
                maxLines = 3,
            )
        }

        AdvancedVisualTileEditor(
            state = state,
            callbacks = advancedCallbacks,
            showVisualContent = state.selectedTileType == TileType.Entity,
        )

        if (state.selectedTileType == TileType.Entity) {
            TileConfigurationPreview(state)
        }
    }

    TileIconRow(
        selectedIcon = state.selectedIcon,
        showResetIcon = state.showResetIcon,
        onShowIconDialog = onShowIconDialog,
        onResetIcon = onResetIcon,
    )
    if (!state.selectedEntityId.isNullOrBlank()) {
        Text(
            text = stringResource(commonR.string.tile_default_entity_icon),
            style = HATextStyle.BodyMedium,
            color = LocalHAColorScheme.current.colorTextSecondary,
        )
    }

    LabeledSwitchRow(
        label = stringResource(commonR.string.tile_vibrate),
        checked = state.selectedShouldVibrate,
        onCheckedChange = onShouldVibrateChange,
    )

    LabeledSwitchRow(
        label = stringResource(commonR.string.tile_auth_required),
        checked = state.tileAuthRequired,
        onCheckedChange = onAuthRequiredChange,
    )
}

@Composable
private fun TileConfigurationPreview(state: ManageTilesState, modifier: Modifier = Modifier) {
    val loaded = state.entityDisplayState as? EntityDisplayState.Loaded
    val entity = state.selectedEntityId?.let { loaded?.entity(it) }
    val stateEntity = (state.selectedStateEntityId ?: state.selectedEntityId)?.let { loaded?.entity(it) }
    val stateValue = stateEntity?.rawState.orEmpty()
    val label = previewText(
        parts = state.labelParts,
        source = state.labelSource,
        fixedValue = state.tileLabel,
        attribute = state.labelAttribute,
        entityName = entity?.name,
        entityState = entity?.rawState,
        attributes = state.entityAttributeValues,
    )
    val subtitle = previewText(
        parts = state.subtitleParts,
        source = state.subtitleSource,
        fixedValue = state.tileSubtitle,
        attribute = state.subtitleAttribute,
        entityName = entity?.name,
        entityState = entity?.rawState,
        attributes = state.entityAttributeValues,
    )
    val stateIcon = state.iconRules
        .firstOrNull { it.state.equals(stateValue, ignoreCase = true) }
        ?.iconName
        ?.let(Mdi::fromHaName)
    val icon = stateIcon ?: state.selectedIcon
    val colorScheme = LocalHAColorScheme.current
    val shape = RoundedCornerShape(HARadius.L)

    Column(
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(commonR.string.tile_preview),
            style = HATextStyle.BodyMedium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
            modifier = Modifier
                .fillMaxWidth()
                .border(HABorderWidth.S, colorScheme.colorBorderNeutralQuiet, shape)
                .padding(HADimens.SPACE4),
        ) {
            if (icon != null) {
                Image(
                    imageVector = icon.rememberImageVector(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorScheme.colorOnPrimaryNormal),
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.ifBlank { state.selectedEntityId.orEmpty() },
                    style = HATextStyle.BodyMedium,
                )
                if (state.showSubtitle && subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = HATextStyle.Body,
                        color = colorScheme.colorTextSecondary,
                    )
                }
                if (stateValue.isNotBlank()) {
                    Text(
                        text = stringResource(commonR.string.tile_preview_state, stateValue),
                        style = HATextStyle.Body,
                        color = colorScheme.colorTextSecondary,
                    )
                }
            }
        }
    }
}

private fun previewText(
    parts: List<TileTextPart>,
    source: TileTextSource,
    fixedValue: String,
    attribute: String?,
    entityName: String?,
    entityState: String?,
    attributes: Map<String, String>,
): String {
    fun value(type: TileTextSource, configuredValue: String?): String = when (type) {
        TileTextSource.FIXED -> configuredValue.orEmpty()
        TileTextSource.NAME -> entityName.orEmpty()
        TileTextSource.STATE -> entityState.orEmpty()
        TileTextSource.ATTRIBUTE -> configuredValue?.let(attributes::get).orEmpty()
    }

    return if (parts.isNotEmpty()) {
        parts.joinToString(separator = "") { part -> value(part.sourceType, part.value) }
    } else {
        value(source, if (source == TileTextSource.FIXED) fixedValue else attribute)
    }
}

@Composable
private fun TileIconRow(
    selectedIcon: MdiIcon?,
    showResetIcon: Boolean,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconContentDescription = stringResource(commonR.string.tile_icon)
    val colorScheme = LocalHAColorScheme.current
    val buttonShape = RoundedCornerShape(HARadius.Pill)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(commonR.string.tile_icon),
            style = HATextStyle.Body,
            modifier = Modifier.padding(end = HADimens.SPACE2),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showResetIcon) {
            HAIconButton(
                icon = Mdi.Undo.rememberImageVector(autoMirror = true),
                onClick = onResetIcon,
                contentDescription = stringResource(commonR.string.undo),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .border(HABorderWidth.S, colorScheme.colorBorderNeutralQuiet, buttonShape)
                .clip(buttonShape)
                .clickable(onClick = onShowIconDialog, role = Role.Button)
                .padding(horizontal = HADimens.SPACE4)
                .heightIn(min = 40.dp)
                .semantics { contentDescription = iconContentDescription },
        ) {
            if (selectedIcon != null) {
                Image(
                    imageVector = selectedIcon.rememberImageVector(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorScheme.colorOnPrimaryNormal),
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = stringResource(commonR.string.select),
                    style = HATextStyle.Button,
                    color = colorScheme.colorOnPrimaryNormal,
                )
            }
        }
    }
}

@Composable
private fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = HATextStyle.Body,
        )
        HASwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview(name = "Add tile", showBackground = true)
@Composable
private fun ManageTilesPreview() {
    HAThemeForPreview {
        ManageTilesContent(
            snackbarHostState = remember { SnackbarHostState() },
            state = previewState,
            submitEnabled = false,
            onTileSelected = {},
            onTileTypeSelected = {},
            onEditorModeSelected = {},
            onYamlConfigChange = {},
            onServerSelected = {},
            onTileLabelChange = {},
            onTileSubtitleChange = {},
            onLabelSourceSelected = {},
            onLabelAttributeSelected = {},
            onSubtitleSourceSelected = {},
            onSubtitleAttributeSelected = {},
            onSelectionChanged = {},
            onStateEntityChanged = {},
            onStateTemplateChange = {},
            onStateDescriptionTemplateChange = {},
            onIconTemplateChange = {},
            onContentDescriptionTemplateChange = {},
            onTapActionSelected = {},
            onActionDomainChange = {},
            onActionNameChange = {},
            onActionDataTemplateChange = {},
            onShowIconDialog = {},
            onResetIcon = {},
            onShouldVibrateChange = {},
            onAuthRequiredChange = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Update tile", showBackground = true)
@Composable
private fun ManageTilesUpdatePreview() {
    HAThemeForPreview {
        ManageTilesContent(
            snackbarHostState = remember { SnackbarHostState() },
            submitEnabled = false,
            state = previewState.copy(
                selectedTileId = TileId("tile_2"),
                tileLabel = "Living room",
                tileSubtitle = "Lights",
                selectedEntityId = "light.living_room",
                submitButtonLabel = commonR.string.tile_save,
            ),
            onTileSelected = {},
            onTileTypeSelected = {},
            onEditorModeSelected = {},
            onYamlConfigChange = {},
            onServerSelected = {},
            onTileLabelChange = {},
            onTileSubtitleChange = {},
            onLabelSourceSelected = {},
            onLabelAttributeSelected = {},
            onSubtitleSourceSelected = {},
            onSubtitleAttributeSelected = {},
            onSelectionChanged = {},
            onStateEntityChanged = {},
            onStateTemplateChange = {},
            onStateDescriptionTemplateChange = {},
            onIconTemplateChange = {},
            onContentDescriptionTemplateChange = {},
            onTapActionSelected = {},
            onActionDomainChange = {},
            onActionNameChange = {},
            onActionDataTemplateChange = {},
            onShowIconDialog = {},
            onResetIcon = {},
            onShouldVibrateChange = {},
            onAuthRequiredChange = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Labeled switch row", showBackground = true)
@Composable
private fun LabeledSwitchRowPreview() {
    HAThemeForPreview {
        LabeledSwitchRow(
            label = "Vibrate when selected",
            checked = true,
            onCheckedChange = {},
        )
    }
}

private val previewState = ManageTilesState(
    selectedTileId = TileId("tile_1"),
    selectedServerId = 0,
    tileLabel = "",
    tileSubtitle = "",
    selectedEntityId = "",
    submitButtonLabel = commonR.string.tile_add,
    showSubtitle = true,
)
