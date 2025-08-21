package com.imyvm.iwg

import com.imyvm.iwg.commands.register
import com.imyvm.iwg.region.RegionDatabase
import com.imyvm.iwg.useblock.UseBlockCommandsHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.scoreboard.ScoreHolder
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.scoreboard.ScoreboardCriterion
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import net.minecraft.scoreboard.ScoreboardObjective
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ImyvmWorldGeo : ModInitializer {

	override fun onInitialize() {

		CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, _ ->
			register(dispatcher, registryAccess)
		}
		UseBlockCallback.EVENT.register(UseBlockCommandsHandler())

		dataLoad()
		dataSave()

		ServerLifecycleEvents.SERVER_STARTED.register{
			server -> initializeGeographicScoreboard(server.scoreboard)
		}
		ServerTickEvents.END_SERVER_TICK.register{
			server ->
			tickCounter++
			updateGeographicScoreboardPlayers(server)
		}

		logger.info("Imyvm World Geo initialized.")
	}

	companion object {
		const val MOD_ID = "imyvm-world-geo"
		val logger: Logger = LoggerFactory.getLogger(MOD_ID)
		val data: RegionDatabase = RegionDatabase()

		private var tickCounter: Long = 0L

		val commandlySelectingPlayers: ConcurrentHashMap<UUID, MutableList<BlockPos>> = ConcurrentHashMap()

		fun dataLoad() {
			try {
				data.load()
			} catch (e: Exception) {
				logger.error("Failed to load region database: ${e.message}", e)
			}
		}

		fun dataSave() {
			ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
				try {
					data.save()
				} catch (e: Exception) {
					logger.error("Failed to save region database: ${e.message}", e)
				}
			}

		}

		fun initializeGeographicScoreboard(scoreboard: Scoreboard) {
			val objectName = "iwg_region"
			val displayName = Text.of("Current Region")

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
				newObjective.scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective)
				logger.info("Geographic scoreboard created and set for display.")
			} else {
				scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective)
				logger.info("Geographic scoreboard already exists and has been set for display.")
			}
		}

		fun updateGeographicScoreboardPlayers(server: net.minecraft.server.MinecraftServer) {
			if ((tickCounter % 20).toInt() == 0) {
				val scoreboard = server.scoreboard
				val objective: ScoreboardObjective = scoreboard.getNullableObjective("iwg_region")
					?: return

				for (player in server.playerManager.playerList) {
					val playerX = player.blockX
					val playerZ = player.blockZ

					val region = data.getRegionAt(playerX, playerZ)
					val regionName = region?.name ?: "-wilderness-"

					val scoreHolder: ScoreHolder = ScoreHolder.fromName(regionName)
					val score = scoreboard.getOrCreateScore(scoreHolder, objective)
					score.score = 0
				}
			}
		}
	}
}