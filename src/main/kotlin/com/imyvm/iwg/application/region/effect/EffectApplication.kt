package com.imyvm.iwg.application.region.effect

import com.imyvm.iwg.application.region.effect.helper.getResolvedActiveEffects
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.config.EffectConfig.EFFECT_DURATION_SECONDS
import com.imyvm.iwg.util.translator.getOnlinePlayers
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.resources.Identifier

fun applyRegionEffectsToPlayers(server: MinecraftServer) {
    for (player in getOnlinePlayers(server)) {
        val resolved = RegionDatabase.getRegionScopeSubSpaceAt(player.level(), player.blockPosition().x, player.blockPosition().z) ?: continue
        val (region, scope, subSpace) = resolved
        val effects = getResolvedActiveEffects(region, scope, subSpace, player.uuid)
        val duration = EFFECT_DURATION_SECONDS.value * 20
        for ((key, amplifier) in effects) {
            val effectEntry = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("minecraft:${key.effectId}")).orElse(null) ?: continue
            player.addEffect(MobEffectInstance(effectEntry, duration, amplifier, true, false, true))
        }
    }
}
