package com.imyvm.iwg.util.geo
import com.imyvm.iwg.ModConfig.Companion.MIN_CIRCLE_RADIUS
import com.imyvm.iwg.ModConfig.Companion.MIN_POLYGON_AREA
import com.imyvm.iwg.ModConfig.Companion.MIN_RECTANGLE_AREA
import com.imyvm.iwg.ModConfig.Companion.MIN_SIDE_LENGTH
fun checkRectangleSize(width: Int, length: Int): Boolean {
    val area = width * length
    return width >= MIN_SIDE_LENGTH.value && length >= MIN_SIDE_LENGTH.value && area >= MIN_RECTANGLE_AREA.value
}

fun checkCircleSize(radius: Double) = radius >= MIN_CIRCLE_RADIUS.value
fun checkPolygonSize(area: Double) = area >= MIN_POLYGON_AREA.value

// TODO("polygon side length.")