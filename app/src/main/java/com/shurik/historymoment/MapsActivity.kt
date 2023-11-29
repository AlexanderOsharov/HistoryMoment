package com.shurik.historymoment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.location.*
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.graphhopper.reader.osm.GraphHopperOSM
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.shapes.GHPoint
import com.shurik.historymoment.content.InfoModalData
import com.shurik.historymoment.content.InfoModalDialog
import com.shurik.historymoment.content.html_parsing.SearchInfo
import com.shurik.historymoment.databinding.ActivityMapsBinding
import com.shurik.historymoment.db.DatabaseManager
import com.shurik.historymoment.db.HistoryMomentViewModel
import com.shurik.historymoment.module_moscowapi.MoscowDataAPI
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.Coordinates
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.GeometryCoordinate
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class MapsActivity : AppCompatActivity() {
    private val dbManager = DatabaseManager();
    companion object {
        lateinit var map: MapView
        lateinit var mapController: IMapController
    }

    private lateinit var binding: ActivityMapsBinding
    private lateinit var currentLocation: Marker
    private lateinit var routeButton: Button
    private lateinit var centerLocationButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val REQUEST_LOCATION_PERMISSIONS = 1

    // Для определения частоты обновления местоположения
    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 10000
        fastestInterval = 5000
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    //TODO(Для проверки работоспособности базы поместил код с viewmodel в MapsActivity, дальше используй по своему усмотрению)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeMap()
        initializeLocation()
        additionalSettings()
        getLastKnownLocation()
        initLocationUpdates()


        // В val objects_list будут хранится все обьекты сразу, но в лог выведется только 100
        // ==========================================================================
        HistoryMomentViewModel.objects.observe(this) {
            val objects_list = it
            var index = 0
            objects_list.forEach {
                val typeLoc = it?.address?.mapPosition?.type
                it.address?.fulladdress?.let { it1 -> Log.e("Address", it1) }
                Log.e("Locations", typeLoc + it?.name + it.address?.fulladdress + it?.hash + " ")
                val coord = it.address?.mapPosition?.coordinates
                coord?.forEach {
                    Log.e("LocF", "$index   $typeLoc   $it")
                }
                index++

                /*runBlocking {
                    val searchInfo = it?.name + " " + it.address?.fulladdress
                    //val searchInfo = "г. Москва, Романов переулок, дом 2/6, строение 8 акт государственной историко культурной экспертизы"
                    val info = SearchInfo.getInfoFromYandex(searchInfo)
                    Log.e("SearchInfo", "${searchInfo}:")
                    Log.e("SearchInfo", "Text: ${info.text}")
                    Log.e("SearchInfo", "Images: ${info.images}")
                    Log.e("SearchInfo", "Videos: ${info.video}")
                    Log.e("SearchInfo", "")
                    Log.e("SearchInfo", "")
                    Log.e("SearchInfo", "")
                }*/
            }
        }
        dbManager.getData()
        // ==========================================================================
    }

    private fun initializeMap() {
        map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.mapOrientation = 0.0f

        mapController = map.controller
        mapController.setZoom(20)

        currentLocation = Marker(map)
        currentLocation.title = "Я"
        currentLocation.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        currentLocation.icon = resources.getDrawable(R.drawable.ic_mark_person)
        map.overlays.add(currentLocation)

    }

    private fun initializeLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSIONS
            )
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient?.lastLocation?.addOnSuccessListener {
            if (it != null) {
                val startPoint = GeoPoint(it.latitude, it.longitude)
                mapController.setCenter(startPoint)
            }
        }
    }

    private fun initLocationUpdates() {
        val locationRequest = LocationRequest.create()?.apply {
            interval = 100 // Обновляем каждые 100 миллисекунд
            fastestInterval = 10
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    updateCurrentLocationMarker(location.latitude, location.longitude)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient?.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateCurrentLocationMarker(latitude: Double, longitude: Double) {
        val startPoint = GeoPoint(latitude, longitude)
        if (currentLocation.position == null) {
            currentLocation.position = startPoint
            map.overlays.add(currentLocation)
        } else {
            currentLocation.position = startPoint
        }
        //mapController.setCenter(startPoint)
        map.invalidate()
    }


    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
            if (location != null) {
                val startPoint = GeoPoint(location.latitude, location.longitude)
                mapController.setCenter(startPoint)
                currentLocation.position = startPoint
                map.overlays.add(currentLocation)

                // Отобразить места после успешного определения местоположения
                displayLocations()

                map.invalidate()
            }
        }
    }

    private fun additionalSettings() {
        centerLocationButton = binding.centerLocationButton
        centerLocationButton.setOnClickListener {
            getLastKnownLocation()
        }

        setupRouteButton()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation()
            }
        }
    }

    override fun onResume() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSIONS
            )
            return
        }

        super.onResume()
        getLastKnownLocation()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    private fun displayLocations() {
        var previousZoomLevel = map.zoomLevelDouble
        displayMarkersOnScreen()
        map.addMapListener(object : MapListener {
            override fun onZoom(e: ZoomEvent?): Boolean {
                if (map.zoomLevelDouble > 17.0 && map.zoomLevelDouble > previousZoomLevel) {
                    previousZoomLevel = map.zoomLevelDouble
                    displayMarkersOnScreen()
                } else {
                    map.overlays.clear()
                    map.overlays.add(currentLocation)
                }
                return true
            }

            override fun onScroll(e: ScrollEvent?): Boolean {
                displayMarkersOnScreen()
                return true
            }
        })
    }

    private fun displayMarkersOnScreen() {
        val mapBoundingBox = map.boundingBox
        val visibleMarkers = mutableListOf<Marker>()

        if (map.zoomLevelDouble > 18.0) {
            HistoryMomentViewModel.objects.observe(this) { locations ->
                locations?.forEach { location ->
                    val coord = location.address?.mapPosition?.coordinates
                    if (coord != null && coord.size == 2) {
                        val locationPosition = GeoPoint(coord[1], coord[0])
                        if (mapBoundingBox.contains(locationPosition) && isMarkerWithinScreenBounds(locationPosition)) {
                            val newLocation = Marker(map)
                            newLocation.title = location.name
                            newLocation.subDescription = location.description
                            newLocation.position = locationPosition
                            newLocation.icon = resources.getDrawable(R.drawable.historical_places)
                            val target = object : CustomTarget<Drawable>() {
                                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                    newLocation.icon = resource
                                }

                                override fun onLoadCleared(placeholder: Drawable?) {
                                    // Handle clear
                                }
                            }

                            val placeholderIcon = R.drawable.historical_places
                            Glide.with(this)
                                .asDrawable()
                                .load(listOf(location.photo?.url.toString())[0])
                                .circleCrop() // для того чтобы сделать картинку круглой
                                .override(100, 100) // желаемый размер
                                .placeholder(placeholderIcon) // устанавливаем иконку по умолчанию
                                .error(placeholderIcon) // устанавливаем иконку по умолчанию в случае ошибки загрузки
                                .into(target)

                            val job = CoroutineScope(Dispatchers.IO).async {
                                val searchInfo = location.name + location.address
                                try {
                                    val info = SearchInfo.getInfoFromYandex(searchInfo)
                                    info.text
                                } catch (e: Exception) {
                                    Log.e("SearchInfo", "Error: ${e.message}")
                                    "Failed to retrieve description"
                                }
                            }

                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val result = withContext(Dispatchers.IO) { job.await() }
                                    newLocation.subDescription = result
                                } catch (e: Exception) {
                                    Log.e("SearchInfo", "Error: ${e.message}")
                                    newLocation.subDescription = "Failed to retrieve description"
                                }
                            }



//                            // установить значение по умолчанию в 0.5, чтобы окно с информацией показывалось непосредственно под иконкой маркера
//                            newLocation.setInfoWindowAnchor(0.5f, 0.5f)
//
//                            // показать окно с информацией
//                            newLocation.showInfoWindow()

                            newLocation.setOnMarkerClickListener { marker, mapView ->
                                val infoModalData = InfoModalData().apply {
                                    title = newLocation.title
                                    coordinates = listOf(
                                        Coordinates(
                                            coord[1],
                                            coord[0]
                                        )
                                    ) as MutableList<Coordinates>
                                    description = newLocation.subDescription
                                    images =
                                        listOf(location.photo?.url.toString()) as MutableList<String>
                                    type = location.address?.mapPosition?.type.toString()
                                }
                                val dialog = InfoModalDialog(this@MapsActivity, infoModalData)
                                dialog.show()
                                true
                            }
                            visibleMarkers.add(newLocation)
                        }
                    }
                }
            }

            map.overlays.clear()
            map.overlays.addAll(visibleMarkers)
            map.overlays.add(currentLocation)
        }
    }

    private fun isMarkerWithinScreenBounds(markerPosition: GeoPoint): Boolean {
        val markerPoint = Point()
        map.projection.toPixels(markerPosition, markerPoint)
        val screenWidth = map.width
        val screenHeight = map.height
        return markerPoint.x in 0..screenWidth && markerPoint.y in 0..screenHeight
    }



    private var isRoutePlanned = false
    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var routePolyline: Polyline? = null

    private fun setupRouteButton() {
        routeButton = binding.routeButton
        routeButton.setOnClickListener {
            if (!isRoutePlanned) {
                // Переключение в режим построения маршрута
                isRoutePlanned = true
                routeButton.text = "Отменить"
                // Вешаем обработчик нажатия на карту для выбора точек
                map.overlays.add(0, object : Overlay() {
                    override fun onSingleTapConfirmed(
                        e: MotionEvent?,
                        mapView: MapView?
                    ): Boolean {
                        val projection = mapView!!.projection
                        val tappedGeoPoint: GeoPoint = projection!!.fromPixels(
                            e!!.x.toInt(),
                            e.y.toInt()
                        ) as GeoPoint
                        if (startPoint == null) {
                            startPoint = tappedGeoPoint
                            startMarker = addStartEndMarker(tappedGeoPoint, "start")
                        } else if (endPoint == null && tappedGeoPoint != startPoint) {
                            endPoint = tappedGeoPoint
                            endMarker = addStartEndMarker(tappedGeoPoint, "end")
                            buildPedestrianRoute(startMarker!!, endMarker!!) { routePoints ->
                                for (point in routePoints) {
                                    val newLocation = Marker(map)
                                    newLocation.position = GeoPoint(point.getLat(), point.getLon())
                                    newLocation.icon =
                                        resources.getDrawable(R.drawable.walking_direction)
                                    newLocation.setOnMarkerClickListener { marker, mapView ->
                                        true
                                    }
                                    map.overlays.add(newLocation)
                                }
                            }
                            //routeButton.performClick() // Моделируем нажатие кнопки для переключения обратно
                        }
                        return true
                    }

                    // Другие методы интерфейса Overlay
                })
            } else {
                // Отмена построения маршрута
                isRoutePlanned = false
                routeButton.text = "Построить маршрут"
                map.overlays.remove(startMarker)
                map.overlays.remove(endMarker)
                if (routePolyline != null) {
                    map.overlays.remove(routePolyline)
                    routePolyline = null
                }
                startPoint = null
                endPoint = null
                startMarker = null
                endMarker = null
                map.invalidate()
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun addStartEndMarker(geoPoint: GeoPoint, type: String): Marker {
        val marker = Marker(map)
        marker.position = geoPoint
        marker.icon = if (type == "start") {
            resources.getDrawable(R.drawable.walking_tour)
        } else {
            resources.getDrawable(R.drawable.walking_finish)
        }
        map.overlays.add(marker)
        map.invalidate()
        return marker
    }

    private fun buildPedestrianRoute(start: Marker, end: Marker, onRouteBuilt: (List<GHPoint>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://graphhopper.com/api/1/route?point=${start.getPosition().latitude},${start.getPosition().longitude}&point=${end.getPosition().latitude},${end.getPosition().longitude}&vehicle=foot&locale=en&key=${SecretFile.ACCESS_TOKEN_GRAPHHOPPER}")
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    // обработка ошибки, например, вывод сообщения об ошибке
                    withContext(Dispatchers.Main) {
                        onRouteBuilt(emptyList())
                    }
                } else {
                    response.body?.use { responseBody ->
                        val pathPoints = JSONObject(responseBody.string())
                            .getJSONObject("paths").getJSONArray("points")

                        val points = mutableListOf<GHPoint>()
                        for (i in 0 until pathPoints.length()) {
                            val point = pathPoints.getJSONArray(i)
                            points.add(GHPoint(point.getDouble(0), point.getDouble(1)))
                        }
                        withContext(Dispatchers.Main) {
                            onRouteBuilt(points)
                        }
                    }
                }
            } catch (e: Exception) {
                // обработка других исключений, например, логирование
                Log.e("GrapEr", e.toString())
                withContext(Dispatchers.Main) {
                    onRouteBuilt(emptyList())
                }
            }
        }
    }

    private lateinit var tts: TextToSpeech
    fun speakDescription(view: View) {
        val descriptionTextView: TextView = findViewById(R.id.descriptionTextView)
        val textToSpeak: String = descriptionTextView.text.toString()
        tts = TextToSpeech(this, TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.getDefault()
            }
        })

        tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}


