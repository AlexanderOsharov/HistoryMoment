package com.shurik.historymoment.module_moscowapi.additional_module

import com.google.gson.annotations.SerializedName

data class FeaturesResponse(
    @SerializedName("features") val features: List<Feature>
)
