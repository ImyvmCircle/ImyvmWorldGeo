package com.imyvm.iwg

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class ImyvmWorldGeo : ModInitializer {

	override fun onInitialize() {
		dataLoad()

		dataSave()

		logger.info("Imyvm World Geo initialized.")
	}

	companion object {
		const val MOD_ID = "imyvm-world-geo"
		val logger: Logger = LoggerFactory.getLogger(MOD_ID)
		val data : RegionDatabase = RegionDatabase()

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