package io.homeassistant.companion.android.qs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileEntity
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.TileTextPart
import io.homeassistant.companion.android.database.qs.TileTextSource
import io.homeassistant.companion.android.database.qs.TileType
import io.homeassistant.companion.android.database.qs.decodeStringList
import io.homeassistant.companion.android.database.qs.decodeTileIconRules
import io.homeassistant.companion.android.database.qs.decodeTileTextParts
import io.homeassistant.companion.android.database.qs.getHighestInUse
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.database.qs.labelSourceType
import io.homeassistant.companion.android.database.qs.numberedId
import io.homeassistant.companion.android.database.qs.splitTileActionData
import io.homeassistant.companion.android.database.qs.stateSourceEntityId
import io.homeassistant.companion.android.database.qs.subtitleSourceType
import io.homeassistant.companion.android.database.qs.tapActionType
import io.homeassistant.companion.android.database.qs.type
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.intentLaunchWithNavigateTo
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.qs.TileId
import io.homeassistant.companion.android.settings.qs.updateActiveTileServices
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TILE_ICON_SIZE_DP = 48

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
internal abstract class TileExtensions : TileService() {

    abstract val tileId: TileId
    abstract fun getTile(): Tile?

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var tileDao: TileDao

    private val mainScope = MainScope()

    private var stateUpdateJob: Job? = null

    override fun onClick() {
        super.onClick()
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(tile)
                tileClicked(tile, isUnlock = false)
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Timber.d("Tile: $tileId added")
        handleInject()
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(tile)
            }
        }
        mainScope.launch {
            setTileAdded(added = true)
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Timber.d("Tile: $tileId removed")
        handleInject()
        runBlocking {
            setTileAdded(added = false)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Timber.d("Tile: $tileId is in view")
        getTile()?.let { tile ->
            mainScope.launch {
                setTileData(tile)
            }
            stateUpdateJob = mainScope.launch {
                val tileData = tileDao.get(tileId.value)
                if (tileData != null &&
                    tileData.isSetup &&
                    serverManager.getServer(tileData.serverId) != null
                ) {
                    val repository = serverManager.integrationRepository(tileData.serverId)
                    val entityIds = listOf(tileData.entityId, tileData.stateSourceEntityId)
                        .filter { it.isNotBlank() }
                        .distinct()
                    if (entityIds.isNotEmpty()) {
                        launch {
                            repository.getEntityUpdates(entityIds)?.collect {
                                setTileData(tile)
                            }
                        }
                    }

                    tileData.dynamicTemplates().forEach { template ->
                        launch {
                            observeTemplate(template) {
                                setTileData(tile)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        Timber.d("Tile: $tileId is no longer in view")
        stateUpdateJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }

    private suspend fun setTileData(tile: Tile): Boolean {
        Timber.d("Attempting to set tile data for tile ID: $tileId")
        val context = applicationContext
        val tileData = tileDao.get(tileId.value)
        try {
            return if (tileData != null && tileData.isSetup) {
                val repository = serverManager.integrationRepository(tileData.serverId)
                val mainEntity = tileData.entityId.takeIf { it.isNotBlank() }?.let { entityId ->
                    withContext(Dispatchers.IO) {
                        try {
                            repository.getEntity(entityId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "Unable to get entity $entityId for tile ID: $tileId")
                            null
                        }
                    }
                }
                val stateEntity = if (tileData.stateSourceEntityId == tileData.entityId) {
                    mainEntity
                } else {
                    tileData.stateSourceEntityId.takeIf { it.isNotBlank() }?.let { entityId ->
                        withContext(Dispatchers.IO) {
                            try {
                                repository.getEntity(entityId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Timber.w(e, "Unable to get state entity $entityId for tile ID: $tileId")
                                null
                            }
                        }
                    }
                }

                val labelParts = decodeTileTextParts(tileData.labelPartsJson)
                val subtitleParts = decodeTileTextParts(tileData.subtitlePartsJson)
                val label = if (labelParts.isNotEmpty()) {
                    resolveTextParts(labelParts, mainEntity)
                } else {
                    resolveDisplayText(
                        source = tileData.labelSourceType,
                        fixedValue = tileData.label,
                        attribute = tileData.labelAttribute,
                        entity = mainEntity,
                    )
                } ?: tileData.label
                tile.label = label
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                    tile.subtitle = if (subtitleParts.isNotEmpty()) {
                        resolveTextParts(subtitleParts, mainEntity)
                    } else {
                        resolveDisplayText(
                            source = tileData.subtitleSourceType,
                            fixedValue = tileData.subtitle,
                            attribute = tileData.subtitleAttribute,
                            entity = mainEntity,
                        )
                    }
                }
                tile.contentDescription = renderTileText(tileData.contentDescriptionTemplate) ?: label

                tile.state = resolveTileState(tileData, stateEntity)

                val renderedIcon = renderTileText(tileData.iconTemplate)?.takeIf { it.isNotBlank() }
                val stateIcon = decodeTileIconRules(tileData.iconRulesJson)
                    .firstOrNull { rule -> rule.state.equals(stateEntity?.state, ignoreCase = true) }
                    ?.iconName
                    ?.takeIf { it.isNotBlank() }
                getTileIcon(
                    stateIcon ?: renderedIcon ?: tileData.iconName,
                    mainEntity ?: stateEntity,
                    context,
                )?.let { icon ->
                    tile.icon = Icon.createWithBitmap(icon)
                }
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.R)) {
                    tile.stateDescription =
                        renderTileText(tileData.stateDescriptionTemplate) ?: stateEntity?.state
                }
                Timber.d("Tile data set for tile ID: $tileId")
                tile.updateTile()
                true
            } else {
                if (tileData != null) {
                    Timber.d("Tile data found but not setup for tile ID: $tileId")
                } else {
                    Timber.d("No tile data found for tile ID: $tileId")
                }
                tile.state =
                    if (serverManager.isRegistered()) {
                        Tile.STATE_INACTIVE
                    } else {
                        Tile.STATE_UNAVAILABLE
                    }
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                    tile.subtitle = getString(commonR.string.tile_not_setup)
                }
                tile.updateTile()
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to set tile data for tile ID: $tileId")
            return false
        }
    }

    private suspend fun resolveTextParts(parts: List<TileTextPart>, entity: Entity?): String = buildString {
        parts.forEach { part ->
            append(
                when (part.sourceType) {
                    TileTextSource.FIXED -> part.value.orEmpty()
                    TileTextSource.NAME -> entity?.attributes?.get("friendly_name")?.toString()
                        ?: entity?.entityId.orEmpty()
                    TileTextSource.STATE -> entity?.state.orEmpty()
                    TileTextSource.ATTRIBUTE -> part.value?.let {
                        entity?.attributes?.get(it)?.toString()
                    }.orEmpty()
                },
            )
        }
    }

    private suspend fun resolveDisplayText(
        source: TileTextSource,
        fixedValue: String?,
        attribute: String?,
        entity: Entity?,
    ): String? = when (source) {
        TileTextSource.FIXED -> renderTileText(fixedValue)
        TileTextSource.NAME -> entity?.attributes?.get("friendly_name")?.toString() ?: entity?.entityId
        TileTextSource.STATE -> entity?.state
        TileTextSource.ATTRIBUTE -> attribute?.let { entity?.attributes?.get(it)?.toString() }
    }

    private suspend fun renderTileText(value: String?): String? {
        if (value == null || !value.isTileTemplate()) return value
        return try {
            serverManager.integrationRepository(
                checkNotNull(tileDao.get(tileId.value)?.serverId),
            ).renderTemplate(value, emptyMap())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Unable to render template for tile ID: $tileId")
            value
        }
    }

    private suspend fun observeTemplate(value: String?, onUpdate: suspend (String?) -> Unit) {
        if (value == null || !value.isTileTemplate()) return
        try {
            val tileData = tileDao.get(tileId.value) ?: return
            serverManager.integrationRepository(tileData.serverId).getTemplateUpdates(value)?.collect(onUpdate)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Unable to observe template for tile ID: $tileId")
        }
    }

    private suspend fun resolveTileState(tileData: TileEntity, entity: Entity?): Int {
        val templateState = tileData.stateTemplate?.takeIf { it.isNotBlank() }?.let { renderTileText(it) }
        if (templateState != null) return templateState.toTileState()

        val usesEntityState = tileData.type == TileType.Entity ||
            (tileData.type == TileType.Template && tileData.entityId.isNotBlank()) ||
            tileData.entityId.substringBefore('.') in toggleDomainsWithLock
        if (!usesEntityState) return Tile.STATE_INACTIVE

        val activeStates = decodeStringList(tileData.activeStatesJson)
        return when {
            entity == null || entity.state in setOf("unavailable", "unknown") -> Tile.STATE_UNAVAILABLE
            activeStates.isNotEmpty() -> if (activeStates.any { it.equals(entity.state, ignoreCase = true) }) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            entity.isActive() -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
    }

    private fun TileEntity.dynamicTemplates(): List<String> = listOfNotNull(
        label.takeIf { it.isTileTemplate() },
        subtitle?.takeIf { it.isTileTemplate() },
        stateTemplate?.takeIf { it.isTileTemplate() },
        stateDescriptionTemplate?.takeIf { it.isTileTemplate() },
        iconTemplate?.takeIf { it.isTileTemplate() },
        contentDescriptionTemplate?.takeIf { it.isTileTemplate() },
    ).distinct()

    private fun String.toTileState(): Int = when (trim().lowercase()) {
        "active", "on", "true", "1" -> Tile.STATE_ACTIVE
        "unavailable", "unknown", "none", "null" -> Tile.STATE_UNAVAILABLE
        else -> Tile.STATE_INACTIVE
    }

    private fun String.isTileTemplate(): Boolean = contains("{{") || contains("{%")

    private suspend fun tileClicked(tile: Tile, isUnlock: Boolean) {
        Timber.d("Click detected for tile ID: $tileId")
        val context = applicationContext
        val tileData = tileDao.get(tileId.value)
        val vm = getSystemService<Vibrator>()
        if (!isUnlock) {
            if (tileData?.shouldVibrate == true) {
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                    vm?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vm?.vibrate(500)
                }
            }
            if (tileData?.authRequired == true && isSecure) {
                unlockAndRun {
                    mainScope.launch { tileClicked(tile, isUnlock = true) }
                }
                return
            }
        }

        val hasTile = setTileData(tile)
        if (hasTile) {
            if (tileData?.serverId == null || serverManager.getServer(tileData.serverId) == null) {
                tileClickedError(tileData, null)
                return
            }
            try {
                performTileAction(tileData)
                setTileData(tile)
                Timber.d("Tile action completed for tile ID: $tileId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tileClickedError(tileData, e)
            }
        } else {
            Timber.d("No tile data found for tile ID: $tileId")
            val tileSettingIntent = SettingsActivity.newInstance(
                context,
                SettingsActivity.Deeplink.QSTile(tileId.value),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
            withContext(Dispatchers.Main) {
                TileServiceCompat.startActivityAndCollapse(
                    this@TileExtensions,
                    PendingIntentActivityWrapper(
                        context,
                        tileId.value.hashCode(),
                        tileSettingIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT,
                        false,
                    ),
                )
            }
        }
    }

    private suspend fun performTileAction(tileData: TileEntity) {
        performConfiguredAction(
            tileData = tileData,
            actionType = tileData.tapActionType,
            domain = tileData.actionDomain,
            action = tileData.actionName,
            actionData = tileData.actionDataTemplate,
            navigationPath = tileData.tapNavigationPath,
            url = tileData.tapUrl,
            requestCode = tileData.tileId.hashCode(),
        )
    }

    private suspend fun performConfiguredAction(
        tileData: TileEntity,
        actionType: TileTapAction,
        domain: String?,
        action: String?,
        actionData: String?,
        navigationPath: String?,
        url: String?,
        requestCode: Int,
    ) {
        when (actionType) {
            TileTapAction.Automatic -> {
                require(tileData.entityId.isNotBlank()) { "Tile action requires an entity" }
                val domain = tileData.entityId.substringBefore('.')
                if (tileData.type == TileType.Basic || domain in EntityExt.APP_PRESS_ACTION_DOMAINS) {
                    withContext(Dispatchers.IO) {
                        onEntityPressedWithoutState(
                            tileData.entityId,
                            serverManager.integrationRepository(tileData.serverId),
                        )
                    }
                } else {
                    launchFromTile(
                        applicationContext.intentLaunchWithNavigateTo(
                            FrontendTarget.EntityMoreInfo(tileData.entityId),
                            tileData.serverId,
                        ),
                        requestCode,
                    )
                }
            }

            TileTapAction.Toggle -> {
                require(tileData.entityId.isNotBlank()) { "Toggle action requires an entity" }
                withContext(Dispatchers.IO) {
                    serverManager.webSocketRepository(tileData.serverId).callService(
                        domain = "homeassistant",
                        service = "toggle",
                        target = mapOf("entity_id" to tileData.entityId),
                    )
                }
            }

            TileTapAction.MoreInfo -> {
                require(tileData.entityId.isNotBlank()) { "More-info tile action requires an entity" }
                launchFromTile(
                    applicationContext.intentLaunchWithNavigateTo(
                        FrontendTarget.EntityMoreInfo(tileData.entityId),
                        tileData.serverId,
                    ),
                    requestCode,
                )
            }

            TileTapAction.PerformAction -> {
                val actionDomain = requireNotNull(domain?.takeIf { it.isNotBlank() }) {
                    "Perform action requires a domain"
                }
                val actionName = requireNotNull(action?.takeIf { it.isNotBlank() }) {
                    "Perform action requires an action"
                }
                val renderedData = renderTileText(actionData)?.trim()
                val data = if (renderedData.isNullOrBlank()) {
                    emptyMap()
                } else {
                    kotlinJsonMapper.decodeFromString<Map<String, Any?>>(MapAnySerializer, renderedData)
                }
                val payload = splitTileActionData(data)
                withContext(Dispatchers.IO) {
                    serverManager.webSocketRepository(tileData.serverId).callService(
                        domain = actionDomain,
                        service = actionName,
                        serviceData = payload.serviceData,
                        target = payload.target,
                    )
                }
            }

            TileTapAction.Navigate -> {
                val path = requireNotNull(navigationPath?.takeIf { it.isNotBlank() }) {
                    "Navigate action requires a path"
                }
                launchFromTile(
                    applicationContext.intentLaunchWithNavigateTo(FrontendTarget.Path(path), tileData.serverId),
                    requestCode,
                )
            }

            TileTapAction.Url -> {
                val target = requireNotNull(url?.takeIf { it.isNotBlank() }) { "URL action requires a URL" }
                launchFromTile(Intent(Intent.ACTION_VIEW, Uri.parse(target)), requestCode)
            }

            TileTapAction.Assist -> {
                launchFromTile(
                    AssistActivity.newInstance(
                        context = applicationContext,
                        serverId = tileData.serverId,
                        startListening = true,
                        fromFrontend = false,
                    ),
                    requestCode,
                )
            }

            TileTapAction.None -> Unit
        }
    }

    private suspend fun launchFromTile(intent: Intent, requestCode: Int) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        withContext(Dispatchers.Main) {
            TileServiceCompat.startActivityAndCollapse(
                this@TileExtensions,
                PendingIntentActivityWrapper(
                    applicationContext,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false,
                ),
            )
        }
    }

    private suspend fun tileClickedError(tileData: TileEntity?, e: Exception?) {
        if (e != null) Timber.e(e, "Unable to call service for tile ID: ${tileData?.id}")
        if (tileData != null && tileData.shouldVibrate) {
            val vm = getSystemService<Vibrator>()
            if (SdkVersion.isAtLeast(Build.VERSION_CODES.Q)) {
                vm?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vm?.vibrate(1000)
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                applicationContext,
                commonR.string.action_failure,
                Toast.LENGTH_SHORT,
            )
                .show()
        }
    }

    private suspend fun setTileAdded(added: Boolean) {
        tileDao.get(tileId.value)?.let {
            tileDao.add(it.copy(added = added))
        } ?: run {
            if (added) { // Store an empty tile in the database to track added
                tileDao.add(
                    TileEntity(
                        tileId = tileId.value,
                        added = true,
                        serverId = 0,
                        iconName = null,
                        entityId = "",
                        label = "",
                        subtitle = null,
                        shouldVibrate = false,
                        authRequired = false,
                    ),
                )
            } // else if it doesn't exist and is removed we don't have to save anything
        }

        val highestInUse = tileDao.getHighestInUse()?.numberedId ?: 0
        Timber.d("Highest tile in use: $highestInUse")
        updateActiveTileServices(highestInUse, applicationContext)
    }

    private fun getTileIcon(tileIconName: String?, entity: Entity?, context: Context): Bitmap? {
        // The system renders tile icons as alpha masks, so the fill color only carries the shape.
        if (!tileIconName.isNullOrBlank()) {
            return Mdi.fromHaName(tileIconName)?.toBitmap(context, TILE_ICON_SIZE_DP, Color.WHITE)
        }
        return entity?.getIcon()?.toBitmap(context, TILE_ICON_SIZE_DP, Color.WHITE)
    }

    companion object {
        private val toggleDomainsWithLock = EntityExt.DOMAINS_TOGGLE
    }

    private fun handleInject() {
        // onTileAdded/onTileRemoved might be called outside onCreate - onDestroy, which usually
        // handles injection. Because we need the DAO to save added/removed, inject it if required.
        if (!this::tileDao.isInitialized) {
            tileDao = EntryPointAccessors.fromApplication(
                this@TileExtensions.applicationContext,
                TileExtensionsEntryPoint::class.java,
            ).tileDao()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileExtensionsEntryPoint {
        fun tileDao(): TileDao
    }
}
