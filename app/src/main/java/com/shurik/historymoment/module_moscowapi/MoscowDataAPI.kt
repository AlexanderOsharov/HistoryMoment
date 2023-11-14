package com.shurik.historymoment.module_moscowapi

import android.util.Log
import com.google.gson.GsonBuilder
import com.shurik.historymoment.SecretFile
import com.shurik.historymoment.module_moscowapi.additional_module.Feature
import com.shurik.historymoment.module_moscowapi.additional_module.FeaturesResponse
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.Coordinates
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.GeometryCoordinate
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.GeometryCoordinateDeserializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class MoscowDataAPI {
    private val apiKey = SecretFile.ACCESS_TOKEN_DATAMOS
    private val baseUrl = "https://apidata.mos.ru/v1"

    private val okHttpClient = OkHttpClient()

    fun getObjectsByCoordinates(latitude: Double, longitude: Double): List<Feature> {
        val url = "$baseUrl/datasets/2252/features?api_key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .build()

        val response: Response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw RuntimeException("No body found")
        Log.e("MoscowDataAPI", body)
        val gson = GsonBuilder()
            .registerTypeAdapter(GeometryCoordinate::class.java, GeometryCoordinateDeserializer())
            .create()

        return gson.fromJson(body, FeaturesResponse::class.java).features
    }


    private fun calculateDistance(
        coordinates: Coordinates,
        latitude: Double,
        longitude: Double
    ): Double {
        val latDiff = coordinates.latitude - latitude
        val lonDiff = coordinates.longitude - longitude
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff)
    }
}