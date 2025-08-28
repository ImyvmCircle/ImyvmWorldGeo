package com.imyvm.iwg.util


private const val MIN_RECTANGLE_AREA = 100.0
private const val MIN_SIDE_LENGTH = 10.0
private const val MIN_CIRCLE_RADIUS = 5.0
private const val MIN_POLYGON_AREA = 100.0
fun checkRectangleSize(width: Int, length: Int): Boolean {
    val area = width * length
    return width >= MIN_SIDE_LENGTH && length >= MIN_SIDE_LENGTH && area >= MIN_RECTANGLE_AREA
}

fun checkCircleSize(radius: Double) = radius >= MIN_CIRCLE_RADIUS
fun checkPolygonSize(area: Double) = area >= MIN_POLYGON_AREA