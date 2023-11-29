package com.shurik.historymoment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.location.*
import com.shurik.historymoment.content.InfoModalData
import com.shurik.historymoment.content.InfoModalDialog
import com.shurik.historymoment.databinding.ActivityMapsBinding
import com.shurik.historymoment.db.DatabaseManager
import com.shurik.historymoment.db.HistoryMomentViewModel
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.Coordinates
import com.vividsolutions.jts.operation.overlay.MaximalEdgeRing
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.*
import kotlin.math.pow

class MapsActivity : AppCompatActivity() {
    private val dbManager = DatabaseManager();
    companion object {
        lateinit var map: MapView
        lateinit var mapController: IMapController
    }

    private lateinit var binding: ActivityMapsBinding
    private lateinit var currentLocation: Marker
    private lateinit var routeButton: Button
    private var isRouteButtonClicked: Boolean = false
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
            if (map.mapCenter == currentLocation.position){
                mapController.setZoom(20)
            }
            else {
                getLastKnownLocation()
            }
        }

        routeButton = binding.routeButton
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
                if (!isRouteButtonClicked) {
                    if (map.zoomLevelDouble > 17.0 && map.zoomLevelDouble > previousZoomLevel) {
                        previousZoomLevel = map.zoomLevelDouble
                        displayMarkersOnScreen()
                    } else {
                        map.overlays.clear()
                        map.overlays.add(currentLocation)
                    }
                }
                return true
            }

            override fun onScroll(e: ScrollEvent?): Boolean {
                if (!isRouteButtonClicked) displayMarkersOnScreen()
                return true
            }
        })
    }

    private fun displayMarkersOnScreen() {
        if (map.zoomLevelDouble > 18.0) {
            val visibleMarkers = ListPointsRoute()

            map.overlays.clear()
            map.overlays.addAll(visibleMarkers)
            map.overlays.add(currentLocation)
        }
    }

    private fun setupRouteButton(){
        routeButton.setOnClickListener {
            isRouteButtonClicked = !isRouteButtonClicked
            if (isRouteButtonClicked) {
                routeButton.text = "Вернуться"

                val visibleMarkers = ListPointsRoute()
                val numberPicker = NumberPicker(this)
                numberPicker.minValue = 1
                numberPicker.maxValue =
                    visibleMarkers.size // кол-во элементов в листе visibleMarkers

                val dialog = AlertDialog.Builder(this)
                    .setTitle("Выберите количество точек для маршрута")
                    .setView(numberPicker)
                    .setPositiveButton("OK") { _, _ ->
                        val selectedNumPoints = numberPicker.value
                        buildRoute(selectedNumPoints, visibleMarkers)
                    }
                    .setNegativeButton("Cancel", null)
                    .create()

                dialog.show()
            }
            else {
                routeButton.text = "построить маршрут"

                if (map.zoomLevelDouble > 17.0) {
                    displayMarkersOnScreen()
                } else {
                    map.overlays.clear()
                    map.overlays.add(currentLocation)
                }
            }
        }
    }

    private fun buildRoute(numPoints: Int, visibleMarkers: MutableList<Marker>) {
        //val sortedMarkers = visibleMarkers.sortedBy { it.position.distanceToAsDouble(currentLocation.position) }
        var temp = visibleMarkers[0]
        var index = FindPoint(currentLocation, visibleMarkers)
        visibleMarkers[0] = visibleMarkers[index]
        visibleMarkers[index] = temp
        Log.e("BuildRoute666666", "visibleMarkers.size = ${visibleMarkers.size}")
        for (i in 1 until visibleMarkers.size) {
            Log.e("BuildRoute666666", "i = $i")
            temp = visibleMarkers[i]
            index = FindPoint(visibleMarkers[i - 1], visibleMarkers.subList(i, visibleMarkers.size))
            Log.e("BuildRoute666666", "index = $index")
            visibleMarkers[i] = visibleMarkers[index + i]
            visibleMarkers[index + i] = temp
            Log.e("BuildRoute666666", "index + i + 1 = ${index + i + 1}")
        }

        // Строим маршрут
        val polyline = Polyline()
        polyline.width = 10f
        polyline.color = Color.BLUE
        polyline.addPoint(currentLocation.position) // текущее положение
        polyline.addPoint(visibleMarkers[0].position) // положение первой видимой точки

        map.overlayManager.add(polyline)

        for (i in 1 until numPoints) {
            val polyline1 = Polyline()
            polyline1.width = 10f
            polyline1.color = Color.BLUE
            polyline1.addPoint(visibleMarkers[i - 1].position) // положение первой точки
            polyline1.addPoint(visibleMarkers[i].position) // положение второй очки

            map.overlayManager.add(polyline1)
        }

        map.invalidate() // обновление карты
    }

    @SuppressLint("SuspiciousIndentation")
    private fun FindPoint(marker: Marker, listMarkers: MutableList<Marker>): Int {
        var minIndex = 0

        if (listMarkers.size > 0){
            //var minDist = InfoModalDialog.bearingBetweenLocations(marker.position.latitude, marker.position.longitude, listMarkers[0].position.latitude, listMarkers[0].position.longitude)
            var minDist = distance(marker, listMarkers[0])

            for (i in 1 until listMarkers.size){
                if (marker.position != listMarkers[i].position) {
                    //val newDist = InfoModalDialog.bearingBetweenLocations(marker.position.latitude, marker.position.longitude, listMarkers[i].position.latitude, listMarkers[i].position.longitude)
                    val newDist = distance(marker, listMarkers[i])
                    if (newDist < minDist) {
                        minDist = newDist
                        minIndex = i
                    }
                }
            }
        }

        return minIndex
    }

    fun distance(marker1: Marker, marker2: Marker): Double {
        val latDistance = Math.toRadians(marker2.position.latitude - marker1.position.latitude)
        val lonDistance = Math.toRadians(marker2.position.longitude - marker1.position.longitude)

        val a = Math.sin(latDistance / 2).pow(2.0) +
                Math.cos(Math.toRadians(marker1.position.latitude)) * Math.cos(Math.toRadians(marker2.position.latitude)) *
                Math.sin(lonDistance / 2).pow(2.0)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        val radius = 6371 // Earth's radius in kilometers
        return radius * c
    }

    private fun ListPointsRoute(): MutableList<Marker> {
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
        }
        return visibleMarkers
    }

    private fun isMarkerWithinScreenBounds(markerPosition: GeoPoint): Boolean {
        val markerPoint = Point()
        map.projection.toPixels(markerPosition, markerPoint)
        val screenWidth = map.width
        val screenHeight = map.height
        return markerPoint.x in 0..screenWidth && markerPoint.y in 0..screenHeight
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


