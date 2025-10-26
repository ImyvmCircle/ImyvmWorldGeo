package com.imyvm.iwg.domain.component

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.util.text.Translator
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text

class GeoScope(
    var scopeName: String,
    var geoShape: GeoShape?,
    var settings: MutableList<Setting> = mutableListOf()
) {

    fun getScopeInfo(index: Int): Text? {
        val shapeInfoString = geoShape?.getShapeInfo()?.string ?: ""
        return Translator.tr("scope.info", index, scopeName, shapeInfoString)
    }

    fun getSettingInfos(server: MinecraftServer): List<Text> {
        return Region.formatSettings(server, settings, "scope.setting", scopeName)
    }
}