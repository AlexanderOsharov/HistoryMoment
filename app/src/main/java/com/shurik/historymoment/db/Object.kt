package com.shurik.historymoment.db

//data class Coordinates(val latitude: Double? = 0.0, val longitude: Double? = 0.0)
data class MapPosition(val type: String? = "", val coordinates: List<Double>? = null)
data class Address(val fulladdress: String? = "", val mapPosition: MapPosition? = null)
data class Photo(val title: String? = "", var url: Any? = null, var urls: MutableList<String>? = null)

class Object(val address: Address? = null, val name: String? = "", val description: String? = "", val hash: String? = "", val photo: Photo? = null)
