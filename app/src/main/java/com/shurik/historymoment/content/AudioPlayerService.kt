package com.shurik.historymoment.content

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.shurik.historymoment.R
import java.util.Locale

class AudioPlayerService : Service(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isTTSInitialized: Boolean = false
    private var isSpeaking = false

    override fun onCreate() {
        super.onCreate()
//        tts = TextToSpeech(this, this)
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                isTTSInitialized = true
            }
        }
        setTtsUtteranceListener()
        createNotificationChannel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTTSInitialized = true
            // Можно установить язык, если необходимо
            tts.language = Locale("ru")
        }
    }

    fun speakText(text: String) {
        if (isTTSInitialized) {
            if (isSpeaking) {
                tts.stop() // Останавливаем, если уже идет воспроизведение
            }
            isSpeaking = true
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            showNotification(text) // Показываем уведомление
        }
    }

    private fun showNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val stopIntent = Intent(this, AudioPlayerService::class.java).setAction("STOP")
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "history_moment_channel_id")
            .setContentTitle("Воспроизведение текста")
            .setContentText(text)
            .setSmallIcon(R.drawable.hm_icon)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.stop_icon,
                    "Остановить",
                    stopPendingIntent
                )
            )
            .build()

        startForeground(1, notification)
        notificationManager.notify(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SPEAK" -> {
                val textToSpeak = intent.getStringExtra("TEXT_TO_SPEAK") ?: return START_NOT_STICKY
                speakText(textToSpeak)
            }
            "STOP" -> {
                tts.stop()
                isSpeaking = false
                stopSelf() // Завершаем службу
                stopForeground(true)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "History Moment Playback"
            val channelDescription = "Канал уведомлений для озвучивания текста"
            val channel = NotificationChannel("history_moment_channel_id", channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = channelDescription
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun setTtsUtteranceListener() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Началась речь.
                isSpeaking = true
            }

            override fun onDone(p0: String?) {
                // Закончилась речь
                isSpeaking = false
            }

            override fun onError(utteranceId: String?) {
                // Произошла ошибка во время воспроизведения.
                isSpeaking = false
            }
        })
    }
}
