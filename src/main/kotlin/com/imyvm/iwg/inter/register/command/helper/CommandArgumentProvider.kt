package com.imyvm.iwg.inter.register.command.helper

import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.infra.RegionDatabase
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import java.util.*
import java.util.concurrent.CompletableFuture

val SHAPE_TYPE_SUGGESTION_PROVIDER: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
    GeoShapeType.entries
        .filter { it != GeoShapeType.UNKNOWN }
        .forEach { builder.suggest(it.name.lowercase(Locale.getDefault())) }
    CompletableFuture.completedFuture(builder.build())
}

val REGION_NAME_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { _, builder ->
    val regionNames = RegionDatabase.getRegionList().map { it.name }
    regionNames.forEach { builder.suggest(it) }
    builder.buildFuture()
}

val SCOPE_NAME_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { context, builder ->
    val regionIdentifier = StringArgumentType.getString(context, "regionIdentifier")
    getScopesForRegion(regionIdentifier).forEach { builder.suggest(it) }
    builder.buildFuture()
}

val ONLINE_PLAYER_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { context, builder ->
    val source = context.source
    source.server.playerManager.playerList.forEach { player ->
        builder.suggest(player.name.string)
    }
    builder.buildFuture()
}

val SETTING_TYPE_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { context, builder ->
    listOf("permission", "effect", "rule").forEach { builder.suggest(it) }
    builder.buildFuture()
}

val SETTING_KEY_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { context, builder ->
    val type = try {
        StringArgumentType.getString(context, "settingType")
    } catch (e: Exception) {
        null
    }

    when (type?.lowercase()) {
        "permission" -> PermissionKey.entries.forEach { builder.suggest(it.name) }
        "effect"     -> EffectKey.entries.forEach { builder.suggest(it.name) }
        "rule"       -> RuleKey.entries.forEach { builder.suggest(it.name) }
    }

    builder.buildFuture()
}

fun getScopesForRegion(identifier: String): List<String> {
    val region = RegionDatabase.getRegionList().find {
        it.name.equals(identifier, ignoreCase = true) || it.numberID.toString() == identifier
    }
    return region?.geometryScope?.map { it.scopeName } ?: emptyList()
}