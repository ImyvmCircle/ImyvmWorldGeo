package com.imyvm.iwg.util.ui

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.scoreboard.*

fun initializeGeographicScoreboard(scoreboard: Scoreboard) {
    val objectName = "${ImyvmWorldGeo.MOD_ID}_region_scope"
    val displayName = Translator.tr("scoreboard.display-name")

    val objective = scoreboard.getNullableObjective(objectName)

    if (objective == null) {
        val newObjective = scoreboard.addObjective(
            objectName,
            ScoreboardCriterion.DUMMY,
            displayName,
            ScoreboardCriterion.RenderType.INTEGER,
            true,
            null
        )
        newObjective.scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, newObjective)
        ImyvmWorldGeo.logger.info("Geographic scoreboard created and set for display.")
    } else {
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective)
        ImyvmWorldGeo.logger.info("Geographic scoreboard already exists and has been set for display.")
    }
}

fun updateGeographicScoreboardPlayers(server: net.minecraft.server.MinecraftServer) {
    if ((ImyvmWorldGeo.tickCounter % 20).toInt() != 0) return

    val scoreboard = server.scoreboard ?: return
    val objective: ScoreboardObjective =
        scoreboard.getNullableObjective("${ImyvmWorldGeo.MOD_ID}_region_scope") ?: return

    val activeEntries = mutableSetOf<String>()

    for ((uuid, regionScopePair) in ImyvmWorldGeo.playerRegionChecker.getAllRegionScopesWithPlayers()) {
        val region = regionScopePair.first
        val scope = regionScopePair.second

        val regionPrefix = Translator.tr("scoreboard.region.prefix")?.string ?: "Region:"
        val scopePrefix = Translator.tr("scoreboard.scope.prefix")?.string  ?: "Scope:"

        val regionName = region?.name?.takeIf { it.isNotBlank() }
            ?: Translator.tr("scoreboard.region.none.name")?.string  ?: "-wilderness-"

        val scopeName = scope?.scopeName?.takeIf { it.isNotBlank() }
            ?: Translator.tr("scoreboard.scope.none.name")?.string  ?: "-Free to use-"

        val regionLine = if (regionName == Translator.tr("scoreboard.region.none.name")?.string)
            regionName
        else "$regionPrefix $regionName"

        val scopeLine = if (scopeName == Translator.tr("scoreboard.scope.none.name")?.string)
            scopeName
        else "$scopePrefix $scopeName"



        for (line in listOf(regionLine, scopeLine)) {
            activeEntries.add(line)
            val scoreHolder: ScoreHolder = ScoreHolder.fromName(line)
            val score = scoreboard.getOrCreateScore(scoreHolder, objective)
            score.score = 0
        }
    }

    val toRemove = mutableListOf<String>()
    for (score in scoreboard.getScoreboardEntries(objective)) {
        val name = score.owner
        if (!activeEntries.contains(name)) {
            toRemove.add(name)
        }
    }

    toRemove.forEach { name ->
        val scoreHolder = ScoreHolder.fromName(name)
        scoreboard.removeScore(scoreHolder, objective)
    }

    scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective)
}