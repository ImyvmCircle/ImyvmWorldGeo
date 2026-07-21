package com.imyvm.iwg.application.event

import com.imyvm.iwg.domain.WorldGeoBehaviorType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity

fun registerBehaviorEventProducers() {
    ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
        val player = source.entity as? ServerPlayer ?: return@register true
        recordEntityBehavior(WorldGeoBehaviorType.ENTITY_DAMAGE, player, entity)
        true
    }

    ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
        val player = source.entity as? ServerPlayer ?: return@register
        if (entity === player) return@register
        recordEntityBehavior(WorldGeoBehaviorType.ENTITY_KILL, player, entity)
    }

    UseItemCallback.EVENT.register { player, world, hand ->
        val serverPlayer = player as? ServerPlayer ?: return@register InteractionResult.PASS
        val stack = player.getItemInHand(hand)
        if (stack.isEmpty) return@register InteractionResult.PASS
        recordPlayerBehavior(
            WorldGeoBehaviorType.ITEM_USE,
            serverPlayer,
            world,
            player.blockPosition(),
            objectId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        )
        InteractionResult.PASS
    }
}

private fun recordEntityBehavior(type: WorldGeoBehaviorType, player: ServerPlayer, target: LivingEntity) {
    recordPlayerBehavior(
        type,
        player,
        target.level(),
        target.blockPosition(),
        objectId = BuiltInRegistries.ENTITY_TYPE.getKey(target.type).toString(),
        targetId = target.uuid.toString()
    )
}
