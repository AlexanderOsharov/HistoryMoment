package com.shurik.historymoment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.shurik.historymoment.content.InfoModalData
import com.shurik.historymoment.content.InfoModalDialog
import com.shurik.historymoment.content.html_parsing.SearchInfo
import com.shurik.historymoment.databinding.ActivityMapsBinding
import com.shurik.historymoment.module_moscowapi.MoscowDataAPI
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.Coordinates
import com.shurik.historymoment.module_moscowapi.additional_module.coordinates.GeometryCoordinate
import kotlinx.coroutines.*
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapsActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeMap()
        initializeLocation()
        additionalSettings()
        getLastKnownLocation()
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
        runBlocking {
            val searchInfo = "Бакалейно-мучной магазин В.Л. Жернакова"
            //val searchInfo = "г. Москва, Романов переулок, дом 2/6, строение 8 акт государственной историко культурной экспертизы"
            val info = SearchInfo.getInfoFromYandex(searchInfo)
            Log.e("SearchInfo", "Text: ${info.text}")
            Log.e("SearchInfo", "Images: ${info.images}")
            Log.e("SearchInfo", "Videos: ${info.video}")
        }

        GlobalScope.launch(Dispatchers.IO) {
            val moscowDataAPI = MoscowDataAPI()
            val locations = moscowDataAPI.getObjectsByCoordinates(
                currentLocation.position.latitude,
                currentLocation.position.longitude
            )

            locations?.forEach { location ->
                withContext(Dispatchers.Main) {
                    val newLocation = Marker(map)
                    val coord =
                        when (val geometryCoordinate = location.geometry.coordinates) {
                            is GeometryCoordinate.Point -> geometryCoordinate.coordinates
                            is GeometryCoordinate.MultiPoint -> geometryCoordinate.coordinates[0][0] // берем первые координаты, если они мульти-точечные
                        }

                    when (location.geometry.coordinates) {
                        is GeometryCoordinate.Point -> {
                            newLocation.icon = resources.getDrawable(R.drawable.historical_places)
                        }
                        is GeometryCoordinate.MultiPoint -> {
                            newLocation.icon = resources.getDrawable(R.drawable.walking_tour)
                        } // берем первые координаты, если они мульти-точечные
                    }

                    newLocation.position = GeoPoint(coord.longitude, coord.latitude)
                    newLocation.title = location.properties.attributes.title
                    newLocation.subDescription = location.properties.attributes.description.toString()
                    newLocation.icon = resources.getDrawable(R.drawable.historical_places)
                    Log.e("newLocation", "position: ${newLocation.position.toString()}")
                    Log.e("newLocation", "title: ${newLocation.title}")
                    Log.e("newLocation", "subDescription: ${newLocation.subDescription}")
                    //map.overlays.add(newLocation)

                    val infoModalData: InfoModalData = InfoModalData().apply {
                        title = location.properties.attributes.title
                        coordinates = when (val geometryCoordinate = location.geometry.coordinates) {
                            is GeometryCoordinate.Point -> listOf(geometryCoordinate.coordinates) as MutableList<Coordinates>
                            is GeometryCoordinate.MultiPoint -> geometryCoordinate.coordinates.flatten() as MutableList<Coordinates>
                        }
                        description = location.properties.attributes.description.toString()
                        type = location.geometry.type
                    }

                    if (infoModalData.type == "MultiPoint") {
                        newLocation.setOnMarkerClickListener { marker, mapView ->

                            val dialog = InfoModalDialog(this@MapsActivity, infoModalData)
                            dialog.show()

                            true // Возвращаем true, чтобы обозначить, что обработчик сработал успешно
                        }
                    }
                    map.overlays.add(newLocation)

                }
            }

        }
    }

}
