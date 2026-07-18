package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.ScopeNotFoundException
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

internal fun getScopeOrNotify(player: ServerPlayer, region: Region, scopeName: String): GeoScope? = try {
    region.getScopeByName(scopeName)
} catch (error: ScopeNotFoundException) {
    player.sendSystemMessage(Translator.tr("region.error.no_scope", error.scopeName, error.regionName))
    null
}
