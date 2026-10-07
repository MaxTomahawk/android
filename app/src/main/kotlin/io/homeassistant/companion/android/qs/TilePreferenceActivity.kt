package io.homeassistant.companion.android.qs

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.qs.TileDao
import io.homeassistant.companion.android.database.qs.TileTapAction
import io.homeassistant.companion.android.database.qs.holdActionType
import io.homeassistant.companion.android.database.qs.isSetup
import io.homeassistant.companion.android.database.qs.splitTileActionData
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.LaunchActivity
import io.homeassistant.companion.android.launch.intentLaunchWithNavigateTo
import io.homeassistant.companion.android.settings.SettingsActivity
import io.homeassistant.companion.android.settings.qs.tileSlots
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import timber.log.Timber

@AndroidEntryPoint
class TilePreferenceActivity : BaseActivity() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var tileDao: TileDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tileId = resolveTileId()
        lifecycleScope.launch {
            val tileData = tileDao.get(tileId)
            if (!serverManager.isRegistered()) {
                launchAndFinish(Intent(this@TilePreferenceActivity, LaunchActivity::class.java))
                return@launch
            }
            if (tileData?.isSetup != true) {
                launchAndFinish(
                    SettingsActivity.newInstance(this@TilePreferenceActivity, SettingsActivity.Deeplink.QSTile(tileId)),
                )
                return@launch
            }

            when (tileData.holdActionType) {
                TileTapAction.Automatic -> {
                    if (tileData.entityId.isNotBlank()) {
                        val domain = tileData.entityId.substringBefore('.')
                        if (domain in EntityExt.APP_PRESS_ACTION_DOMAINS) {
                            withContext(Dispatchers.IO) {
                                onEntityPressedWithoutState(
                                    tileData.entityId,
                                    serverManager.integrationRepository(tileData.serverId),
                                )
                            }
                            finishWithoutAnimation()
                        } else {
                            launchAndFinish(
                                intentLaunchWithNavigateTo(
                                    FrontendTarget.EntityMoreInfo(tileData.entityId),
                                    tileData.serverId,
                                ),
                            )
                        }
                    } else {
                        finishWithoutAnimation()
                    }
                }

                TileTapAction.Toggle -> {
                    if (tileData.entityId.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            serverManager.webSocketRepository(tileData.serverId).callService(
                                domain = "homeassistant",
                                service = "toggle",
                                target = mapOf("entity_id" to tileData.entityId),
                            )
                        }
                    }
                    finishWithoutAnimation()
                }

                TileTapAction.MoreInfo -> {
                    if (tileData.entityId.isNotBlank()) {
                        launchAndFinish(
                            intentLaunchWithNavigateTo(
                                FrontendTarget.EntityMoreInfo(tileData.entityId),
                                tileData.serverId,
                            ),
                        )
                    } else {
                        finishWithoutAnimation()
                    }
                }

                TileTapAction.PerformAction -> {
                    val domain = tileData.holdActionDomain
                    val action = tileData.holdActionName
                    if (!domain.isNullOrBlank() && !action.isNullOrBlank()) {
                        val renderedData = renderActionData(tileData.holdActionData, tileData.serverId)
                        val data = renderedData?.takeIf { it.isNotBlank() }?.let {
                            kotlinJsonMapper.decodeFromString<Map<String, Any?>>(MapAnySerializer, it)
                        }.orEmpty()
                        val payload = splitTileActionData(data)
                        withContext(Dispatchers.IO) {
                            serverManager.webSocketRepository(tileData.serverId).callService(
                                domain = domain,
                                service = action,
                                serviceData = payload.serviceData,
                                target = payload.target,
                            )
                        }
                    }
                    finishWithoutAnimation()
                }

                TileTapAction.Navigate -> {
                    val path = tileData.holdNavigationPath
                    if (!path.isNullOrBlank()) {
                        launchAndFinish(intentLaunchWithNavigateTo(FrontendTarget.Path(path), tileData.serverId))
                    } else {
                        finishWithoutAnimation()
                    }
                }

                TileTapAction.Url -> {
                    val url = tileData.holdUrl
                    if (!url.isNullOrBlank()) {
                        launchAndFinish(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } else {
                        finishWithoutAnimation()
                    }
                }

                TileTapAction.Assist -> launchAndFinish(
                    AssistActivity.newInstance(
                        context = this@TilePreferenceActivity,
                        serverId = tileData.serverId,
                        startListening = true,
                        fromFrontend = false,
                    ),
                )

                TileTapAction.None -> finishWithoutAnimation()
            }
        }
    }

    private suspend fun renderActionData(value: String?, serverId: Int): String? {
        if (value == null || (!value.contains("{{") && !value.contains("{%"))) return value
        return try {
            serverManager.integrationRepository(serverId).renderTemplate(value, emptyMap())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Unable to render hold action data template")
            value
        }
    }

    private fun resolveTileId(): String {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return "-1"
        val component = intent.extras?.let { extras ->
            BundleCompat.getParcelable(extras, Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } ?: return "-1"
        return try {
            val tileClass = Class.forName(component.className)
            tileSlots.firstOrNull { it.serviceClass == tileClass }?.id?.value ?: "-1"
        } catch (e: Exception) {
            Timber.e(e, "Couldn't get tile ID for component $component")
            "-1"
        }
    }

    private fun launchAndFinish(intent: Intent) {
        startActivity(intent)
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
