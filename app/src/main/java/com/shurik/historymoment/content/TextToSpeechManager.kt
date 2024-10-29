package com.shurik.historymoment.com.shurik.historymoment.content

import android.speech.tts.TextToSpeech
import android.content.Context

object TextToSpeechManager {
    private lateinit var tts: TextToSpeech
    private var isTTSInitialized = false
    private var isTTSPlaying = false

    fun initialize(context: Context) {
        if (!::tts.isInitialized) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTTSInitialized = true
                    // TODO: Установить язык и другие параметры
                }
            }
        }
    }

    fun speak(text: String) {
        if (isTTSInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts")
            isTTSPlaying = true
        }
    }

    fun stop() {
        if (isTTSPlaying) {
            tts.stop()
            isTTSPlaying = false
        }
    }

    fun toggle(text: String) {
        if (isTTSPlaying) {
            stop()
        } else {
            speak(text)
        }
    }
}
