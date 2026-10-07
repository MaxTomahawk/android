package io.homeassistant.companion.android.qs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.getColorTemperature
import io.homeassistant.companion.android.common.data.integration.supportsLightBrightness
import io.homeassistant.companion.android.common.data.integration.supportsLightColorTemperature
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.qs.TileControlDialogMode
import io.homeassistant.companion.android.database.qs.TileControlItem
import io.homeassistant.companion.android.database.qs.TileControlType
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.decodeTileControlDialogConfig
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class QuickSettingsControlDialogActivity : BaseActivity() {

    @Inject lateinit var serverManager: ServerManager

    @Inject lateinit var tileDao: TileDao

    private val entities = mutableStateMapOf<String, Entity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileId = intent.getStringExtra(EXTRA_TILE_ID) ?: run {
            finish()
            return
        }
        lifecycleScope.launch {
            val tile = tileDao.get(tileId) ?: run {
                finish()
                return@launch
            }
            val repo = serverManager.integrationRepository(tile.serverId)
            val config = decodeTileControlDialogConfig(tile.controlDialogJson)
            val primary = withContext(Dispatchers.IO) { repo.getEntity(tile.entityId) }
            if (primary == null) {
                finish()
                return@launch
            }
            entities[primary.entityId] = primary
            val configuredIds = (config.controls.mapNotNull { it.entityId } + primary.entityId).toMutableSet()
            val pendingIds = configuredIds.toMutableList()
            var pendingIndex = 0
            while (pendingIndex < pendingIds.size) {
                val id = pendingIds[pendingIndex++]
                val entity = entities[id] ?: withContext(Dispatchers.IO) { repo.getEntity(id) } ?: continue
                entities[id] = entity
                entity.groupMembers().forEach { memberId ->
                    if (configuredIds.add(memberId)) pendingIds += memberId
                }
            }

            setContent {
                HATheme {
                    Dialog(onDismissRequest = ::finish) {
                        Surface {
                            QuickSettingsControlDialog(
                                tile = tile,
                                configMode = config.mode,
                                configItems = config.controls,
                                entities = entities,
                                onCallService = { domain, service, data, targetEntity ->
                                    lifecycleScope.launch {
                                        callService(tile.serverId, domain, service, data, targetEntity)
                                    }
                                },
                                onDismiss = ::finish,
                            )
                        }
                    }
                }
            }

            repo.getEntityUpdates(configuredIds.toList())?.collect { entity ->
                entities[entity.entityId] = entity
            }
        }
    }

    private suspend fun callService(
        serverId: Int,
        domain: String,
        service: String,
        data: Map<String, Any?> = emptyMap(),
        targetEntity: String? = null,
    ) {
        try {
            withContext(Dispatchers.IO) {
                serverManager.webSocketRepository(serverId).callService(
                    domain = domain,
                    service = service,
                    serviceData = data,
                    target = targetEntity?.let { mapOf("entity_id" to it) }.orEmpty(),
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Unable to execute Quick Settings dialog action")
        }
    }

    companion object {
        private const val EXTRA_TILE_ID = "tile_id"

        fun newInstance(context: Context, tileId: String): Intent =
            Intent(context, QuickSettingsControlDialogActivity::class.java)
                .putExtra(EXTRA_TILE_ID, tileId)
    }
}

@Composable
private fun QuickSettingsControlDialog(
    tile: TileEntity,
    configMode: TileControlDialogMode,
    configItems: List<TileControlItem>,
    entities: Map<String, Entity>,
    onCallService: (String, String, Map<String, Any?>, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = entities[tile.entityId] ?: return
    val controls = if (configMode == TileControlDialogMode.AUTOMATIC) {
        automaticControls(primary)
    } else {
        configItems
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        modifier = Modifier
            .width(360.dp)
            .heightIn(max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(HADimens.SPACE4),
    ) {
        Text(text = primary.displayName())
        Text(text = primary.state)
        HorizontalDivider()
        controls.forEach { item ->
            RenderControl(
                item = item,
                fallbackEntityId = primary.entityId,
                entities = entities,
                onCallService = onCallService,
            )
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        }
    }
}

internal fun automaticControls(entity: Entity): List<TileControlItem> {
    val domain = entity.entityId.substringBefore('.')
    val controls = mutableListOf(TileControlItem(type = TileControlType.ENTITY_STATE, entityId = entity.entityId))
    when (domain) {
        "light" -> {
            controls += TileControlItem(type = TileControlType.TOGGLE, entityId = entity.entityId)
            if (entity.supportsLightBrightness()) {
                controls += TileControlItem(
                    type = TileControlType.SLIDER,
                    entityId = entity.entityId,
                    label = "Brightness",
                    attribute = "brightness",
                    actionDomain = "light",
                    actionName = "turn_on",
                    actionField = "brightness_pct",
                    min = 0f,
                    max = 100f,
                    step = 1f,
                    unit = "%",
                )
            }
            val supportedModes = (entity.attributes["supported_color_modes"] as? List<*>)?.map {
                it.toString()
            }.orEmpty()
            if (supportedModes.any { it in setOf("hs", "xy", "rgb", "rgbw", "rgbww") }) {
                controls += TileControlItem(type = TileControlType.COLOR, entityId = entity.entityId)
            }
            if (entity.supportsLightColorTemperature()) {
                controls += TileControlItem(type = TileControlType.COLOR_TEMPERATURE, entityId = entity.entityId)
            }
            if ((entity.attributes["effect_list"] as? List<*>)?.isNotEmpty() == true) {
                controls += TileControlItem(
                    type = TileControlType.SELECT,
                    entityId = entity.entityId,
                    label = "Effect",
                    attribute = "effect",
                    actionDomain = "light",
                    actionName = "turn_on",
                    actionField = "effect",
                )
            }
        }
        "fan" -> controls += listOf(
            TileControlItem(type = TileControlType.TOGGLE, entityId = entity.entityId),
            TileControlItem(
                type = TileControlType.SLIDER,
                entityId = entity.entityId,
                label = "Speed",
                attribute = "percentage",
                actionDomain = "fan",
                actionName = "set_percentage",
                actionField = "percentage",
                min = 0f,
                max = 100f,
                step = 1f,
                unit = "%",
            ),
        )
        "cover" -> controls += TileControlItem(
            type = TileControlType.SLIDER,
            entityId = entity.entityId,
            label = "Position",
            attribute = "current_position",
            actionDomain = "cover",
            actionName = "set_cover_position",
            actionField = "position",
            min = 0f,
            max = 100f,
            step = 1f,
            unit = "%",
        )
        "media_player" -> controls += TileControlItem(
            type = TileControlType.SLIDER,
            entityId = entity.entityId,
            label = "Volume",
            attribute = "volume_level",
            actionDomain = "media_player",
            actionName = "volume_set",
            actionField = "volume_level",
            min = 0f,
            max = 1f,
            step = 0.01f,
        )
        "number", "input_number" -> controls += TileControlItem(
            type = TileControlType.SLIDER,
            entityId = entity.entityId,
            label = entity.displayName(),
            actionDomain = domain,
            actionName = "set_value",
            actionField = "value",
            min = (entity.attributes["min"] as? Number)?.toFloat(),
            max = (entity.attributes["max"] as? Number)?.toFloat(),
            step = (entity.attributes["step"] as? Number)?.toFloat(),
            unit = entity.attributes["unit_of_measurement"]?.toString(),
        )
        "climate" -> controls += TileControlItem(
            type = TileControlType.SLIDER,
            entityId = entity.entityId,
            label = "Temperature",
            attribute = "temperature",
            actionDomain = "climate",
            actionName = "set_temperature",
            actionField = "temperature",
            min = (entity.attributes["min_temp"] as? Number)?.toFloat(),
            max = (entity.attributes["max_temp"] as? Number)?.toFloat(),
            step = (entity.attributes["target_temp_step"] as? Number)?.toFloat(),
            unit = entity.attributes["temperature_unit"]?.toString(),
        )
        "select", "input_select" -> controls += TileControlItem(
            type = TileControlType.SELECT,
            entityId = entity.entityId,
            actionDomain = domain,
            actionName = "select_option",
            actionField = "option",
        )
    }
    if (entity.groupMembers().isNotEmpty()) {
        controls += TileControlItem(type = TileControlType.GROUP_MEMBERS, entityId = entity.entityId)
    }
    return controls
}

@Composable
private fun RenderControl(
    item: TileControlItem,
    fallbackEntityId: String,
    entities: Map<String, Entity>,
    onCallService: (String, String, Map<String, Any?>, String?) -> Unit,
) {
    val entityId = item.entityId ?: fallbackEntityId
    val entity = entities[entityId] ?: return
    when (item.type) {
        TileControlType.ENTITY_STATE -> {
            val value = item.attribute?.let { entity.attributes[it] } ?: entity.state
            Column {
                Text(item.label ?: entity.displayName())
                Text(if (item.attribute == null) entity.visualSummary() else value.toString())
                entity.visualColor()?.let { color ->
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .background(color)
                            .padding(4.dp),
                    )
                }
            }
        }
        TileControlType.TOGGLE -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.label ?: entity.displayName())
                Switch(
                    checked = entity.state == "on",
                    onCheckedChange = {
                        onCallService("homeassistant", "toggle", emptyMap(), entity.entityId)
                    },
                )
            }
        }
        TileControlType.SLIDER -> NumericControl(item, entity, onCallService)
        TileControlType.COLOR -> ColorControl(item, entity, onCallService)
        TileControlType.COLOR_TEMPERATURE -> ColorTemperatureControl(item, entity, onCallService)
        TileControlType.SELECT -> SelectControl(item, entity, onCallService)
        TileControlType.ACTION -> {
            val domain = item.actionDomain ?: return
            val action = item.actionName ?: return
            Button(
                onClick = { onCallService(domain, action, item.actionData.toServiceData(), entity.entityId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(item.label ?: "$domain.$action")
            }
        }
        TileControlType.GROUP_MEMBERS -> {
            val members = entity.groupMembers()
            if (members.isNotEmpty()) {
                Text(item.label ?: "Entities")
                members.forEach { memberId ->
                    entities[memberId]?.let { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(member.displayName())
                                Text(member.visualSummary())
                                member.visualColor()?.let { color ->
                                    Spacer(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(color)
                                            .padding(2.dp),
                                    )
                                }
                            }
                            Switch(
                                checked = member.state == "on",
                                onCheckedChange = {
                                    onCallService("homeassistant", "toggle", emptyMap(), member.entityId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericControl(
    item: TileControlItem,
    entity: Entity,
    onCallService: (String, String, Map<String, Any?>, String?) -> Unit,
) {
    val domain = item.actionDomain ?: return
    val action = item.actionName ?: return
    val field = item.actionField ?: return
    val raw = item.attribute?.let { entity.attributes[it] } ?: entity.state
    val actual = when {
        entity.entityId.startsWith("light.") && item.attribute == "brightness" ->
            (raw as? Number)?.toFloat()?.div(255f)?.times(100f)
        else -> (raw as? Number)?.toFloat() ?: raw.toString().toFloatOrNull()
    }
    val min = item.min ?: 0f
    val max = item.max ?: 100f
    var value by remember(entity.entityId, raw) { mutableStateOf((actual ?: min).coerceIn(min, max)) }
    Column {
        Text("${item.label ?: item.attribute ?: field}: ${formatControlValue(value)}${item.unit.orEmpty()}")
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = {
                onCallService(
                    domain,
                    action,
                    item.actionData.toServiceData() + (field to value),
                    entity.entityId,
                )
            },
            valueRange = min..max,
        )
    }
}

@Composable
private fun ColorControl(
    item: TileControlItem,
    entity: Entity,
    onCallService: (String, String, Map<String, Any?>, String?) -> Unit,
) {
    val hs = entity.attributes["hs_color"] as? List<*>
    var hue by remember(entity.entityId, hs) { mutableStateOf((hs?.getOrNull(0) as? Number)?.toFloat() ?: 0f) }
    var saturation by remember(entity.entityId, hs) {
        mutableStateOf((hs?.getOrNull(1) as? Number)?.toFloat() ?: 100f)
    }
    val swatch = Color.hsv(hue, saturation / 100f, 1f)
    Column {
        Text(item.label ?: "Color")
        Spacer(
            Modifier
                .fillMaxWidth()
                .background(swatch)
                .padding(8.dp),
        )
        Text("Hue ${hue.toInt()}°")
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = {
                onCallService("light", "turn_on", mapOf("hs_color" to listOf(hue, saturation)), entity.entityId)
            },
            valueRange = 0f..360f,
        )
        Text("Saturation ${saturation.toInt()}%")
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            onValueChangeFinished = {
                onCallService("light", "turn_on", mapOf("hs_color" to listOf(hue, saturation)), entity.entityId)
            },
            valueRange = 0f..100f,
        )
    }
}

@Composable
private fun ColorTemperatureControl(
    item: TileControlItem,
    entity: Entity,
    onCallService: (String, String, Map<String, Any?>, String?) -> Unit,
) {
    val control = entity.getColorTemperature()
    val min = control?.min ?: (entity.attributes["min_color_temp_kelvin"] as? Number)?.toFloat() ?: 2000f
    val max = control?.max ?: (entity.attributes["max_color_temp_kelvin"] as? Number)?.toFloat() ?: 6500f
    val raw = (entity.attributes["color_temp_kelvin"] as? Number)?.toFloat() ?: ((min + max) / 2)
    var value by remember(entity.entityId, raw) { mutableStateOf(raw.coerceIn(min, max)) }
    Column {
        Text("${item.label ?: "Color temperature"}: ${value.toInt()} K")
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = {
                onCallService("light", "turn_on", mapOf("color_temp_kelvin" to value.toInt()), entity.entityId)
            },
            valueRange = min..max,
        )
    }
}

@Composable
private fun SelectControl(
    item: TileControlItem,
    entity: Entity,
    onCallService: (String, String, Map<String, Any?>, String?) -> Unit,
) {
    val options = when {
        item.attribute != null && entity.attributes[item.attribute + "_list"] is List<*> ->
            entity.attributes[item.attribute + "_list"] as List<*>
        item.attribute == "effect" -> entity.attributes["effect_list"] as? List<*> ?: emptyList<Any>()
        else -> entity.attributes["options"] as? List<*> ?: emptyList<Any>()
    }.map { it.toString() }
    if (options.isEmpty()) return
    val current = item.attribute?.let { entity.attributes[it]?.toString() } ?: entity.state
    val domain = item.actionDomain ?: return
    val action = item.actionName ?: return
    val field = item.actionField ?: return
    HADropdownMenu(
        items = options.map { HADropdownItem(it, it) },
        selectedKey = current,
        onItemSelected = {
            onCallService(
                domain,
                action,
                item.actionData.toServiceData() + (field to it),
                entity.entityId,
            )
        },
        label = item.label ?: item.attribute ?: field,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Map<String, String>.toServiceData(): Map<String, Any?> = mapValues { (_, raw) ->
    val value = raw.trim()
    when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        value.toIntOrNull() != null -> value.toInt()
        value.toDoubleOrNull() != null -> value.toDouble()
        value.contains(",") -> value.split(",").map(String::trim).filter(String::isNotEmpty)
        else -> value
    }
}

private fun Entity.groupMembers(): List<String> =
    (attributes["entity_id"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()

private fun Entity.displayName(): String =
    attributes["friendly_name"]?.toString()?.takeIf { it.isNotBlank() } ?: entityId

private fun Entity.visualSummary(): String {
    val values = mutableListOf(state)
    if (entityId.startsWith("light.")) {
        (attributes["brightness"] as? Number)?.toFloat()?.let {
            values += "${(it / 255f * 100f).toInt()}%"
        }
        attributes["color_mode"]?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
    }
    return values.joinToString(" · ")
}

private fun Entity.visualColor(): Color? {
    val rgb = attributes["rgb_color"] as? List<*>
    if (rgb?.size == 3) {
        val values = rgb.mapNotNull { (it as? Number)?.toFloat() }
        if (values.size == 3) {
            return Color(values[0] / 255f, values[1] / 255f, values[2] / 255f)
        }
    }

    val hs = attributes["hs_color"] as? List<*>
    if (hs?.size == 2) {
        val hue = (hs[0] as? Number)?.toFloat()
        val saturation = (hs[1] as? Number)?.toFloat()
        if (hue != null && saturation != null) return Color.hsv(hue, saturation / 100f, 1f)
    }

    if (attributes["color_mode"]?.toString() == "color_temp") {
        val kelvin = (attributes["color_temp_kelvin"] as? Number)?.toFloat() ?: return null
        val min = (attributes["min_color_temp_kelvin"] as? Number)?.toFloat() ?: 2000f
        val max = (attributes["max_color_temp_kelvin"] as? Number)?.toFloat() ?: 6500f
        val t = ((kelvin - min) / (max - min).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return Color(
            red = 1f - 0.35f * t,
            green = 0.55f + 0.3f * t,
            blue = 0.2f + 0.8f * t,
        )
    }
    return null
}

private fun formatControlValue(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.2f".format(value)
