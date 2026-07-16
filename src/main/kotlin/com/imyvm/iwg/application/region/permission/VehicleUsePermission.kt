package com.imyvm.iwg.application.region.permission

import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.infra.config.PermissionConfig.PERMISSION_DEFAULT_RPG_VEHICLE_USE
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.world.entity.animal.camel.Camel
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.entity.monster.Strider
import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
import net.minecraft.world.InteractionResult

private fun isVehicleEntity(entity: net.minecraft.world.entity.Entity): Boolean {
    return entity is AbstractBoat || entity is AbstractMinecart || entity is AbstractHorse
        || entity is Pig || entity is Strider || entity is Camel
}

fun playerVehicleUsePermission() {
    UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
        if (!isVehicleEntity(entity)) return@register InteractionResult.PASS
        if (denyPermissionAt(player, world, entity.blockPosition(), PermissionKey.RPG_VEHICLE_USE,
                PERMISSION_DEFAULT_RPG_VEHICLE_USE.value, "setting.permission.vehicle_use")) return@register InteractionResult.CONSUME
        InteractionResult.PASS
    }
}
