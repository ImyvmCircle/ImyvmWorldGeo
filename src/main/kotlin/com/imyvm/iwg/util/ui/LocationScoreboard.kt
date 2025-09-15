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

    val activeRegionScopes = mutableSetOf<String>()

    for ((uuid, regionScopePair) in ImyvmWorldGeo.playerRegionChecker.getAllRegionScopesWithPlayers()) {
        val regionName = regionScopePair.first.name.takeIf { it.isNotBlank() } ?: "-wilderness-"
        val scopeName = regionScopePair.second.scopeName.takeIf { it.isNotBlank() } ?: "-none-"

        val displayName = "$regionName \n $scopeName"
        activeRegionScopes.add(displayName)

        val scoreHolder: ScoreHolder = ScoreHolder.fromName(displayName)
        val score = scoreboard.getOrCreateScore(scoreHolder, objective)
        score.score = 0
    }

    val toRemove = mutableListOf<String>()
    for (score in scoreboard.getScoreboardEntries(objective)) {
        val name = score.owner
        if (!activeRegionScopes.contains(name)) {
            toRemove.add(name)
        }
    }

    toRemove.forEach { name ->
        val scoreHolder = ScoreHolder.fromName(name)
        scoreboard.removeScore(scoreHolder, objective)
    }

    scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective)
}
