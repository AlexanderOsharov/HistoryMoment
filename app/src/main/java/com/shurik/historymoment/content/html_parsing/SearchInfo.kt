package com.shurik.historymoment.content.html_parsing

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class SearchInfo {
    companion object {
        suspend fun getInfoFromYandex(searchInfo: String): InfoYandexContentData {
            return withContext(Dispatchers.IO) {
                val info = InfoYandexContentData()
                val searchResInfo = searchInfo.replace(" ", "+")
                val queryUrl = "https://ya.ru/search/?text=$searchResInfo"
                val results = Jsoup.connect(queryUrl).get().select(".organic__url").filter {
                    val href = it.attr("href")
                    !href.contains("yandex") && !href.contains("ya")
                }
                if (results.isNotEmpty()) {
                    val firstResult = results[0].attr("href")
                    Log.e("TagCheck", firstResult)
                    val document = Jsoup.connect(firstResult).get()
                    info.text = document.text()
                    document.select("img").forEach { image ->
                        var imageUrl = image.attr("src")
                        if (!imageUrl.startsWith("http")) {
                            imageUrl = firstResult + imageUrl
                        }
                        info.images.add(imageUrl)
                    }
                    document.select("video").forEach { video ->
                        val videoUrl = video.attr("src")
                        info.video.add(videoUrl)
                    }
                }

                return@withContext info
            }
        }
    }
}