package com.shurik.historymoment.module_moscowapi.additional_module.coordinates

sealed class GeometryCoordinate {
    data class Point(val coordinates: Coordinates) : GeometryCoordinate()
    data class MultiPoint(val coordinates: List<List<Coordinates>>) : GeometryCoordinate()
}
