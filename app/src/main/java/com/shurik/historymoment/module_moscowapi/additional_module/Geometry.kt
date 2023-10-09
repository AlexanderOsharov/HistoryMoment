package com.shurik.historymoment.module_moscowapi.additional_module

import com.google.gson.annotations.SerializedName
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.GeometryCoordinate

data class Geometry(
    @SerializedName("coordinates") val coordinates: GeometryCoordinate
)

