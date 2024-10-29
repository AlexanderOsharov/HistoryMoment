package com.shurik.historymoment



import android.Manifest
import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.shurik.historymoment.R
import com.shurik.historymoment.db.DatabaseManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
class SplashActivity : AppCompatActivity() {
    private lateinit var splashImageView: ImageView
    private lateinit var mapView: MapView
    private val dbManager = DatabaseManager()

    private val REQUEST_LOCATION_PERMISSIONS = 1000
    private var isRequestingPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        splashImageView = findViewById(R.id.splash_image_view)
        mapView = findViewById(R.id.map_view) // Убедитесь, что вы добавили MapView в разметку activity_splash

        // Запрос разрешения на местоположение
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!isRequestingPermission) {
                isRequestingPermission = true
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSIONS)
            }
        } else {
            // Разрешение уже предоставлено, переходим к инициализации карты
            preloadMap()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            isRequestingPermission = false // Сброс флага

            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение предоставлено, инициализируем карту
                preloadMap()
            } else {
                // Разрешение отклонено, показываем сообщение и повторный запрос
                showPermissionRationale()
            }
        }
    }

    private fun preloadMap() {
        // Настройка карты OpenStreetMap
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Установить видимость карты на VISIBLE для тайлового запроса
        mapView.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Список координат для дерганья карты
            val points = listOf(
                GeoPoint(55.7558, 37.6173), // Центр Москвы
                GeoPoint(55.8102, 37.6549), // Кутузовский проспект
                GeoPoint(55.7422, 37.6173), // Краснопресненская набережная
                GeoPoint(55.7539, 37.6208), // Кремль
                GeoPoint(55.7701, 37.6403), // ВВДНХ
                GeoPoint(55.6612, 37.5173), // Кузьминки
                GeoPoint(55.8000, 37.5800), // Таганка
                GeoPoint(55.7660, 37.6173), // Арбат
                GeoPoint(55.7520, 37.6173), // Красная площадь
                GeoPoint(55.7999, 37.6542), // Циолковского
                GeoPoint(55.7350, 37.6542)  // Чистые пруды
            )

            val zoomLevels = listOf(
                15.0, 12.0, 12.0, 14.0,
                12.0, 12.0, 12.0, 14.0,
                14.0, 12.0, 12.0
            )

            // Проходим по всем точкам и зумам
            for (i in points.indices) {
                mapView.controller.setZoom(zoomLevels[i])
                mapView.controller.setCenter(points[i])
                mapView.invalidate() // Запрашиваем новый тайл

                // Ожидание, чтобы дать время на загрузку
                //delay(500)
            }

            // Вернуть видимость карты на GONE, если необходимо
            mapView.visibility = View.GONE

            // После завершения всех "дерганий" запускаем другие задачи
            goToMapsActivity()
        }
    }

    private fun showPermissionRationale() {
        // Показать сообщение о том, что разрешение необходимо
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Это приложение требует разрешения на доступ к местоположению для работы. Пожалуйста, предоставьте разрешение.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Повторно запрашиваем разрешение
                requestLocationPermission()
            }
            .setNegativeButton("Выход") { dialog, _ ->
                dialog.dismiss()
                // Завершение активности, если пользователь не хочет давать разрешение
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun goToMapsActivity() {
        splashImageView = findViewById(R.id.splash_image_view)
        val gifResourceID = R.raw.splash_gif // Ресурс GIF

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