package com.imyvm.iwg.util.command

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.Region
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import java.util.*
import java.util.concurrent.CompletableFuture

val SHAPE_TYPE_SUGGESTION_PROVIDER: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
    Region.Companion.GeoShapeType.entries
        .filter { it != Region.Companion.GeoShapeType.UNKNOWN }
        .forEach { builder.suggest(it.name.lowercase(Locale.getDefault())) }
    CompletableFuture.completedFuture(builder.build())
}

val REGION_NAME_SUGGESTION_PROVIDER = SuggestionProvider<ServerCommandSource> { _, builder ->
    val regionNames = ImyvmWorldGeo.data.getRegionList().map { it.name }
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

fun getScopesForRegion(identifier: String): List<String> {
    val region = ImyvmWorldGeo.data.getRegionList().find {
        it.name.equals(identifier, ignoreCase = true) || it.numberID.toString() == identifier
    }
    return region?.geometryScope?.map { it.scopeName } ?: emptyList()
}