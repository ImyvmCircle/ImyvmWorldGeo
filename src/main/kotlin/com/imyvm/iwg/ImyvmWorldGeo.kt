package com.imyvm.iwg

import com.imyvm.iwg.application.regionapp.PlayerRegionChecker
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.infra.LazyTicker.registerLazyTicker
import com.imyvm.iwg.inter.register.event.registerLocationDisplay
import com.imyvm.iwg.inter.register.event.registerPlayerGeographyPair
import com.imyvm.iwg.inter.register.event.registerPointSelection
import com.imyvm.iwg.inter.register.event.registerRegionPermissions
import com.imyvm.iwg.inter.register.command.register
import com.imyvm.iwg.inter.register.registerDataLoadSave
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ImyvmWorldGeo : ModInitializer {

	override fun onInitialize() {
		registerDataLoadSave()

		registerLazyTicker()
		registerPlayerGeographyPair()
		registerLocationDisplay()
		registerRegionPermissions()

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> register(dispatcher) }
		registerPointSelection()

		logger.info("$MOD_ID initialized successfully.")
	}

	companion object {
		const val MOD_ID = "imyvmworldgeo"
		val logger: Logger = LoggerFactory.getLogger(MOD_ID)

		val playerRegionChecker: PlayerRegionChecker = PlayerRegionChecker()

		val pointSelectingPlayers: ConcurrentHashMap<UUID, MutableList<BlockPos>> = ConcurrentHashMap()
		val locationActionBarEnabledPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())
	}
}