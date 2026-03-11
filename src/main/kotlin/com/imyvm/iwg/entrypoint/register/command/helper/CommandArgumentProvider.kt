package com.imyvm.iwg.inter.register.command.helper

import com.imyvm.iwg.domain.component.EffectKey
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.EntryExitMessageKey
import com.imyvm.iwg.domain.component.EntryExitToggleKey
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.translator.getOnlinePlayers
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
    regionNames.forEach { name ->
        if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)
    }
    builder.buildFuture()
}

val SCOPE_NAME_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { context, builder ->
    val regionIdentifier = StringArgumentType.getString(context, "regionIdentifier")
    getScopesForRegion(regionIdentifier).forEach { name ->
        if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)
    }
    builder.buildFuture()
}

val ONLINE_PLAYER_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { context, builder ->
    val sourceServer = context.source.server
    getOnlinePlayers(sourceServer).forEach { player ->
        builder.suggest(player.name.string)
    }
    builder.buildFuture()
}

val SETTING_TYPE_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { context, builder ->
    listOf("permission", "effect", "rule", "entry_exit").forEach { builder.suggest(it) }
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
        "entry_exit" -> {
            EntryExitToggleKey.entries.forEach { builder.suggest(it.name) }
            EntryExitMessageKey.entries.forEach { builder.suggest(it.name) }
        }
        else -> {
            PermissionKey.entries.forEach { builder.suggest(it.name) }
            EffectKey.entries.forEach { builder.suggest(it.name) }
            RuleKey.entries.forEach { builder.suggest(it.name) }
            EntryExitToggleKey.entries.forEach { builder.suggest(it.name) }
            EntryExitMessageKey.entries.forEach { builder.suggest(it.name) }
        }
    }

    builder.buildFuture()
}

fun getScopesForRegion(identifier: String): List<String> {
    val region = RegionDatabase.getRegionList().find {
        it.name.equals(identifier, ignoreCase = true) || it.numberID.toString() == identifier
    }
    return region?.geometryScope?.map { it.scopeName } ?: emptyList()
}