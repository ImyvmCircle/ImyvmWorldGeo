package com.imyvm.iwg

import com.imyvm.iwg.infra.LazyTicker
import com.imyvm.iwg.infra.LazyTicker.registerLazyTicker
import com.imyvm.iwg.infra.config.initializeConfigValidation
import com.imyvm.iwg.application.time.WorldGeoPeriodTracker
import com.imyvm.iwg.application.region.WorldGeoGeographicProfileSupport
import com.imyvm.iwg.application.region.effect.EffectOverlayService
import com.imyvm.iwg.domain.NaturalPeriodKind
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.inter.register.event.registerLocationDisplay
import com.imyvm.iwg.inter.register.event.registerPlayerGeographyPair
import com.imyvm.iwg.inter.register.event.registerPointSelection
import com.imyvm.iwg.inter.register.event.registerRegionEffects
import com.imyvm.iwg.inter.register.event.registerRegionEntryExit
import com.imyvm.iwg.inter.register.event.registerRegionPermissions
import com.imyvm.iwg.inter.register.event.registerPlayerStats
import com.imyvm.iwg.inter.register.event.registerSelectionDisplay
import com.imyvm.iwg.inter.register.command.register
import com.imyvm.iwg.entrypoint.register.registerDataLoadSave
import com.imyvm.iwg.domain.component.SelectionState
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class
ImyvmWorldGeo : ModInitializer {

	override fun onInitialize() {
		initializeConfigValidation()
		registerDataLoadSave()

		registerLazyTicker()
		var refreshGeographicProfiles = false
		WorldGeoPeriodTracker.registerCallback { transition ->
			if (transition.kind == NaturalPeriodKind.WEEK) refreshGeographicProfiles = true
		}
		LazyTicker.registerTask { WorldGeoPeriodTracker.process() }
		LazyTicker.registerTask { server ->
			if (refreshGeographicProfiles) {
				refreshGeographicProfiles = false
				WorldGeoGeographicProfileSupport.refreshAll(server, RegionDatabase.getRegionList())
			}
		}
		EffectOverlayService.register()
		registerPlayerGeographyPair()
		registerRegionEntryExit()
		registerPlayerStats()
		registerLocationDisplay()
		// UseBlockCallback stops at the first non-PASS result; active selection must run first.
		registerPointSelection()
		registerRegionPermissions()
		registerRegionEffects()

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> register(dispatcher) }
		registerSelectionDisplay()

		if (FabricLoader.getInstance().isModLoaded("dynmap")) {
			runCatching {
				val clazz = Class.forName("com.imyvm.iwg.infra.dynmap.DynmapIntegration")
				val instance = clazz.getDeclaredField("INSTANCE").get(null)
				clazz.getDeclaredMethod("registerIfLoaded").invoke(instance)
			}.onFailure { logger.error("Failed to initialize dynmap integration: ${it.message}") }
		}

		logger.info("$MOD_ID initialized successfully.")
	}

	companion object {
		const val MOD_ID = "imyvmworldgeo"
		val logger: Logger = LoggerFactory.getLogger(MOD_ID)

		val pointSelectingPlayers: ConcurrentHashMap<UUID, SelectionState> = ConcurrentHashMap()
		val locationActionBarEnabledPlayers: MutableSet<UUID> = Collections.synchronizedSet(mutableSetOf())
	}
}
