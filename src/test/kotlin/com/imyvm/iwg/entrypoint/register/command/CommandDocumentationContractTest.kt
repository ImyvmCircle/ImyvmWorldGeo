package com.imyvm.iwg.inter.register.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.Bootstrap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommandDocumentationContractTest {
    private lateinit var root: CommandNode<CommandSourceStack>

    @BeforeTest
    fun registerCommandTree() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        register(dispatcher)
        root = assertNotNull(dispatcher.root.getChild("imyvmWorldGeo"))
    }

    @Test
    fun `creation grammar gets shape only from selection state`() {
        val create = root.getChild("create")
        assertNotNull(create.command)
        assertNotNull(create.getChild("name").command)
        assertNull(create.getChild("shapeType"))

        val addScope = root.getChild("addScope")
        val region = addScope.getChild("regionIdentifier")
        assertNotNull(region.command)
        assertNotNull(region.getChild("scopeName").command)
        assertNull(addScope.getChild("shapeType"))
    }

    @Test
    fun `selection and mutation scope commands remain separate paths`() {
        val selectionScope = root.getChild("select")
            .getChild("modifyScope")
            .getChild("regionIdentifier")
            .getChild("scopeName")
        assertNotNull(selectionScope.command)

        val mutationScope = root.getChild("modifyScope")
            .getChild("regionIdentifier")
            .getChild("scopeName")
        assertNotNull(mutationScope.command)
        assertNotNull(mutationScope.getChild("newName").command)
    }

    @Test
    fun `teleport reset and inquiry support only zero or complete target arguments`() {
        val teleportPoint = root.getChild("teleportPoint")
        listOf("reset", "inquiry").forEach { operation ->
            val node = teleportPoint.getChild(operation)
            assertNotNull(node.command)
            val region = node.getChild("regionIdentifier")
            assertNull(region.command)
            assertNotNull(region.getChild("scopeName").command)
        }
    }
}
