package com.imyvm.iwg.inter.register.command

import com.imyvm.iwg.application.interaction.*
import com.imyvm.iwg.domain.NaturalStatsCategory
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.SubSpace
import com.imyvm.iwg.entrypoint.register.command.helper.*
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.RegionNotFoundException
import com.imyvm.iwg.inter.register.command.helper.*
import com.imyvm.iwg.util.text.Translator
import net.minecraft.commands.Commands as MinecraftCommands
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer

fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
    dispatcher.register(
        literal("imyvmWorldGeo")
            .then(
                literal("select")
                    .then(
                        literal("start")
                            .executes { runStartSelect(it) }
                            .then(argument("shapeType", StringArgumentType.word()).suggests(SHAPE_TYPE_SUGGESTION_PROVIDER).executes { runStartSelectWithShape(it) })
                    )
                    .then(literal("stop").executes { runStopSelect(it) })
                    .then(
                        literal("reset")
                            .executes { runResetSelect(it) }
                            .then(argument("shapeType", StringArgumentType.word()).suggests(SHAPE_TYPE_SUGGESTION_PROVIDER).executes { runResetSelectWithShape(it) })
                    )
                    .then(
                        literal("shape")
                            .then(argument("shapeType", StringArgumentType.word()).suggests(SHAPE_TYPE_SUGGESTION_PROVIDER).executes { runSetSelectionShape(it) })
                    )
                    .then(
                        literal("modifyScope")
                            .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runStartSelectForModify(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("create")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .executes { runCreateRegion(it) }
                    .then(
                        argument("name", StringArgumentType.string())
                            .executes { runCreateRegion(it) }
                    )
            )
            .then(
                literal("delete")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runDeleteRegion(it) }
                    )
            )
            .then(
                literal("rename")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("newName", StringArgumentType.string())
                                    .executes { runRenameRegion(it) }
                            )
                    )
            )
            .then(
                literal("addScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runAddScope(it) }
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .executes { runAddScope(it) }
                            )
                    )
            )
            .then(
                literal("deleteScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runDeleteScope(it) }
                            )
                    )
            )
            .then(
                literal("teleportPoint")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("set")
                            .executes { runSetTeleportPoint(it) }
                            .then(
                                argument("x", StringArgumentType.word())
                                    .then(
                                        argument("y", StringArgumentType.word())
                                            .then(
                                                argument("z", StringArgumentType.word())
                                                    .executes { runSetTeleportPoint(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("reset")
                            .executes { runResetTeleportPointAtCurrentScope(it) }
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runResetTeleportPoint(it) }
                                    )
                            )
                    )
                    .then(
                        literal("inquiry")
                            .executes { runInquiryTeleportPointAtCurrentScope(it) }
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runInquiryTeleportPoint(it) }
                                    )
                            )
                    )
                    .then(
                        literal("teleport")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runTeleportPlayerAsAdministrator(it) }
                                    )
                            )
                    )
                    .then(
                        literal("toggle")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runToggleTeleportPointAccessibility(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("teleport")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runTeleportPlayerToRegion(it) }
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runTeleportPlayer(it) }
                            )
                    )
            )
            .then(
                literal("modifyScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runModifyScope(it) }
                                    .then(
                                        argument("newName", StringArgumentType.string())
                                            .executes { runRenameScope(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("transferScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("targetRegionIdentifier", StringArgumentType.string())
                                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                            .executes { runTransferScope(it) }
                                    )
                            )
                    )
            )
            .then(
                literal("mergeRegion")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("targetRegionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .executes { runMergeRegion(it) }
                            )
                    )
            )
            .then(
                literal("setting")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("add")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("key", StringArgumentType.string())
                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("value", StringArgumentType.string())
                                                    .executes { runAddDeleteSetting(it) }
                                                    .then(
                                                        argument("playerName", StringArgumentType.string())
                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                            .executes { runAddDeleteSetting(it) }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("remove")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("key", StringArgumentType.string())
                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                            .executes { runAddDeleteSetting(it) }
                                            .then(
                                                argument("playerName", StringArgumentType.string())
                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                    .executes { runAddDeleteSetting(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("queryValue")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("key", StringArgumentType.string())
                                            .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                            .executes { runQuerySettingValue(it) }
                                            .then(
                                                argument("playerName", StringArgumentType.string())
                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                    .executes { runQuerySettingValue(it) }
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("settingScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("add")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .then(
                                                        argument("value", StringArgumentType.string())
                                                            .executes { runAddDeleteSetting(it) }
                                                            .then(
                                                                argument("playerName", StringArgumentType.string())
                                                                    .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                                    .executes { runAddDeleteSetting(it) }
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("remove")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .executes { runAddDeleteSetting(it) }
                                                    .then(
                                                        argument("playerName", StringArgumentType.string())
                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                            .executes { runAddDeleteSetting(it) }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("queryValue")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("key", StringArgumentType.string())
                                                    .suggests(SETTING_KEY_SUGGESTION_PROVIDER)
                                                    .executes { runQuerySettingValue(it) }
                                                    .then(
                                                        argument("playerName", StringArgumentType.string())
                                                            .suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER)
                                                            .executes { runQuerySettingValue(it) }
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                literal("subspace")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("select")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runStartSelectForSubSpace(it) }
                                            .then(
                                                argument("shapeType", StringArgumentType.word())
                                                    .suggests(SHAPE_TYPE_SUGGESTION_PROVIDER)
                                                    .executes { runStartSelectForSubSpace(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("create")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("scopeName", StringArgumentType.string())
                                            .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("subSpaceName", StringArgumentType.string())
                                                    .executes { runCreateSubSpace(it) }
                                                    .then(
                                                        argument("shapeType", StringArgumentType.word())
                                                            .suggests(SHAPE_TYPE_SUGGESTION_PROVIDER)
                                                            .executes { runCreateSubSpace(it) }
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("delete")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("subSpaceName", StringArgumentType.string())
                                            .suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runDeleteSubSpace(it) }
                                    )
                            )
                    )
                    .then(
                        literal("rename")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("subSpaceName", StringArgumentType.string())
                                            .suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("newName", StringArgumentType.string())
                                                    .executes { runRenameSubSpace(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("replaceShape")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("subSpaceName", StringArgumentType.string())
                                            .suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runReplaceSubSpaceShape(it) }
                                            .then(
                                                argument("shapeType", StringArgumentType.word())
                                                    .suggests(SHAPE_TYPE_SUGGESTION_PROVIDER)
                                                    .executes { runReplaceSubSpaceShape(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("setEntryMessage")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("subSpaceName", StringArgumentType.string())
                                            .suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER)
                                            .then(
                                                argument("message", StringArgumentType.greedyString())
                                                    .executes { runSetSubSpaceEntryMessage(it) }
                                            )
                                    )
                            )
                    )
                    .then(
                        literal("clearEntryMessage")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("subSpaceName", StringArgumentType.string())
                                            .suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runClearSubSpaceEntryMessage(it) }
                                    )
                            )
                    )
                    .then(
                        literal("query")
                            .then(
                                argument("regionIdentifier", StringArgumentType.string())
                                    .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                                    .then(
                                        argument("subSpaceName", StringArgumentType.string())
                                            .suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER)
                                            .executes { runQuerySubSpace(it) }
                                    )
                            )
                    )
                    .then(
                        literal("tag")
                            .then(literal("add").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("tag", StringArgumentType.string()).executes { runAddSubSpaceStringTag(it) }))))
                            .then(literal("remove").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("tag", StringArgumentType.string()).executes { runRemoveSubSpaceStringTag(it) }))))
                            .then(literal("put").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("key", StringArgumentType.string()).then(argument("value", StringArgumentType.string()).executes { runPutSubSpaceKeyedTag(it) })))))
                            .then(literal("removeKey").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("key", StringArgumentType.string()).executes { runRemoveSubSpaceKeyedTag(it) }))))
                    )
            )
            .then(
                literal("debug")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(literal("spaceHere").executes { runDebugSpaceHere(it) })
                    .then(literal("time").executes { runDebugTime(it) })
                    .then(
                        literal("behavior")
                            .then(literal("emit").executes { runDebugBehaviorEmit(it) })
                            .then(literal("recent").executes { runDebugBehaviorRecent(it) })
                            .then(literal("stats").executes { runDebugBehaviorStats(it) })
                    )
                    .then(literal("validateSubspaces").executes { runValidateSubSpaces(it) })
                    .then(
                        literal("spaceSnapshot")
                            .executes { runDebugCurrentSpaceSnapshot(it) }
                            .then(literal("region").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).executes { runDebugRegionSpaceSnapshot(it) }))
                            .then(literal("scope").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("scopeName", StringArgumentType.string()).suggests(SCOPE_NAME_SUGGESTION_PROVIDER).executes { runDebugScopeSpaceSnapshot(it) })))
                            .then(literal("subspace").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).executes { runDebugSubSpaceSnapshot(it) })))
                    )
                    .then(
                        literal("settingSummaries")
                            .then(literal("region").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).executes { runDebugRegionSettingSummaries(it) }))
                            .then(literal("scope").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("scopeName", StringArgumentType.string()).suggests(SCOPE_NAME_SUGGESTION_PROVIDER).executes { runDebugScopeSettingSummaries(it) })))
                            .then(literal("subspace").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).executes { runDebugSubSpaceSettingSummaries(it) })))
                    )
                    .then(
                        literal("sendSpaceMessage")
                            .then(literal("region").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("message", StringArgumentType.greedyString()).executes { runDebugSendRegionSpaceMessage(it) })))
                            .then(literal("scope").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("scopeName", StringArgumentType.string()).suggests(SCOPE_NAME_SUGGESTION_PROVIDER).then(argument("message", StringArgumentType.greedyString()).executes { runDebugSendScopeSpaceMessage(it) }))))
                            .then(literal("subspace").then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("message", StringArgumentType.greedyString()).executes { runDebugSendSubSpaceMessage(it) }))))
                    )
                    .then(
                        literal("region")
                            .then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).executes { runDebugRegion(it) })
                    )
                    .then(
                        literal("scope")
                            .then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("scopeName", StringArgumentType.string()).suggests(SCOPE_NAME_SUGGESTION_PROVIDER).executes { runDebugScope(it) }))
                    )
                    .then(
                        literal("subspace")
                            .then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).executes { runDebugSubSpace(it) }))
                    )
            )
            .then(
                literal("settingSubSpace")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        literal("add")
                            .then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("key", StringArgumentType.string()).suggests(SETTING_KEY_SUGGESTION_PROVIDER).then(argument("value", StringArgumentType.string()).executes { runAddDeleteSubSpaceSetting(it) }.then(argument("playerName", StringArgumentType.string()).suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER).executes { runAddDeleteSubSpaceSetting(it) })))))
                    )
                    .then(
                        literal("remove")
                            .then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("key", StringArgumentType.string()).suggests(SETTING_KEY_SUGGESTION_PROVIDER).executes { runAddDeleteSubSpaceSetting(it) }.then(argument("playerName", StringArgumentType.string()).suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER).executes { runAddDeleteSubSpaceSetting(it) }))))
                    )
                    .then(
                        literal("queryValue")
                            .then(argument("regionIdentifier", StringArgumentType.string()).suggests(REGION_NAME_SUGGESTION_PROVIDER).then(argument("subSpaceName", StringArgumentType.string()).suggests(SUBSPACE_NAME_SUGGESTION_PROVIDER).then(argument("key", StringArgumentType.string()).suggests(SETTING_KEY_SUGGESTION_PROVIDER).executes { runQuerySubSpaceSettingValue(it) }.then(argument("playerName", StringArgumentType.string()).suggests(ONLINE_PLAYER_SUGGESTION_PROVIDER).executes { runQuerySubSpaceSettingValue(it) }))))
                    )
            )
            .then(
                literal("dynmapToggle")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runToggleRegionDynmap(it) }
                    )
            )
            .then(
                literal("dynmapToggleScope")
                    .requires(MinecraftCommands.hasPermission(MinecraftCommands.LEVEL_GAMEMASTERS))
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .then(
                                argument("scopeName", StringArgumentType.string())
                                    .suggests(SCOPE_NAME_SUGGESTION_PROVIDER)
                                    .executes { runToggleScopeDynmap(it) }
                            )
                    )
            )
            .then(
                literal("query")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runQueryRegion(it) }
                    )
            )
            .then(
                literal("stats")
                    .then(
                        argument("regionIdentifier", StringArgumentType.string())
                            .suggests(REGION_NAME_SUGGESTION_PROVIDER)
                            .executes { runQueryRegionStats(it) }
                            .then(
                                argument("category", StringArgumentType.word())
                                    .suggests(NATURAL_STATS_CATEGORY_SUGGESTION_PROVIDER)
                                    .executes { runQueryRegionStats(it) }
                            )
                    )
            )
            .then(literal("list").executes { runListRegions(it) })
            .then(literal("toggle").executes { runToggleActionBar(it) })
            .then(literal("help").executes { runHelp(it) })
    )
}

private fun runStartSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onStartSelection(player)
}

private fun runStopSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onStopSelection(player)
}

private fun runResetSelect(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onResetSelection(player)
}

private fun runStartSelectWithShape(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val shapeTypeStr = StringArgumentType.getString(context, "shapeType").uppercase()
    val shapeType = parseShapeType(shapeTypeStr, player) ?: return 0
    return onStartSelection(player, shapeType)
}

private fun runResetSelectWithShape(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val shapeTypeStr = StringArgumentType.getString(context, "shapeType").uppercase()
    val shapeType = parseShapeType(shapeTypeStr, player) ?: return 0
    return onResetSelection(player, shapeType)
}

private fun runSetSelectionShape(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val shapeTypeStr = StringArgumentType.getString(context, "shapeType").uppercase()
    val shapeType = parseShapeType(shapeTypeStr, player) ?: return 0
    return onSetSelectionShape(player, shapeType)
}

private fun parseShapeType(shapeTypeStr: String, player: ServerPlayer): GeoShapeType? {
    val shapeType = GeoShapeType.entries.find { it.name == shapeTypeStr } ?: GeoShapeType.UNKNOWN
    if (shapeType == GeoShapeType.UNKNOWN) {
        player.sendSystemMessage(Translator.tr("interaction.meta.create.invalid_shape", shapeTypeStr)!!)
        return null
    }
    return shapeType
}

private fun runCreateRegion(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    val nameArg = getOptionalArgument(context, "name")
    return onRegionCreation(player, nameArg, null, idMark = 0)
}

private fun runDeleteRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToDelete -> onRegionDelete(player, regionToDelete) }
}

private fun runRenameRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val newName = context.getArgument("newName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToRename -> onRegionRename(player, regionToRename, newName) }
}

private fun runAddScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeNameArg = getOptionalArgument(context, "scopeName")
    return identifierHandler(regionIdentifier, player) { regionToAddScope -> onScopeCreation(player, regionToAddScope, scopeNameArg, null)}
}

private fun runDeleteScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToDeleteScope -> onScopeDelete(player, regionToDeleteScope, scopeName)}
}

private fun runRenameScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    val newName = context.getArgument("newName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToRenameScope ->
        onScopeRename(player, regionToRenameScope, scopeName, newName)}
}

private fun runTransferScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    val targetRegionIdentifier = context.getArgument("targetRegionIdentifier", String::class.java)
    return identifierHandler(regionIdentifier, player) { sourceRegion ->
        identifierHandler(targetRegionIdentifier, player) { targetRegion ->
            onScopeTransfer(player, sourceRegion, scopeName, targetRegion)
        }
    }
}

private fun runMergeRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val targetRegionIdentifier = context.getArgument("targetRegionIdentifier", String::class.java)
    return identifierHandler(regionIdentifier, player) { sourceRegion ->
        identifierHandler(targetRegionIdentifier, player) { targetRegion ->
            onRegionMerge(player, sourceRegion, targetRegion)
        }
    }
}

private fun runSetTeleportPoint(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    var x = getPosArgument(context, "x")
    var y = getPosArgument(context, "y")
    var z = getPosArgument(context, "z")
    if (x == null || y == null || z == null) {
        x = player.blockPosition().x
        y = player.blockPosition().y
        z = player.blockPosition().z
    }

    val regionScopePair = RegionDatabase.getRegionAndScopeAt(player.level(),x,z)
    if (regionScopePair == null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.no_region")!!)
        return 0
    }

    return onAddingTeleportPoint(player, regionScopePair.first, regionScopePair.second, x, y, z)
}

private fun runResetTeleportPoint(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    return onResettingTeleportPoint(player, region, scope)
}

private fun runResetTeleportPointAtCurrentScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getCurrentPlayerRegionScope(context) ?: return 0
    return onResettingTeleportPoint(player, region, scope)
}

private fun runInquiryTeleportPoint(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    return inquireTeleportPoint(player, region, scope)
}

private fun runInquiryTeleportPointAtCurrentScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getCurrentPlayerRegionScope(context) ?: return 0
    return inquireTeleportPoint(player, region, scope)
}

private fun inquireTeleportPoint(player: ServerPlayer, region: Region, scope: GeoScope): Int {
    val teleportPoint = onGettingTeleportPoint(scope)
    return if (teleportPoint != null) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.inquiry.result",
            teleportPoint.x, teleportPoint.y, teleportPoint.z,
            scope.scopeName,
            region)!!)
        1
    } else {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.inquiry.no_point",
            scope.scopeName,
            region)!!)
        0
    }
}

private fun runTeleportPlayer(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToTeleport ->
        val scope = getScopeOrNotify(player, regionToTeleport, scopeName) ?: return@identifierHandler
        onTeleportingPlayer(player, regionToTeleport, scope)
    }
}

private fun runTeleportPlayerAsAdministrator(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { region ->
        val scope = getScopeOrNotify(player, region, scopeName) ?: return@identifierHandler
        onTeleportingPlayerAsAdministrator(player, region, scope)
    }
}

private fun runTeleportPlayerToRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToTeleport ->
        val scope = findPublicTeleportScope(regionToTeleport)
        if (scope != null) {
            onTeleportingPlayer(player, regionToTeleport, scope)
        } else {
            player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.no_public_scope", regionToTeleport.name)!!)
            return@identifierHandler
        }
    }
}

private fun runToggleTeleportPointAccessibility(context: CommandContext<CommandSourceStack>): Int{
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    val result = onTogglingTeleportPointAccessibility(player, region, scope)
    if (result == 1) {
        player.sendSystemMessage(Translator.tr("interaction.meta.scope.teleport_point.toggle", region.name, scope.scopeName)!!)
    }
    return result
}

private fun runModifyScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { regionToModifyScope -> onModifyScope(player, regionToModifyScope, scopeName)}
}

private fun runAddDeleteSetting(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = getOptionalArgument(context, "scopeName")
    val keyString = context.getArgument("key", String::class.java)
    val valueString = getOptionalArgument(context, "value")
    val targetPlayer = getOptionalArgument(context, "playerName")
    return identifierHandler(regionIdentifier, player) { region ->
        val scope = scopeName?.let { getScopeOrNotify(player, region, it) ?: return@identifierHandler }
        if (scope != null && valueString != null) {
            addScopeSetting(player, region, scope, keyString, valueString, targetPlayer)
        } else if (scope != null) {
            removeScopeSetting(player, region, scope, keyString, targetPlayer)
        } else if (valueString != null) {
            addRegionSetting(player, region, keyString, valueString, targetPlayer)
        } else {
            removeRegionSetting(player, region, keyString, targetPlayer)
        }
    }
}

private fun runQuerySettingValue(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = getOptionalArgument(context, "scopeName")
    val keyString = context.getArgument("key", String::class.java)
    val targetPlayer = getOptionalArgument(context, "playerName")
    return identifierHandler(regionIdentifier, player) { region ->
        val scope = scopeName?.let { getScopeOrNotify(player, region, it) ?: return@identifierHandler }
        onQuerySettingValue(player, region, scope, keyString, targetPlayer)
    }
}

private fun runQueryRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { regionToQuery -> onQueryRegion(player, regionToQuery, false) }
}

private fun runQueryRegionStats(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val categoryName = getOptionalArgument(context, "category")
    val category = NaturalStatsCategory.fromName(categoryName)
    if (category == null) {
        player.sendSystemMessage(
            Translator.tr(
                "interaction.meta.stats.error.invalid_category",
                categoryName,
                NaturalStatsCategory.entries.joinToString(", ") { it.commandName }
            )!!
        )
        return 0
    }
    return identifierHandler(regionIdentifier, player) { regionToQuery ->
        onQueryRegionNaturalStats(player, regionToQuery, category, false)
    }
}

private fun runListRegions(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onListRegions(player)
}

private fun runToggleActionBar(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onToggleActionBar(player)
}

private fun runHelp(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onHelp(player)
}

private fun runStartSelectForModify(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val scopeName = context.getArgument("scopeName", String::class.java)
    return identifierHandler(regionIdentifier, player) { region ->
        val scope = getScopeOrNotify(player, region, scopeName) ?: return@identifierHandler
        onStartSelectionForModify(player, scope)
    }
}

private fun runAddDeleteSubSpaceSetting(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val keyString = context.getArgument("key", String::class.java)
    val valueString = getOptionalArgument(context, "value")
    val targetPlayer = getOptionalArgument(context, "playerName")
    if (valueString != null) {
        addSubSpaceSetting(target.player, target.region, target.scope, target.subSpace, keyString, valueString, targetPlayer)
    } else {
        removeSubSpaceSetting(target.player, target.region, target.scope, target.subSpace, keyString, targetPlayer)
    }
    return 1
}

private fun runQuerySubSpaceSettingValue(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val keyString = context.getArgument("key", String::class.java)
    val targetPlayer = getOptionalArgument(context, "playerName")
    onQuerySettingValue(target.player, target.region, target.scope, keyString, targetPlayer, target.subSpace)
    return 1
}

private fun runToggleRegionDynmap(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { region -> onTogglingRegionDynmap(player, region) }
}

private fun runToggleScopeDynmap(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    return onTogglingScopeDynmap(player, region, scope)
}

private data class CommandSubSpaceTarget(
    val player: ServerPlayer,
    val region: Region,
    val scope: GeoScope,
    val subSpace: SubSpace
)


private fun runStartSelectForSubSpace(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    val shapeType = getOptionalArgument(context, "shapeType")?.uppercase()?.let { parseShapeType(it, player) ?: return 0 }
    return onStartSelectionForSubSpace(player, region, scope, shapeType)
}

private fun runCreateSubSpace(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    val subSpaceName = context.getArgument("subSpaceName", String::class.java)
    val shapeType = getOptionalArgument(context, "shapeType")?.uppercase()?.let { parseShapeType(it, player) ?: return 0 }
    return onSubSpaceCreationFromSelection(player, region, scope, subSpaceName, shapeType)
}

private fun runDeleteSubSpace(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    return onSubSpaceDelete(target.player, target.region, target.scope, target.subSpace)
}

private fun runRenameSubSpace(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val newName = context.getArgument("newName", String::class.java)
    return onSubSpaceRename(target.player, target.region, target.scope, target.subSpace, newName)
}

private fun runReplaceSubSpaceShape(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val shapeType = getOptionalArgument(context, "shapeType")?.uppercase()?.let { parseShapeType(it, target.player) ?: return 0 }
    return onSubSpaceShapeReplacementFromSelection(target.player, target.region, target.scope, target.subSpace, shapeType)
}

private fun runSetSubSpaceEntryMessage(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val message = context.getArgument("message", String::class.java)
    return onSettingSubSpaceEntryMessage(target.player, target.region, target.scope, target.subSpace, message)
}

private fun runClearSubSpaceEntryMessage(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    return onSettingSubSpaceEntryMessage(target.player, target.region, target.scope, target.subSpace, null)
}

private fun runQuerySubSpace(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    return onQuerySubSpace(target.player, target.region, target.scope, target.subSpace)
}

private fun runAddSubSpaceStringTag(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val tag = context.getArgument("tag", String::class.java)
    return onAddingSubSpaceStringTag(target.player, target.region, target.scope, target.subSpace, tag)
}

private fun runRemoveSubSpaceStringTag(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val tag = context.getArgument("tag", String::class.java)
    return onRemovingSubSpaceStringTag(target.player, target.region, target.scope, target.subSpace, tag)
}

private fun runPutSubSpaceKeyedTag(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val key = context.getArgument("key", String::class.java)
    val value = context.getArgument("value", String::class.java)
    return onPuttingSubSpaceKeyedTag(target.player, target.region, target.scope, target.subSpace, key, value)
}

private fun runRemoveSubSpaceKeyedTag(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val key = context.getArgument("key", String::class.java)
    return onRemovingSubSpaceKeyedTag(target.player, target.region, target.scope, target.subSpace, key)
}

private fun runDebugTime(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onDebugTime(player)
}

private fun runDebugSpaceHere(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onDebugCurrentSpace(player)
}

private fun runDebugBehaviorEmit(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onDebugBehaviorEmit(player)
}

private fun runDebugBehaviorRecent(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onDebugBehaviorRecent(player)
}

private fun runDebugBehaviorStats(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onDebugBehaviorStats(player)
}

private fun runValidateSubSpaces(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onValidateSubSpaces(player)
}

private fun runDebugRegion(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { region -> onDebugRegion(player, region) }
}

private fun runDebugScope(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    return onDebugScope(player, region, scope)
}

private fun runDebugSubSpace(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    return onDebugSubSpace(target.player, target.region, target.scope, target.subSpace)
}

private fun runDebugCurrentSpaceSnapshot(context: CommandContext<CommandSourceStack>): Int {
    val player = context.source.player ?: return 0
    return onDebugCurrentSpaceSnapshot(player)
}

private fun runDebugRegionSpaceSnapshot(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { region -> onDebugRegionSpaceSnapshot(player, region) }
}

private fun runDebugScopeSpaceSnapshot(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    return onDebugScopeSpaceSnapshot(player, region, scope)
}

private fun runDebugSubSpaceSnapshot(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    return onDebugSubSpaceSnapshot(target.player, target.region, target.scope, target.subSpace)
}

private fun runDebugRegionSettingSummaries(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    return identifierHandler(regionIdentifier, player) { region -> onDebugRegionSettingSummaries(player, region) }
}

private fun runDebugScopeSettingSummaries(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    return onDebugScopeSettingSummaries(player, region, scope)
}

private fun runDebugSubSpaceSettingSummaries(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    return onDebugSubSpaceSettingSummaries(target.player, target.region, target.scope, target.subSpace)
}

private fun runDebugSendRegionSpaceMessage(context: CommandContext<CommandSourceStack>): Int {
    val (player, regionIdentifier) = getPlayerRegionPair(context) ?: return 0
    val message = context.getArgument("message", String::class.java)
    return identifierHandler(regionIdentifier, player) { region -> onDebugSendRegionSpaceMessage(player, region, message) }
}

private fun runDebugSendScopeSpaceMessage(context: CommandContext<CommandSourceStack>): Int {
    val (player, region, scope) = getExplicitPlayerRegionScope(context) ?: return 0
    val message = context.getArgument("message", String::class.java)
    return onDebugSendScopeSpaceMessage(player, region, scope, message)
}

private fun runDebugSendSubSpaceMessage(context: CommandContext<CommandSourceStack>): Int {
    val target = getSubSpaceTarget(context) ?: return 0
    val message = context.getArgument("message", String::class.java)
    return onDebugSendSubSpaceMessage(target.player, target.region, target.scope, target.subSpace, message)
}

private fun getSubSpaceTarget(context: CommandContext<CommandSourceStack>): CommandSubSpaceTarget? {
    val player = context.source.player ?: return null
    val regionIdentifier = context.getArgument("regionIdentifier", String::class.java)
    val subSpaceName = context.getArgument("subSpaceName", String::class.java)
    val region = try {
        resolveRegionIdentifier(regionIdentifier)
    } catch (e: RegionNotFoundException) {
        notifyRegionNotFound(player, regionIdentifier)
        return null
    }
    val (scope, subSpace) = RegionDatabase.getSubSpaceByName(region, subSpaceName) ?: run {
        player.sendSystemMessage(Translator.tr("region.error.no_subspace", subSpaceName, region.name)!!)
        return null
    }
    return CommandSubSpaceTarget(player, region, scope, subSpace)
}
