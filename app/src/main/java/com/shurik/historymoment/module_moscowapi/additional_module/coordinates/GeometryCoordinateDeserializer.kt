package com.shurik.historymoment.module_moscowapi.additional_module.coordinates

import android.util.Log
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class GeometryCoordinateDeserializer : JsonDeserializer<GeometryCoordinate> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GeometryCoordinate {
        val coordinates = json.asJsonArray

        // Проверяем, является ли первый элемент массивом
        return if (coordinates[0].isJsonArray) {
            // Мы имеем дело с множественными координатами.
            val pointList = mutableListOf<List<Coordinates>>()
            for (arrayElement in coordinates) {
                pointList.add(arrayElement.asJsonArray
                    .map { item -> Coordinates(item.asJsonArray[0].asDouble, item.asJsonArray[1].asDouble) })
            }
            Log.e("Coordinates: ", pointList.toString())
            GeometryCoordinate.MultiPoint(pointList)
        } else {
            // Мы имеем дело с одиночными координатами.
            GeometryCoordinate.Point(Coordinates(coordinates[0].asDouble, coordinates[1].asDouble))
        }
    }
}

