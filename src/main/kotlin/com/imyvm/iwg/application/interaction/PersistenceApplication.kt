package com.imyvm.iwg.application.interaction

import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.application.region.WorldGeoGeographicProfileSupport
import com.imyvm.iwg.infra.RegionDatabase
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.level.ServerPlayer

internal fun saveRegionData(
    player: ServerPlayer? = null,
    save: () -> Unit = RegionDatabase::save
): Boolean = try {
    save()
    WorldGeoGeographicProfileSupport.invalidateAll("region_data_saved")
    true
} catch (error: Exception) {
    ImyvmWorldGeo.logger.error("Failed to persist region data: ${error.message}", error)
    if (player != null) Translator.tr("interaction.meta.persistence.save_failed")?.let(player::sendSystemMessage)
    false
}
