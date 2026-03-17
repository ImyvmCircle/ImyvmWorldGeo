package com.imyvm.iwg

import com.imyvm.iwg.infra.LazyTicker.registerLazyTicker
import com.imyvm.iwg.infra.dynmap.DynmapIntegration
import com.imyvm.iwg.inter.register.event.registerLocationDisplay
import com.imyvm.iwg.inter.register.event.registerPlayerGeographyPair
import com.imyvm.iwg.inter.register.event.registerPointSelection
import com.imyvm.iwg.inter.register.event.registerRegionEffects
import com.imyvm.iwg.inter.register.event.registerRegionEntryExit
import com.imyvm.iwg.inter.register.event.registerRegionPermissions
import com.imyvm.iwg.inter.register.event.registerSelectionDisplay
import com.imyvm.iwg.inter.register.command.register
import com.imyvm.iwg.entrypoint.register.registerDataLoadSave
import com.imyvm.iwg.domain.component.SelectionState
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class
ImyvmWorldGeo : ModInitializer {

	override fun onInitialize() {
		registerDataLoadSave()

		registerLazyTicker()
		registerPlayerGeographyPair()
		registerRegionEntryExit()
		registerLocationDisplay()
		registerRegionPermissions()
		registerRegionEffects()

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> register(dispatcher) }
		registerPointSelection()
		registerSelectionDisplay()

		DynmapIntegration.registerIfLoaded()

		logger.info("$MOD_ID initialized successfully.")
	}

	companion object {
		const val MOD_ID = "imyvmworldgeo"
		val logger: Logger = LoggerFactory.getLogger(MOD_ID)

		val pointSelectingPlayers: ConcurrentHashMap<UUID, SelectionState> = ConcurrentHashMap()
		val locationActionBarEnabledPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())
	}
}