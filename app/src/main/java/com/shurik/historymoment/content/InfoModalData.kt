package com.shurik.historymoment.content

import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.Coordinates

class InfoModalData {
    var title: String = ""
    var coordinates: MutableList<Coordinates> = mutableListOf()
    var description: String = ""
    var images: MutableList<String> = mutableListOf()
    var type: String = "" // "Point" или "MultiPoint"
}

