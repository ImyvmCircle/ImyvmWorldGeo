package com.imyvm.iwg.ui

import com.imyvm.iwg.ImyvmWorldGeo
import net.minecraft.scoreboard.*

fun initializeGeographicScoreboard(scoreboard: Scoreboard) {
    val objectName = "${ImyvmWorldGeo.MOD_ID}_region"
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
    val objective: ScoreboardObjective = scoreboard.getNullableObjective("${ImyvmWorldGeo.MOD_ID}_region") ?: return

    val activeRegions = mutableSetOf<String>()

    for ((uuid, region) in ImyvmWorldGeo.playerRegionChecker.getAllRegions()) {
        val regionName: String = region?.name?.takeIf { it.isNotBlank() }
            ?: Translator.tr("scoreboard.region.none.name").string ?: "-wilderness-"

        activeRegions.add(regionName)

        val scoreHolder: ScoreHolder = ScoreHolder.fromName(regionName)
        val score = scoreboard.getOrCreateScore(scoreHolder, objective)
        score.score = 0
    }

    val toRemove = mutableListOf<String>()
    for (score in scoreboard.getScoreboardEntries(objective)) {
        val playerName = score.owner
        if (!activeRegions.contains(playerName)) {
            toRemove.add(playerName)
        }
    }

    toRemove.forEach { playerName ->
        val scoreHolder = ScoreHolder.fromName(playerName)
        scoreboard.removeScore(scoreHolder, objective)
    }

    scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective)
}