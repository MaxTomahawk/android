package io.homeassistant.companion.android.database.qs

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "qs_tiles")
data class TileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "tile_id")
    val tileId: String,
    @ColumnInfo(name = "added", defaultValue = "1")
    val added: Boolean,
    @ColumnInfo(name = "server_id", defaultValue = "0")
    val serverId: Int,
    /** Icon name, such as "mdi:account-alert" */
    @ColumnInfo(name = "icon_name")
    val iconName: String?,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "subtitle")
    val subtitle: String?,
    @ColumnInfo(name = "should_vibrate", defaultValue = "0")
    val shouldVibrate: Boolean,
    @ColumnInfo(name = "auth_required", defaultValue = "0")
    val authRequired: Boolean,
    @ColumnInfo(name = "tile_type", defaultValue = "'basic'")
    val tileType: String = TileType.Basic.storageValue,
    @ColumnInfo(name = "state_entity_id")
    val stateEntityId: String? = null,
    @ColumnInfo(name = "state_template")
    val stateTemplate: String? = null,
    @ColumnInfo(name = "state_description_template")
    val stateDescriptionTemplate: String? = null,
    @ColumnInfo(name = "icon_template")
    val iconTemplate: String? = null,
    @ColumnInfo(name = "content_description_template")
    val contentDescriptionTemplate: String? = null,
    @ColumnInfo(name = "tap_action", defaultValue = "'automatic'")
    val tapAction: String = TileTapAction.Automatic.storageValue,
    @ColumnInfo(name = "action_domain")
    val actionDomain: String? = null,
    @ColumnInfo(name = "action_name")
    val actionName: String? = null,
    @ColumnInfo(name = "action_data_template")
    val actionDataTemplate: String? = null,
    @ColumnInfo(name = "label_source", defaultValue = "'fixed'")
    val labelSource: String = TileTextSource.FIXED.storageValue,
    @ColumnInfo(name = "label_attribute")
    val labelAttribute: String? = null,
    @ColumnInfo(name = "subtitle_source", defaultValue = "'fixed'")
    val subtitleSource: String = TileTextSource.FIXED.storageValue,
    @ColumnInfo(name = "subtitle_attribute")
    val subtitleAttribute: String? = null,
    @ColumnInfo(name = "label_parts_json")
    val labelPartsJson: String? = null,
    @ColumnInfo(name = "subtitle_parts_json")
    val subtitlePartsJson: String? = null,
    @ColumnInfo(name = "icon_rules_json")
    val iconRulesJson: String? = null,
    @ColumnInfo(name = "active_states_json")
    val activeStatesJson: String? = null,
    @ColumnInfo(name = "hold_action", defaultValue = "'more_info'")
    val holdAction: String = TileTapAction.MoreInfo.storageValue,
    @ColumnInfo(name = "hold_action_domain")
    val holdActionDomain: String? = null,
    @ColumnInfo(name = "hold_action_name")
    val holdActionName: String? = null,
    @ColumnInfo(name = "hold_action_data")
    val holdActionData: String? = null,
    @ColumnInfo(name = "tap_navigation_path")
    val tapNavigationPath: String? = null,
    @ColumnInfo(name = "tap_url")
    val tapUrl: String? = null,
    @ColumnInfo(name = "hold_navigation_path")
    val holdNavigationPath: String? = null,
    @ColumnInfo(name = "hold_url")
    val holdUrl: String? = null,
)

val TileEntity.type: TileType
    get() = TileType.fromStorageValue(tileType)

val TileEntity.tapActionType: TileTapAction
    get() = TileTapAction.fromStorageValue(tapAction)

val TileEntity.holdActionType: TileTapAction
    get() = TileTapAction.fromStorageValue(holdAction)

val TileEntity.labelSourceType: TileTextSource
    get() = TileTextSource.fromStorageValue(labelSource)

val TileEntity.subtitleSourceType: TileTextSource
    get() = TileTextSource.fromStorageValue(subtitleSource)

val TileEntity.stateSourceEntityId: String
    get() = stateEntityId?.takeIf { it.isNotBlank() } ?: entityId

val TileEntity.isSetup: Boolean
    get() {
        val labelParts = decodeTileTextParts(labelPartsJson)
        val labelConfigured = when {
            labelParts.isNotEmpty() -> labelParts.any { part ->
                part.sourceType != TileTextSource.FIXED || !part.value.isNullOrBlank()
            }
            type == TileType.Entity && labelSourceType != TileTextSource.FIXED -> true
            else -> label.isNotBlank()
        }
        return labelConfigured &&
            when (type) {
                TileType.Basic, TileType.Entity -> entityId.isNotBlank()
                TileType.Template -> entityId.isNotBlank() ||
                    !stateTemplate.isNullOrBlank() ||
                    tapActionType == TileTapAction.PerformAction ||
                    tapActionType == TileTapAction.Navigate ||
                    tapActionType == TileTapAction.Url ||
                    tapActionType == TileTapAction.Assist ||
                    tapActionType == TileTapAction.None
            }
    }

val TileEntity.numberedId: Int
    get() = this.tileId.split("_")[1].toInt()
