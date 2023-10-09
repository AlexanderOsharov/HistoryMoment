package com.shurik.historymoment.module_moscowapi.additional_module

import com.google.gson.annotations.SerializedName

data class Attributes(
    @SerializedName("RouteName") val title: String,
    @SerializedName("Description") val description: String?
)