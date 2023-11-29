package com.shurik.historymoment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.google.android.gms.location.*
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
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.OverlayItem

class MapsActivity : AppCompatActivity() {
    private val dbManager = DatabaseManager();
    companion object {
        lateinit var map: MapView
        lateinit var mapController: IMapController
    }

    private lateinit var binding: ActivityMapsBinding
    private lateinit var currentLocation: Marker
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
                Log.e("Locations", typeLoc + it?.name + it.address?.fulladdress + it?.hash + " ")
                val coord = it.address?.mapPosition?.coordinates
                coord?.forEach {
                    Log.e("LocF", "$index   $typeLoc   $it")
                }
                index++
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
            interval = 1000 // Обновляем каждую секунду
            fastestInterval = 5000
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

        if (map.zoomLevelDouble > 17.0) {
            HistoryMomentViewModel.objects.observe(this) { locations ->
                locations?.forEach { location ->
                    val coord = location.address?.mapPosition?.coordinates
                    if (coord != null && coord.size == 2) {
                        val locationPosition = GeoPoint(coord[1], coord[0])
                        if (mapBoundingBox.contains(locationPosition)) {
                            val newLocation = Marker(map)
                            newLocation.title = location.name
                            newLocation.subDescription = location.description
                            newLocation.position = locationPosition
                            newLocation.icon = if (location.address?.mapPosition?.type == "Point") {
                                resources.getDrawable(R.drawable.historical_places)
                            } else {
                                resources.getDrawable(R.drawable.walking_tour)
                            }
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

        map.overlays.clear()
        map.overlays.addAll(visibleMarkers)
        map.overlays.add(currentLocation)
    }
}


