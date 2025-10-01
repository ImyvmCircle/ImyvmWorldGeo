package com.imyvm.iwg

import com.imyvm.iwg.inter.register.register
import com.imyvm.iwg.domain.PlayerRegionChecker
import com.imyvm.iwg.inter.register.registerLocationDisplay
import com.imyvm.iwg.inter.register.registerPlayerGeographyPair
import com.imyvm.iwg.inter.register.registerRegionPermissions
import com.imyvm.iwg.util.LazyTicker.registerLazyTicker
import com.imyvm.iwg.util.command.UseBlockCommandsHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ImyvmWorldGeo : ModInitializer {

	override fun onInitialize() {

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			register(dispatcher)
		}
		UseBlockCallback.EVENT.register(UseBlockCommandsHandler())

		dataLoad()
		dataSave()

		registerLazyTicker()

		registerPlayerGeographyPair()
		registerLocationDisplay()

		registerRegionPermissions()

		logger.info("$MOD_ID initialized successfully.")
	}

	companion object {
		const val MOD_ID = "imyvmworldgeo"
		val logger: Logger = LoggerFactory.getLogger(MOD_ID)

		val data: RegionDatabase = RegionDatabase()
		val playerRegionChecker: PlayerRegionChecker = PlayerRegionChecker()

		val pointSelectingPlayers: ConcurrentHashMap<UUID, MutableList<BlockPos>> = ConcurrentHashMap()
		val locationActionBarEnabledPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())

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
	}
}
