package com.imyvm.iwg.application.region.effect

import com.imyvm.iwg.application.region.effect.helper.getActiveEffects
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.config.EffectConfig.EFFECT_DURATION_SECONDS
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.resources.Identifier

fun applyRegionEffectsToPlayers(server: MinecraftServer) {
    for (player in getOnlinePlayers(server)) {
        val regionAndScope = RegionDatabase.getRegionAndScopeAt(player.level(), player.blockPosition().x, player.blockPosition().z) ?: continue
        val (region, scope) = regionAndScope
        val effects = getActiveEffects(region, player.uuid, scope)
        val duration = EFFECT_DURATION_SECONDS.value * 20
        for ((key, amplifier) in effects) {
            val effectEntry = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("minecraft:${key.effectId}")).orElse(null) ?: continue
            player.addEffect(MobEffectInstance(effectEntry, duration, amplifier.coerceIn(0, 255), true, false, true))
        }
    }
}