package com.imyvm.iwg.domain

import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShape
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainPresentationRuntimeCompatibilityTest {
    @Test
    fun `released presentation descriptors remain available`() {
        assertMethod(Region::class.java, "getScopeInfos", List::class.java, MinecraftServer::class.java)
        assertMethod(Region::class.java, "getSettingInfos", List::class.java, MinecraftServer::class.java)
        assertMethod(GeoScope::class.java, "getScopeInfo", Component::class.java, Int::class.javaPrimitiveType!!)
        assertMethod(GeoScope::class.java, "getSettingInfos", List::class.java, MinecraftServer::class.java)
        assertMethod(GeoShape::class.java, "getShapeInfo", Component::class.java)
        assertMethod(
            Region.Companion::class.java,
            "formatSettings",
            List::class.java,
            MinecraftServer::class.java,
            List::class.java,
            String::class.java,
            String::class.java
        )
        assertMethod(
            Region.Companion::class.java,
            "formatSettings\$default",
            List::class.java,
            Region.Companion::class.java,
            MinecraftServer::class.java,
            List::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType!!,
            Any::class.java
        )
    }

    @Test
    fun `released runtime descriptors remain available`() {
        assertMethod(GeoScope::class.java, "getWorld", ServerLevel::class.java, MinecraftServer::class.java)
        assertMethod(
            GeoScope::class.java,
            "certificateTeleportPoint",
            Boolean::class.javaPrimitiveType!!,
            Level::class.java,
            BlockPos::class.java
        )
        assertMethod(
            GeoScope::class.java,
            "getTeleportPointInvalidReasonKey",
            String::class.java,
            Level::class.java,
            BlockPos::class.java
        )
        assertMethod(
            GeoScope::class.java,
            "findNearestValidTeleportPoint",
            BlockPos::class.java,
            Level::class.java,
            BlockPos::class.java,
            Int::class.javaPrimitiveType!!
        )
        assertMethod(GeoShape::class.java, "generateTeleportPoint", BlockPos::class.java, Level::class.java)
        assertMethod(
            GeoShape::class.java,
            "certificateTeleportPoint",
            Boolean::class.javaPrimitiveType!!,
            Level::class.java,
            BlockPos::class.java
        )
        assertMethod(
            GeoShape::class.java,
            "getTeleportPointInvalidReasonKey",
            String::class.java,
            Level::class.java,
            BlockPos::class.java
        )
        assertMethod(
            GeoShape::class.java,
            "findNearestValidTeleportPoint",
            BlockPos::class.java,
            Level::class.java,
            BlockPos::class.java,
            Int::class.javaPrimitiveType!!
        )
        assertMethod(
            GeoShape.Companion::class.java,
            "isPhysicalSafe",
            Boolean::class.javaPrimitiveType!!,
            Level::class.java,
            BlockPos::class.java
        )
        assertMethod(
            GeoShape.Companion::class.java,
            "getPhysicalSafetyFailureReasonKey",
            String::class.java,
            Level::class.java,
            BlockPos::class.java
        )
    }

    private fun assertMethod(
        owner: Class<*>,
        name: String,
        returnType: Class<*>,
        vararg parameterTypes: Class<*>
    ) {
        val method = owner.getMethod(name, *parameterTypes)
        assertEquals(returnType, method.returnType, "${owner.name}.$name return type")
    }
}
