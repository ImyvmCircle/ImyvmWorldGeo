package com.imyvm.iwg.inter.register.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.ArgumentCommandNode
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.commands.arguments.coordinates.Coordinates
import net.minecraft.server.Bootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeleportPointCommandArgumentTest {
    @Test
    fun `teleport point set command uses one vanilla block position argument`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        register(dispatcher)

        val setNode = dispatcher.root
            .getChild("imyvmWorldGeo")
            .getChild("teleportPoint")
            .getChild("set")
        assertNotNull(setNode.command)
        assertNull(setNode.getChild("x"))

        val positionNode = assertIs<ArgumentCommandNode<CommandSourceStack, *>>(setNode.getChild("pos"))
        assertIs<BlockPosArgument>(positionNode.type)
        assertNotNull(positionNode.command)
    }

    @Test
    fun `vanilla block position grammar accepts absolute world-relative and local-relative coordinates`() {
        val argument = BlockPosArgument.blockPos()

        val absolute = parse(argument, "1 -2 3")
        assertFalse(absolute.isXRelative)
        assertFalse(absolute.isYRelative)
        assertFalse(absolute.isZRelative)

        val worldRelative = parse(argument, "~0.5 ~1 ~-2.25")
        assertTrue(worldRelative.isXRelative)
        assertTrue(worldRelative.isYRelative)
        assertTrue(worldRelative.isZRelative)

        val localRelative = parse(argument, "^0.5 ^1 ^-2.25")
        assertTrue(localRelative.isXRelative)
        assertTrue(localRelative.isYRelative)
        assertTrue(localRelative.isZRelative)
    }

    @Test
    fun `vanilla block position command rejects partial malformed and trailing coordinates`() {
        val dispatcher = coordinateDispatcher()

        assertFailsWith<CommandSyntaxException> { dispatcher.execute("set 1 2", Unit) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("set invalid 2 3", Unit) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("set ~ ^ ~", Unit) }
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("set 1 2 3 trailing", Unit) }
    }

    @Test
    fun `legacy coordinate helper JVM method remains available`() {
        val helperClass = Class.forName(
            "com.imyvm.iwg.entrypoint.register.command.helper.CommandArgumentGetterKt"
        )
        helperClass.getMethod("getPosArgument", CommandContext::class.java, String::class.java)
    }

    private fun parse(argument: ArgumentType<Coordinates>, input: String): Coordinates {
        val command = "set $input"
        val dispatcher = coordinateDispatcher(argument)
        val result = dispatcher.parse(command, Unit)
        assertTrue(result.exceptions.isEmpty())
        assertEquals(command.length, result.reader.cursor)
        return result.context.build(command).getArgument("pos", Coordinates::class.java)
    }

    private fun coordinateDispatcher(
        argument: ArgumentType<Coordinates> = BlockPosArgument.blockPos()
    ): CommandDispatcher<Unit> = CommandDispatcher<Unit>().also { dispatcher ->
        dispatcher.register(
            LiteralArgumentBuilder.literal<Unit>("set").then(
                RequiredArgumentBuilder.argument<Unit, Coordinates>("pos", argument)
                    .executes { 1 }
            )
        )
    }
}
