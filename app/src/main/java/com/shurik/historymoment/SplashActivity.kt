package com.shurik.historymoment


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.VideoView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import android.content.Intent
import android.net.Uri
import com.shurik.historymoment.R
import com.shurik.historymoment.db.DatabaseManager

class SplashActivity : AppCompatActivity() {
    private lateinit var videoView: VideoView
    private val dbManager = DatabaseManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        videoView = findViewById(R.id.splash_video_view)
        val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.splash_video}")
        videoView.setVideoURI(videoUri)
        videoView.start()

        // Запустите оба процесса асинхронно
        // Примите решение после завершения обоих
        initTasks()
    }

    private fun initTasks() {
        val dataLoaded = CompletableDeferred<Boolean>()
        val videoWatched = CompletableDeferred<Boolean>()

        // Инициализируем загрузку данных в фоне
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                dbManager.getData() // Загружаем данные
                dataLoaded.complete(true) // Отмечаем, что загрузка данных завершена
            } catch (e: Exception) {
                dataLoaded.complete(false) // В случае ошибки
            }
        }

        // Отслеживаем завершение видео
        videoView.setOnCompletionListener {
            videoWatched.complete(true) // Отмечаем, что просмотр завершён
        }

        // Переходим к MapsActivity после завершения обоих заданий
        lifecycleScope.launch {
            joinAll(
                async { dataLoaded.await() },
                async { videoWatched.await() }
            )
            // После завершения всех задач запускаем MapsActivity
            val intent = Intent(this@SplashActivity, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback() // Останавливаем воспроизведение и освобождаем ресурсы
    }
}