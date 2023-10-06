package com.shurik.historymoment

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapsActivity : AppCompatActivity() {

        lateinit var map: MapView
        //private lateinit var fusedLocationClient: FusedLocationProviderClient

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
            setContentView(R.layout.activity_maps)

            map = findViewById(R.id.map)    // Прикрепляемся к объекту view
            map.setTileSource(TileSourceFactory.MAPNIK)     // Это выбор тайла (что такое тайлы см. ниже)
            map.setMultiTouchControls(true)     // включает поддержку мультитач-жестов на карте (пользователь может использовать несколько пальцев одновременно)

            val mapController = map.controller     // Положение карты
            mapController.setZoom(9.5)
            val startPoint = GeoPoint(48.8583, 2.2944) // Центр карты
            mapController.setCenter(startPoint)

            // Так можно добавлять точки различных мест
            val place1 = GeoPoint(48.8566, 2.3522) // Точка на карте (например, Эйфелева бащня)
            addMarker(place1)

            //... Пытался сделать отметку местоположения пользователя (пока не вышло)
            /*fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if(it != null) {
                    val startPoint = GeoPoint(it.latitude, it.longitude)
                    mapController.setCenter(startPoint)
                }
            }*/
        }

        /*private val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return

                for (location in locationResult.locations){
                    if (location != null) {
                        val startPoint = GeoPoint(location.latitude, location.longitude)
                        mapController.setCenter(startPoint)
                    }
                }
            }
        }*/

    // Функция по добавлению новых точек
    fun addMarker(point: GeoPoint) {
            val marker = Marker(map)
            marker.position = point
            marker.title = "Historical Place"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { marker, mapView ->
                // TODO: Show Wikipedia info here
                true
            }
            map.overlays.add(marker)
        }

        override fun onResume() {
            super.onResume()
            map.onResume()
        }

        override fun onPause() {
            super.onPause()
            map.onPause()
        }
    }

/*Тайлы - это небольшие изображения, которые составляют картографическую информацию, такую как карты.
Они представляют собой квадратные фрагменты карты, которые загружаются и отображаются на экране вместе.

В библиотеке Mapsforge для карты доступны различные источники тайлов. Некоторые из них включают:
1. MAPNIK: Это стандартный и самый распространенный источник тайлов OpenStreetMap.
Он предоставляет стандартные тайлы, содержащие подробную информацию о дорогах, зданиях, природных объектах и т.д.
2. MAPNIK\_EUROPE: Это вариант источника тайлов MAPNIK, который ограничен только картами Европы.
Он содержит те же типы информации, что и MAPNIK, но только для Европы.
3. BASE: Это источник тайлов с базовой картографической информацией, такой как границы стран, основные города и реки.

Выбор источника тайлов зависит от конкретного использования и потребностей.
Различные источники могут предоставлять различную степень детализации,
охватывать разные регионы и иметь разный стиль отображения.
Можно выбрать источник тайлов, соответствующий требованиям к функциональности и эстетике карты.*/

