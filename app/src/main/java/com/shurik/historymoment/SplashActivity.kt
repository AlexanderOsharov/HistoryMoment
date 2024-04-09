package com.shurik.historymoment



import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import android.content.Intent
import com.bumptech.glide.Glide
import com.shurik.historymoment.R
import com.shurik.historymoment.db.DatabaseManager

class SplashActivity : AppCompatActivity() {
    private lateinit var splashImageView: ImageView
    private val dbManager = DatabaseManager()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        splashImageView = findViewById(R.id.splash_image_view)
        val gifResourceID = R.raw.splash_gif // Замените на ваш ресурс GIF

        // Загрузка и отображение GIF с помощью библиотеки Glide
        Glide.with(this)
//            .asGif()
            .load(R.drawable.splash_image_view)
            .into(splashImageView)

        // Запуск процессов асинхронно и переход к MapsActivity после их завершения
        initTasks()
    }

    private fun initTasks() {
        val dataLoaded = CompletableDeferred<Boolean>()
        val delayedTransition = CompletableDeferred<Boolean>()

        // Инициализация загрузки данных в фоне
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                dbManager.getData() // Загрузка данных
                dataLoaded.complete(true) // Отметка о завершении загрузки данных
            } catch (e: Exception) {
                dataLoaded.complete(false) // В случае ошибки
            }
        }

        // Задержка перед переходом к MapsActivity
        lifecycleScope.launch {
            delay(8000) // Задержка на 8 секунд для проигрывания GIF
            delayedTransition.complete(true) // Отметка о завершении задержки
        }

        // Переход к MapsActivity после завершения обоих задач
        lifecycleScope.launch {
            joinAll(
                async { dataLoaded.await() },
                async { delayedTransition.await() }
            )
            // После завершения всех задач запускаем MapsActivity
            val intent = Intent(this@SplashActivity, MapsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}