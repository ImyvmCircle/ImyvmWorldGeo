package com.imyvm.iwg

class Region {
    var name: String = ""
    var numberID: Int = 0
    lateinit var geometryScope: GeoScope


    companion object{
        class GeoScope
        {
            var scopeId: Int = 0
        }
    }
}