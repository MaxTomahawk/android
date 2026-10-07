package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADetails
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.ActionFields
import io.homeassistant.companion.android.database.qs.TileControlDialogMode
import io.homeassistant.companion.android.database.qs.TileControlItem
import io.homeassistant.companion.android.database.qs.TileControlType
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileTextPart
import io.homeassistant.companion.android.database.qs.TileTextSource
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class AdvancedTileCallbacks(
    val addLabelPart: () -> Unit,
    val addSubtitlePart: () -> Unit,
    val removeLabelPart: (Int) -> Unit,
    val removeSubtitlePart: (Int) -> Unit,
    val updateLabelPart: (Int, TileTextPart) -> Unit,
    val updateSubtitlePart: (Int, TileTextPart) -> Unit,
    val moveLabelPart: (Int, Int) -> Unit,
    val moveSubtitlePart: (Int, Int) -> Unit,
    val addIconRule: () -> Unit,
    val removeIconRule: (Int) -> Unit,
    val updateIconRuleState: (Int, String) -> Unit,
    val showIconRulePicker: (Int) -> Unit,
    val addActiveState: () -> Unit,
    val removeActiveState: (Int) -> Unit,
    val updateActiveState: (Int, String) -> Unit,
    val selectTapAction: (TileTapAction) -> Unit,
    val selectHoldAction: (TileTapAction) -> Unit,
    val selectTapPerformAction: (String) -> Unit,
    val selectHoldPerformAction: (String) -> Unit,
    val setTapActionField: (String, String) -> Unit,
    val setHoldActionField: (String, String) -> Unit,
    val setTapTargetEntity: (String?) -> Unit,
    val setTapTargetDevice: (String?) -> Unit,
    val setTapTargetArea: (String?) -> Unit,
    val setHoldTargetEntity: (String?) -> Unit,
    val setHoldTargetDevice: (String?) -> Unit,
    val setHoldTargetArea: (String?) -> Unit,
    val setTapNavigationPath: (String) -> Unit,
    val setTapUrl: (String) -> Unit,
    val setHoldNavigationPath: (String) -> Unit,
    val setHoldUrl: (String) -> Unit,
    val setControlDialogMode: (TileControlDialogMode) -> Unit,
    val addControlDialogItem: () -> Unit,
    val removeControlDialogItem: (Int) -> Unit,
    val moveControlDialogItem: (Int, Int) -> Unit,
    val updateControlDialogItem: (Int, TileControlItem) -> Unit,
)

internal val NoopAdvancedTileCallbacks = AdvancedTileCallbacks(
    addLabelPart = {},
    addSubtitlePart = {},
    removeLabelPart = {},
    removeSubtitlePart = {},
    updateLabelPart = { _, _ -> },
    updateSubtitlePart = { _, _ -> },
    moveLabelPart = { _, _ -> },
    moveSubtitlePart = { _, _ -> },
    addIconRule = {},
    removeIconRule = {},
    updateIconRuleState = { _, _ -> },
    showIconRulePicker = {},
    addActiveState = {},
    removeActiveState = {},
    updateActiveState = { _, _ -> },
    selectTapAction = {},
    selectHoldAction = {},
    selectTapPerformAction = {},
    selectHoldPerformAction = {},
    setTapActionField = { _, _ -> },
    setHoldActionField = { _, _ -> },
    setTapTargetEntity = {},
    setTapTargetDevice = {},
    setTapTargetArea = {},
    setHoldTargetEntity = {},
    setHoldTargetDevice = {},
    setHoldTargetArea = {},
    setTapNavigationPath = {},
    setTapUrl = {},
    setHoldNavigationPath = {},
    setHoldUrl = {},
    setControlDialogMode = {},
    addControlDialogItem = {},
    removeControlDialogItem = {},
    moveControlDialogItem = { _, _ -> },
    updateControlDialogItem = { _, _ -> },
)

@Composable
internal fun AdvancedVisualTileEditor(
    state: ManageTilesState,
    callbacks: AdvancedTileCallbacks,
    showVisualContent: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4)) {
        if (showVisualContent) {
            TextPartsEditor(
                title = stringResource(commonR.string.tile_label_content),
                parts = state.labelParts,
                attributes = state.entityAttributes,
                onAdd = callbacks.addLabelPart,
                onRemove = callbacks.removeLabelPart,
                onUpdate = callbacks.updateLabelPart,
                onMove = callbacks.moveLabelPart,
            )
            if (state.showSubtitle) {
                TextPartsEditor(
                    title = stringResource(commonR.string.tile_subtitle_content),
                    parts = state.subtitleParts,
                    attributes = state.entityAttributes,
                    onAdd = callbacks.addSubtitlePart,
                    onRemove = callbacks.removeSubtitlePart,
                    onUpdate = callbacks.updateSubtitlePart,
                    onMove = callbacks.moveSubtitlePart,
                )
            }

            HADetails(
                title = stringResource(commonR.string.tile_advanced_appearance),
                defaultExpanded = state.activeStates.isNotEmpty() || state.iconRules.isNotEmpty(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
                    SectionTitle(stringResource(commonR.string.tile_active_states))
                    SupportingText(stringResource(commonR.string.tile_active_states_hint))
                    state.activeStates.forEachIndexed { index, activeState ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            HATextField(
                                value = activeState,
                                onValueChange = { callbacks.updateActiveState(index, it) },
                                label = { Text(stringResource(commonR.string.tile_state_value)) },
                                modifier = Modifier.weight(1f),
                            )
                            HAPlainButton(
                                text = stringResource(commonR.string.tile_remove_content_part),
                                onClick = { callbacks.removeActiveState(index) },
                            )
                        }
                    }
                    HAPlainButton(
                        text = stringResource(commonR.string.tile_add_active_state),
                        onClick = callbacks.addActiveState,
                    )

                    SectionTitle(stringResource(commonR.string.tile_state_icons))
                    state.iconRules.forEachIndexed { index, rule ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            HATextField(
                                value = rule.state,
                                onValueChange = { callbacks.updateIconRuleState(index, it) },
                                label = { Text(stringResource(commonR.string.tile_state_value)) },
                                modifier = Modifier.weight(1f),
                            )
                            HAPlainButton(
                                text = rule.iconName.ifBlank { stringResource(commonR.string.tile_icon) },
                                onClick = { callbacks.showIconRulePicker(index) },
                            )
                            HAPlainButton(
                                text = stringResource(commonR.string.tile_remove_content_part),
                                onClick = { callbacks.removeIconRule(index) },
                            )
                        }
                    }
                    HAPlainButton(
                        text = stringResource(commonR.string.tile_add_state_icon),
                        onClick = callbacks.addIconRule,
                    )
                }
            }
        }

        TileActionEditor(
            title = stringResource(commonR.string.tile_tap_action),
            actionType = state.selectedTapAction,
            actions = state.availableActions,
            selectedDomain = state.actionDomain,
            selectedAction = state.actionName,
            fieldValues = state.tapActionFieldValues,
            targetEntityId = state.tapTargetEntityId,
            targetDeviceId = state.tapTargetDeviceId,
            targetAreaId = state.tapTargetAreaId,
            navigationPath = state.tapNavigationPath,
            url = state.tapUrl,
            state = state,
            onActionType = callbacks.selectTapAction,
            onPerformAction = callbacks.selectTapPerformAction,
            onField = callbacks.setTapActionField,
            onTargetEntity = callbacks.setTapTargetEntity,
            onTargetDevice = callbacks.setTapTargetDevice,
            onTargetArea = callbacks.setTapTargetArea,
            onNavigationPath = callbacks.setTapNavigationPath,
            onUrl = callbacks.setTapUrl,
        )
        if (state.selectedTapAction == TileTapAction.Controls || state.selectedHoldAction == TileTapAction.Controls) {
            HASettingsCard {
                ControlDialogEditor(state = state, callbacks = callbacks)
            }
        }

        TileActionEditor(
            title = stringResource(commonR.string.tile_hold_action),
            actionType = state.selectedHoldAction,
            actions = state.availableActions,
            selectedDomain = state.holdActionDomain,
            selectedAction = state.holdActionName,
            fieldValues = state.holdActionFieldValues,
            targetEntityId = state.holdTargetEntityId,
            targetDeviceId = state.holdTargetDeviceId,
            targetAreaId = state.holdTargetAreaId,
            navigationPath = state.holdNavigationPath,
            url = state.holdUrl,
            state = state,
            onActionType = callbacks.selectHoldAction,
            onPerformAction = callbacks.selectHoldPerformAction,
            onField = callbacks.setHoldActionField,
            onTargetEntity = callbacks.setHoldTargetEntity,
            onTargetDevice = callbacks.setHoldTargetDevice,
            onTargetArea = callbacks.setHoldTargetArea,
            onNavigationPath = callbacks.setHoldNavigationPath,
            onUrl = callbacks.setHoldUrl,
        )
    }
}

@Composable
private fun TextPartsEditor(
    title: String,
    parts: List<TileTextPart>,
    attributes: List<String>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onUpdate: (Int, TileTextPart) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    if (parts.isEmpty()) {
        HAPlainButton(text = title, onClick = onAdd)
        return
    }

    SectionTitle(title)
    val sources = textSourceItems()
    val attributeItems = attributes.map { HADropdownItem(it, it) }
    parts.forEachIndexed { index, part ->
        Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2), modifier = Modifier.fillMaxWidth()) {
            HADropdownMenu(
                items = sources,
                selectedKey = part.sourceType,
                onItemSelected = { source ->
                    onUpdate(
                        index,
                        when (source) {
                            TileTextSource.FIXED -> TileTextPart.fixed(part.value.orEmpty())
                            TileTextSource.NAME -> TileTextPart.name()
                            TileTextSource.STATE -> TileTextPart.state()
                            TileTextSource.ATTRIBUTE -> TileTextPart.attribute()
                        },
                    )
                },
                label = stringResource(commonR.string.tile_content_part, index + 1),
                modifier = Modifier.fillMaxWidth(),
            )
            when (part.sourceType) {
                TileTextSource.FIXED -> HATextField(
                    value = part.value.orEmpty(),
                    onValueChange = { onUpdate(index, part.copy(value = it)) },
                    label = { Text(stringResource(commonR.string.tile_fixed_text)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TileTextSource.ATTRIBUTE -> HADropdownMenu(
                    items = attributeItems,
                    selectedKey = part.value,
                    onItemSelected = { onUpdate(index, part.copy(value = it)) },
                    label = stringResource(commonR.string.tile_attribute),
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                if (index > 0) {
                    HAPlainButton(
                        text = stringResource(commonR.string.tile_move_content_up),
                        onClick = { onMove(index, -1) },
                    )
                }
                if (index < parts.lastIndex) {
                    HAPlainButton(
                        text = stringResource(commonR.string.tile_move_content_down),
                        onClick = { onMove(index, 1) },
                    )
                }
                HAPlainButton(
                    text = stringResource(commonR.string.tile_remove_content_part),
                    onClick = { onRemove(index) },
                )
            }
        }
    }
    HAPlainButton(text = stringResource(commonR.string.tile_add_content_part), onClick = onAdd)
}

@Composable
private fun ControlDialogEditor(state: ManageTilesState, callbacks: AdvancedTileCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3)) {
        SectionTitle(stringResource(commonR.string.tile_control_dialog))
        HADropdownMenu(
            items = listOf(
                HADropdownItem(
                    TileControlDialogMode.AUTOMATIC,
                    stringResource(commonR.string.tile_control_dialog_automatic),
                ),
                HADropdownItem(
                    TileControlDialogMode.CUSTOM,
                    stringResource(commonR.string.tile_control_dialog_custom),
                ),
            ),
            selectedKey = state.controlDialogConfig.mode,
            onItemSelected = callbacks.setControlDialogMode,
            label = stringResource(commonR.string.tile_control_dialog_mode),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.controlDialogConfig.mode == TileControlDialogMode.AUTOMATIC) {
            SupportingText(stringResource(commonR.string.tile_control_dialog_automatic_hint))
        } else {
            val types = listOf(
                HADropdownItem(TileControlType.ENTITY_STATE, stringResource(commonR.string.tile_control_entity_state)),
                HADropdownItem(TileControlType.TOGGLE, stringResource(commonR.string.tile_control_toggle)),
                HADropdownItem(TileControlType.SLIDER, stringResource(commonR.string.tile_control_slider)),
                HADropdownItem(TileControlType.COLOR, stringResource(commonR.string.tile_control_color)),
                HADropdownItem(
                    TileControlType.COLOR_TEMPERATURE,
                    stringResource(commonR.string.tile_control_color_temperature),
                ),
                HADropdownItem(TileControlType.SELECT, stringResource(commonR.string.tile_control_select)),
                HADropdownItem(TileControlType.ACTION, stringResource(commonR.string.tile_control_action)),
                HADropdownItem(
                    TileControlType.GROUP_MEMBERS,
                    stringResource(commonR.string.tile_control_group_members),
                ),
            )
            state.controlDialogConfig.controls.forEachIndexed { index, item ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HADropdownMenu(
                        items = types,
                        selectedKey = item.type,
                        onItemSelected = {
                            callbacks.updateControlDialogItem(index, item.copy(type = it))
                        },
                        label = stringResource(commonR.string.tile_control_item, index + 1),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    EntityPicker(
                        displayState = state.entityDisplayState,
                        selectedEntityId = item.entityId ?: state.selectedEntityId,
                        onSelectionChanged = {
                            callbacks.updateControlDialogItem(index, item.copy(entityId = it))
                        },
                        addButtonText = stringResource(commonR.string.tile_control_entity),
                    )
                    HATextField(
                        value = item.label.orEmpty(),
                        onValueChange = { callbacks.updateControlDialogItem(index, item.copy(label = it)) },
                        label = { Text(stringResource(commonR.string.tile_control_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (item.type == TileControlType.ENTITY_STATE ||
                        item.type == TileControlType.SLIDER ||
                        item.type == TileControlType.SELECT
                    ) {
                        HATextField(
                            value = item.attribute.orEmpty(),
                            onValueChange = { callbacks.updateControlDialogItem(index, item.copy(attribute = it)) },
                            label = { Text(stringResource(commonR.string.tile_control_attribute)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (item.type == TileControlType.SLIDER ||
                        item.type == TileControlType.SELECT ||
                        item.type == TileControlType.ACTION
                    ) {
                        val actionItems = state.availableActions.map { action ->
                            HADropdownItem(
                                "${action.domain}.${action.action}",
                                action.actionData.name ?: "${action.domain}.${action.action}",
                            )
                        }
                        val selectedAction = listOfNotNull(item.actionDomain, item.actionName)
                            .takeIf { it.size == 2 }
                            ?.joinToString(".")
                        HADropdownMenu(
                            items = actionItems,
                            selectedKey = selectedAction,
                            onItemSelected = { key ->
                                val parts = key.split(".", limit = 2)
                                callbacks.updateControlDialogItem(
                                    index,
                                    item.copy(
                                        actionDomain = parts.getOrNull(0),
                                        actionName = parts.getOrNull(1),
                                        actionField = null,
                                        actionData = emptyMap(),
                                    ),
                                )
                            },
                            label = stringResource(commonR.string.tile_action_select),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val action = state.availableActions.firstOrNull {
                            it.domain == item.actionDomain && it.action == item.actionName
                        }
                        if (action != null) {
                            val actionFields = flattenActionFields(action.actionData.fields)
                            if (item.type == TileControlType.SLIDER || item.type == TileControlType.SELECT) {
                                HADropdownMenu(
                                    items = actionFields.keys.map { HADropdownItem(it, it) },
                                    selectedKey = item.actionField,
                                    onItemSelected = { fieldKey ->
                                        val spec = actionFields[fieldKey]?.numberSelectorSpec()
                                        callbacks.updateControlDialogItem(
                                            index,
                                            item.copy(
                                                actionField = fieldKey,
                                                min = spec?.min ?: item.min,
                                                max = spec?.max ?: item.max,
                                                step = spec?.step ?: item.step,
                                                unit = spec?.unit ?: item.unit,
                                            ),
                                        )
                                    },
                                    label = stringResource(commonR.string.tile_control_action_field),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            ControlDialogActionDataEditor(
                                state = state,
                                index = index,
                                item = item,
                                fields = actionFields.filterKeys { it != item.actionField },
                                callbacks = callbacks,
                            )
                        }
                    }

                    if (item.type == TileControlType.SLIDER) {
                        Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                            HATextField(
                                value = item.min?.toString().orEmpty(),
                                onValueChange = {
                                    callbacks.updateControlDialogItem(index, item.copy(min = it.toFloatOrNull()))
                                },
                                label = { Text(stringResource(commonR.string.tile_control_min)) },
                                modifier = Modifier.weight(1f),
                            )
                            HATextField(
                                value = item.max?.toString().orEmpty(),
                                onValueChange = {
                                    callbacks.updateControlDialogItem(index, item.copy(max = it.toFloatOrNull()))
                                },
                                label = { Text(stringResource(commonR.string.tile_control_max)) },
                                modifier = Modifier.weight(1f),
                            )
                            HATextField(
                                value = item.step?.toString().orEmpty(),
                                onValueChange = {
                                    callbacks.updateControlDialogItem(index, item.copy(step = it.toFloatOrNull()))
                                },
                                label = { Text(stringResource(commonR.string.tile_control_step)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        HATextField(
                            value = item.unit.orEmpty(),
                            onValueChange = { callbacks.updateControlDialogItem(index, item.copy(unit = it)) },
                            label = { Text(stringResource(commonR.string.tile_control_unit)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                        if (index > 0) {
                            HAPlainButton(
                                text = stringResource(commonR.string.tile_move_content_up),
                                onClick = { callbacks.moveControlDialogItem(index, -1) },
                            )
                        }
                        if (index < state.controlDialogConfig.controls.lastIndex) {
                            HAPlainButton(
                                text = stringResource(commonR.string.tile_move_content_down),
                                onClick = { callbacks.moveControlDialogItem(index, 1) },
                            )
                        }
                        HAPlainButton(
                            text = stringResource(commonR.string.tile_remove_content_part),
                            onClick = { callbacks.removeControlDialogItem(index) },
                        )
                    }
                }
            }
            HAPlainButton(
                text = stringResource(commonR.string.tile_control_add),
                onClick = callbacks.addControlDialogItem,
            )
        }
    }
}

@Composable
private fun ControlDialogActionDataEditor(
    state: ManageTilesState,
    index: Int,
    item: TileControlItem,
    fields: Map<String, ActionFields>,
    callbacks: AdvancedTileCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
        fields.forEach { (fieldKey, field) ->
            val label = field.displayName(fieldKey)
            val update: (String) -> Unit = { value ->
                callbacks.updateControlDialogItem(
                    index,
                    item.copy(
                        actionData = item.actionData.toMutableMap().apply {
                            if (value.isBlank()) remove(fieldKey) else put(fieldKey, value)
                        },
                    ),
                )
            }
            when (field.selectorType()) {
                "entity" -> EntityPicker(
                    displayState = state.entityDisplayState,
                    selectedEntityId = item.actionData[fieldKey],
                    onSelectionChanged = { update(it.orEmpty()) },
                    addButtonText = label,
                )
                "boolean" -> HADropdownMenu(
                    items = listOf(
                        HADropdownItem("true", "On"),
                        HADropdownItem("false", "Off"),
                    ),
                    selectedKey = item.actionData[fieldKey],
                    onItemSelected = update,
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                )
                "select" -> HADropdownMenu(
                    items = field.selectOptions().map { (value, optionLabel) -> HADropdownItem(value, optionLabel) },
                    selectedKey = item.actionData[fieldKey],
                    onItemSelected = update,
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                )
                "device" -> HADropdownMenu(
                    items = state.availableDevices.map { HADropdownItem(it.id, it.name) },
                    selectedKey = item.actionData[fieldKey],
                    onItemSelected = update,
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                )
                "area" -> HADropdownMenu(
                    items = state.availableAreas.map { HADropdownItem(it.id, it.name) },
                    selectedKey = item.actionData[fieldKey],
                    onItemSelected = update,
                    label = label,
                    modifier = Modifier.fillMaxWidth(),
                )
                "number", "color_temp" -> {
                    val spec = field.numberSelectorSpec()
                    if (spec != null) {
                        val value = item.actionData[fieldKey]?.toFloatOrNull()?.coerceIn(spec.min, spec.max) ?: spec.min
                        Column {
                            Text(
                                text = label + ": " + formatSelectorNumber(value) + spec.unit.orEmpty(),
                                style = HATextStyle.UserInput.copy(
                                    color = LocalHAColorScheme.current.colorTextPrimary,
                                ),
                            )
                            Slider(
                                value = value,
                                onValueChange = { update(formatSelectorNumber(it)) },
                                valueRange = spec.min..spec.max,
                                colors = editorSliderColors(),
                            )
                        }
                    } else {
                        HATextField(
                            value = item.actionData[fieldKey].orEmpty(),
                            onValueChange = update,
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                else -> HATextField(
                    value = item.actionData[fieldKey].orEmpty(),
                    onValueChange = update,
                    label = { Text(label) },
                    supportingText = field.description?.let { description -> { Text(description) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TileActionEditor(
    title: String,
    actionType: TileTapAction,
    actions: List<Action>,
    selectedDomain: String,
    selectedAction: String,
    fieldValues: Map<String, String>,
    targetEntityId: String?,
    targetDeviceId: String?,
    targetAreaId: String?,
    navigationPath: String,
    url: String,
    state: ManageTilesState,
    onActionType: (TileTapAction) -> Unit,
    onPerformAction: (String) -> Unit,
    onField: (String, String) -> Unit,
    onTargetEntity: (String?) -> Unit,
    onTargetDevice: (String?) -> Unit,
    onTargetArea: (String?) -> Unit,
    onNavigationPath: (String) -> Unit,
    onUrl: (String) -> Unit,
) {
    val actionTypes: List<HADropdownItem<TileTapAction>> = listOf(
        HADropdownItem(TileTapAction.Automatic, stringResource(commonR.string.tile_action_automatic)),
        HADropdownItem(TileTapAction.Toggle, stringResource(commonR.string.tile_action_toggle)),
        HADropdownItem(TileTapAction.MoreInfo, stringResource(commonR.string.tile_action_more_info)),
        HADropdownItem(TileTapAction.Controls, stringResource(commonR.string.tile_action_controls)),
        HADropdownItem(TileTapAction.PerformAction, stringResource(commonR.string.tile_action_custom)),
        HADropdownItem(TileTapAction.Navigate, stringResource(commonR.string.tile_action_navigate)),
        HADropdownItem(TileTapAction.Url, stringResource(commonR.string.tile_action_url)),
        HADropdownItem(TileTapAction.Assist, stringResource(commonR.string.tile_action_assist)),
        HADropdownItem(TileTapAction.None, stringResource(commonR.string.tile_action_none)),
    )
    HADropdownMenu(
        items = actionTypes,
        selectedKey = actionType,
        onItemSelected = onActionType,
        label = title,
        modifier = Modifier.fillMaxWidth(),
    )

    when (actionType) {
        TileTapAction.PerformAction -> {
            val items = actions.map { action ->
                HADropdownItem(
                    "${action.domain}.${action.action}",
                    action.actionData.name ?: "${action.domain}.${action.action}",
                )
            }
            val selectedKey = listOf(selectedDomain, selectedAction).takeIf {
                it.all(String::isNotBlank)
            }?.joinToString(".")
            HADropdownMenu(
                items = items,
                selectedKey = selectedKey,
                onItemSelected = onPerformAction,
                label = stringResource(commonR.string.tile_action_select),
                modifier = Modifier.fillMaxWidth(),
            )
            val selected = actions.firstOrNull { it.domain == selectedDomain && it.action == selectedAction }
            val targetTypes = selected?.actionData?.target.targetSelectorTypes()
            if ("entity" in targetTypes) {
                EntityPicker(
                    displayState = state.entityDisplayState,
                    selectedEntityId = targetEntityId,
                    onSelectionChanged = onTargetEntity,
                    addButtonText = stringResource(commonR.string.tile_action_target_entity),
                )
            }
            if ("device" in targetTypes) {
                HADropdownMenu(
                    items = state.availableDevices.map { HADropdownItem(it.id, it.name) },
                    selectedKey = targetDeviceId,
                    onItemSelected = { onTargetDevice(it) },
                    label = stringResource(commonR.string.tile_action_target_device),
                    placeholder = stringResource(commonR.string.select),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if ("area" in targetTypes) {
                HADropdownMenu(
                    items = state.availableAreas.map { HADropdownItem(it.id, it.name) },
                    selectedKey = targetAreaId,
                    onItemSelected = { onTargetArea(it) },
                    label = stringResource(commonR.string.tile_action_target_area),
                    placeholder = stringResource(commonR.string.select),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            flattenActionFields(selected?.actionData?.fields.orEmpty()).forEach { (fieldKey, field) ->
                val label = field.displayName(fieldKey)
                when (field.selectorType()) {
                    "entity" -> EntityPicker(
                        displayState = state.entityDisplayState,
                        selectedEntityId = fieldValues[fieldKey],
                        onSelectionChanged = { onField(fieldKey, it.orEmpty()) },
                        addButtonText = label,
                    )
                    "boolean" -> HADropdownMenu(
                        items = listOf(
                            HADropdownItem("true", "On"),
                            HADropdownItem("false", "Off"),
                        ),
                        selectedKey = fieldValues[fieldKey],
                        onItemSelected = { onField(fieldKey, it) },
                        label = label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "select" -> HADropdownMenu(
                        items = field.selectOptions().map { (value, optionLabel) ->
                            HADropdownItem(value, optionLabel)
                        },
                        selectedKey = fieldValues[fieldKey],
                        onItemSelected = { onField(fieldKey, it) },
                        label = label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "number", "color_temp" -> {
                        val spec = field.numberSelectorSpec()
                        if (spec != null) {
                            val value = fieldValues[fieldKey]?.toFloatOrNull()?.coerceIn(spec.min, spec.max)
                                ?: spec.min
                            Column {
                                Text(
                                    text = label + ": " + formatSelectorNumber(value) + spec.unit.orEmpty(),
                                    style = HATextStyle.UserInput.copy(
                                        color = LocalHAColorScheme.current.colorTextPrimary,
                                    ),
                                )
                                Slider(
                                    value = value,
                                    onValueChange = { onField(fieldKey, formatSelectorNumber(it)) },
                                    valueRange = spec.min..spec.max,
                                    colors = editorSliderColors(),
                                )
                            }
                        } else {
                            HATextField(
                                value = fieldValues[fieldKey].orEmpty(),
                                onValueChange = { onField(fieldKey, it) },
                                label = { Text(label) },
                                supportingText = field.description?.let { description -> { Text(description) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    "device" -> HADropdownMenu(
                        items = state.availableDevices.map { HADropdownItem(it.id, it.name) },
                        selectedKey = fieldValues[fieldKey],
                        onItemSelected = { onField(fieldKey, it) },
                        label = label,
                        placeholder = stringResource(commonR.string.select),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    "area" -> HADropdownMenu(
                        items = state.availableAreas.map { HADropdownItem(it.id, it.name) },
                        selectedKey = fieldValues[fieldKey],
                        onItemSelected = { onField(fieldKey, it) },
                        label = label,
                        placeholder = stringResource(commonR.string.select),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> {
                        val values = field.values
                        if (!values.isNullOrEmpty()) {
                            HADropdownMenu(
                                items = values.map { HADropdownItem(it, it) },
                                selectedKey = fieldValues[fieldKey],
                                onItemSelected = { onField(fieldKey, it) },
                                label = label,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            HATextField(
                                value = fieldValues[fieldKey].orEmpty(),
                                onValueChange = { onField(fieldKey, it) },
                                label = { Text(label) },
                                supportingText = field.description?.let { description -> { Text(description) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        TileTapAction.Navigate -> HATextField(
            value = navigationPath,
            onValueChange = onNavigationPath,
            label = { Text(stringResource(commonR.string.tile_action_path)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TileTapAction.Url -> HATextField(
            value = url,
            onValueChange = onUrl,
            label = { Text(stringResource(commonR.string.tile_action_url_value)) },
            modifier = Modifier.fillMaxWidth(),
        )
        else -> Unit
    }
}

private fun ActionFields.displayName(fallback: String): String = (name ?: fallback) + if (required == true) " *" else ""

private val allTargetSelectorTypes = setOf("entity", "device", "area")

private fun Any?.targetSelectorTypes(): Set<String> = when (this) {
    false, null -> emptySet()
    is Map<*, *> -> keys.mapNotNull { it as? String }.filterTo(mutableSetOf()) { it in allTargetSelectorTypes }
        .ifEmpty { allTargetSelectorTypes }
    else -> allTargetSelectorTypes
}

@Composable
private fun editorSliderColors() = SliderDefaults.colors(
    thumbColor = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
    activeTrackColor = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
    inactiveTrackColor = LocalHAColorScheme.current.colorFillNeutralNormalResting,
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = HATextStyle.UserInput.copy(
            color = LocalHAColorScheme.current.colorTextPrimary,
        ),
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        style = HATextStyle.BodyMedium.copy(
            color = LocalHAColorScheme.current.colorTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
        ),
    )
}

private fun ActionFields.selectorType(): String? = selector?.keys?.firstOrNull()

private data class NumberSelectorSpec(
    val min: Float,
    val max: Float,
    val step: Float? = null,
    val unit: String? = null,
)

private fun ActionFields.numberSelectorSpec(): NumberSelectorSpec? {
    val type = selectorType() ?: return null
    val config = selector?.get(type) as? JsonObject ?: return null
    val defaultRange = if (type == "color_temp") 2000f to 6500f else null
    val min = (config["min"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: defaultRange?.first ?: return null
    val max = (config["max"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: defaultRange?.second ?: return null
    val step = (config["step"] as? JsonPrimitive)?.content?.toFloatOrNull()
    val unit = ((config["unit_of_measurement"] ?: config["unit"]) as? JsonPrimitive)?.content
    return NumberSelectorSpec(min = min, max = max, step = step, unit = unit)
}

private fun flattenActionFields(fields: Map<String, ActionFields>): Map<String, ActionFields> = buildMap {
    fields.forEach { (key, field) ->
        val nested = field.fields
        if (nested.isNullOrEmpty()) {
            put(key, field)
        } else {
            putAll(flattenActionFields(nested))
        }
    }
}

private fun formatSelectorNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()

private fun ActionFields.selectOptions(): List<Pair<String, String>> {
    val select = selector?.get("select") as? JsonObject
    val selectorOptions = select?.get("options") as? JsonArray
    if (selectorOptions != null) {
        return selectorOptions.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.content to element.content
                is JsonObject -> {
                    val value = (element["value"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                    value to ((element["label"] as? JsonPrimitive)?.content ?: value)
                }
                else -> null
            }
        }
    }
    return values.orEmpty().map { it to it }
}

@Composable
private fun textSourceItems(): List<HADropdownItem<TileTextSource>> = listOf(
    HADropdownItem(TileTextSource.NAME, stringResource(commonR.string.tile_text_source_name)),
    HADropdownItem(TileTextSource.STATE, stringResource(commonR.string.tile_text_source_state)),
    HADropdownItem(TileTextSource.ATTRIBUTE, stringResource(commonR.string.tile_text_source_attribute)),
    HADropdownItem(TileTextSource.FIXED, stringResource(commonR.string.tile_text_source_fixed)),
)
