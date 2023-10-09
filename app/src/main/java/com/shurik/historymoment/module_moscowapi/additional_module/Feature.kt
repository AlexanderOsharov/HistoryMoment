package com.shurik.historymoment.module_moscowapi.additional_module

import com.google.gson.annotations.SerializedName

data class Feature(
    @SerializedName("geometry") val geometry: Geometry,
    @SerializedName("properties") val properties: Properties
)
