package com.shurik.historymoment.module_moscowapi.additional_module

import com.google.gson.annotations.SerializedName

data class Properties(
    @SerializedName("DatasetId") val dataSetId: Int,
    @SerializedName("Attributes") val attributes: Attributes
)
