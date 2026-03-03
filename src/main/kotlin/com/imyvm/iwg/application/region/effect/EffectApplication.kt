package com.imyvm.iwg.application.region.effect

import com.imyvm.iwg.application.region.effect.helper.getActiveEffects
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.config.EffectConfig.EFFECT_DURATION_SECONDS
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier

fun applyRegionEffectsToPlayers(server: MinecraftServer) {
    for (player in getOnlinePlayers(server)) {
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.world, player.blockX, player.blockZ) ?: continue
        val (region, scope) = regionAndScope
        val effects = getActiveEffects(region, player.uuid, scope)
        val duration = EFFECT_DURATION_SECONDS.value * 20
        for ((key, amplifier) in effects) {
            val effectEntry = Registries.STATUS_EFFECT.getEntry(Identifier.of("minecraft:${key.effectId}")).orElse(null) ?: continue
            player.addStatusEffect(StatusEffectInstance(effectEntry, duration, amplifier.coerceIn(0, 255), true, false, true))
        }
    }
}
