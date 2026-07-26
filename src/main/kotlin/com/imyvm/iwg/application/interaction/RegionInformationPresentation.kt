package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.EffectSetting
import com.imyvm.iwg.domain.component.EntryExitMessageSetting
import com.imyvm.iwg.domain.component.EntryExitToggleSetting
import com.imyvm.iwg.domain.component.ExtensionPermissionSetting
import com.imyvm.iwg.domain.component.ExtensionRuleSetting
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.domain.component.Setting
import com.imyvm.iwg.domain.component.UnknownGeometry
import com.imyvm.iwg.util.text.Translator
import com.imyvm.iwg.util.translator.resolvePlayerName
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.util.Locale

internal data class SettingPresentationKeys(
    val header: String,
    val globalHeader: String,
    val personalHeader: String,
    val permissionHeader: String,
    val effectHeader: String,
    val ruleHeader: String,
    val entryExitHeader: String,
    val item: String
)

private val REGION_SETTING_PRESENTATION_KEYS = SettingPresentationKeys(
    header = "region.setting.header",
    globalHeader = "region.setting.global.header",
    personalHeader = "region.setting.personal.header",
    permissionHeader = "region.setting.permission.header",
    effectHeader = "region.setting.effect.header",
    ruleHeader = "region.setting.rule.header",
    entryExitHeader = "region.setting.entry_exit.header",
    item = "region.setting.item"
)

private val SCOPE_SETTING_PRESENTATION_KEYS = SettingPresentationKeys(
    header = "geo.scope.setting.header",
    globalHeader = "geo.scope.setting.global.header",
    personalHeader = "geo.scope.setting.personal.header",
    permissionHeader = "geo.scope.setting.permission.header",
    effectHeader = "geo.scope.setting.effect.header",
    ruleHeader = "geo.scope.setting.rule.header",
    entryExitHeader = "geo.scope.setting.entry_exit.header",
    item = "geo.scope.setting.item"
)

internal sealed interface SettingPresentationTarget {
    val keys: SettingPresentationKeys
    fun translateHeader(): Component

    data object RegionSettings : SettingPresentationTarget {
        override val keys = REGION_SETTING_PRESENTATION_KEYS

        override fun translateHeader(): Component = Translator.tr(keys.header)
    }

    data class ScopeSettings(val scopeName: String) : SettingPresentationTarget {
        override val keys = SCOPE_SETTING_PRESENTATION_KEYS

        override fun translateHeader(): Component = Translator.tr(keys.header, scopeName)
    }
}

internal fun buildRegionScopeInfoLines(server: MinecraftServer, region: Region): List<Component> =
    buildList {
        region.scopes.forEachIndexed { index, scope ->
            add(buildScopeInfoLine(scope, index))
            addAll(buildScopeSettingInfoLines(server, scope))
        }
    }

internal fun buildRegionSettingInfoLines(server: MinecraftServer, region: Region): List<Component> =
    formatSettingInfoLines(server, region.settingsSnapshot(), SettingPresentationTarget.RegionSettings)

internal fun buildScopeSettingInfoLines(server: MinecraftServer, scope: GeoScope): List<Component> =
    formatSettingInfoLines(
        server,
        scope.settingsSnapshot(),
        SettingPresentationTarget.ScopeSettings(scope.scopeName)
    )

internal fun buildScopeInfoLine(scope: GeoScope, index: Int): Component {
    val shapeInfoString = scope.geoShape?.let(::buildShapeInfoLine)?.string.orEmpty()
    val dimensionDisplay = dimensionDisplayName(scope)
    val point = scope.teleportPoint
    return if (point == null) {
        Translator.tr(
            "geo.scope.info",
            index,
            scope.scopeName,
            shapeInfoString,
            dimensionDisplay,
            scope.showOnDynmap
        )
    } else {
        Translator.tr(
            "geo.scope.info.with_teleport_point",
            index,
            scope.scopeName,
            shapeInfoString,
            scope.isTeleportPointPublic,
            point.x,
            point.y,
            point.z,
            dimensionDisplay,
            scope.showOnDynmap
        )
    }
}

internal fun buildShapeInfoLine(shape: GeoShape): Component {
    val area = String.format(Locale.ROOT, "%.2f", shape.calculateArea())
    return when (val geometry = shape.typedGeometry) {
        is CircleGeometry -> Translator.tr(
            "geo.shape.circle.info",
            geometry.centerX,
            geometry.centerZ,
            geometry.radius,
            area
        )
        is RectangleGeometry -> Translator.tr(
            "geo.shape.rectangle.info",
            geometry.west,
            geometry.north,
            geometry.east,
            geometry.south,
            area
        )
        is PolygonGeometry -> Translator.tr(
            "geo.shape.polygon.info",
            polygonCoordinates(geometry),
            area
        )
        UnknownGeometry -> Translator.tr("geo.shape.unknown.info", area)
    }
}

internal fun formatLegacySettingInfoLines(
    server: MinecraftServer,
    settings: List<Setting>,
    key: String,
    scopeName: String?
): List<Component> = formatSettingInfoLines(
    server,
    settings,
    legacySettingPresentationTarget(key, scopeName)
)

internal fun legacySettingPresentationTarget(
    key: String,
    scopeName: String?
): SettingPresentationTarget = when (key) {
    "region.setting" -> {
        require(scopeName == null) { "region setting presentation does not accept a scope name" }
        SettingPresentationTarget.RegionSettings
    }
    "geo.scope.setting" -> SettingPresentationTarget.ScopeSettings(
        requireNotNull(scopeName) { "scope setting presentation requires a scope name" }
    )
    else -> throw IllegalArgumentException("unsupported setting presentation key: $key")
}

internal fun permissionSettingDisplayName(setting: PermissionSetting): String =
    Translator.raw(setting.key.displayTranslationKey)

internal fun permissionSettingDisplayName(setting: ExtensionPermissionSetting): String = setting.key.id

private fun dimensionDisplayName(scope: GeoScope): String {
    val key = "geo.dimension.${scope.worldId.namespace}.${scope.worldId.path}"
    return if (Translator.hasTranslation(key)) Translator.tr(key).string else scope.worldId.toString()
}

private fun polygonCoordinates(polygon: PolygonGeometry): String = buildString {
    for (index in 0 until polygon.vertexCount) {
        if (index > 0) append(", ")
        append('(').append(polygon.x(index)).append(", ").append(polygon.z(index)).append(')')
    }
}

private fun formatSettingInfoLines(
    server: MinecraftServer,
    settings: List<Setting>,
    target: SettingPresentationTarget
): List<Component> {
    if (settings.isEmpty()) return emptyList()

    return buildList {
        val keys = target.keys
        add(target.translateHeader())

        val globalSettings = settings.filter { !it.isPersonal }
        if (globalSettings.isNotEmpty()) {
            add(Translator.tr(keys.globalHeader))
            appendTypeGroups(globalSettings, keys)
        }

        settings.filter { it.isPersonal }
            .groupBy { it.playerUUID }
            .forEach { (uuid, playerSettings) ->
                add(Translator.tr(keys.personalHeader, resolvePlayerName(server, uuid)))
                appendTypeGroups(playerSettings, keys)
            }
    }
}

private fun MutableList<Component>.appendTypeGroups(
    settings: List<Setting>,
    keys: SettingPresentationKeys
) {
    val builtInPermissions = settings.filterIsInstance<PermissionSetting>()
    val extensionPermissions = settings.filterIsInstance<ExtensionPermissionSetting>()
    if (builtInPermissions.isNotEmpty() || extensionPermissions.isNotEmpty()) {
        add(Translator.tr(keys.permissionHeader))
        builtInPermissions.forEach { setting ->
            add(Translator.tr(keys.item, permissionSettingDisplayName(setting), setting.value))
        }
        extensionPermissions.forEach { setting ->
            add(Translator.tr(keys.item, permissionSettingDisplayName(setting), setting.value))
        }
    }

    val effects = settings.filterIsInstance<EffectSetting>()
    if (effects.isNotEmpty()) {
        add(Translator.tr(keys.effectHeader))
        effects.forEach { setting -> add(Translator.tr(keys.item, setting.key, setting.value)) }
    }

    val rules = settings.filter { it is RuleSetting || it is ExtensionRuleSetting }
    if (rules.isNotEmpty()) {
        add(Translator.tr(keys.ruleHeader))
        rules.forEach { setting -> add(Translator.tr(keys.item, setting.key, setting.value)) }
    }

    val notificationToggles = settings.filterIsInstance<EntryExitToggleSetting>()
    val notificationMessages = settings.filterIsInstance<EntryExitMessageSetting>()
    if (notificationToggles.isNotEmpty() || notificationMessages.isNotEmpty()) {
        add(Translator.tr(keys.entryExitHeader))
        notificationToggles.forEach { setting ->
            add(Translator.tr(keys.item, setting.key, setting.value))
        }
        notificationMessages.forEach { setting ->
            add(Translator.tr(keys.item, setting.key, "&r\"${setting.value}\""))
        }
    }
}
