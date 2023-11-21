package com.shurik.historymoment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
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

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun displayLocations() {
        val newLocation = Marker(map)
        newLocation.title = "Спасская башня"
        newLocation.subDescription = "С другой стороны постоянный количественный рост и сфера нашей активности представляет собой интересный эксперимент проверки направлений прогрессивного развития. Идейные соображения высшего порядка, а также сложившаяся структура организации позволяет оценить значение позиций, занимаемых участниками в отношении поставленных задач. Товарищи! укрепление и развитие структуры обеспечивает широкому кругу (специалистов) участие в формировании форм развития.\n" +
                "Значимость этих проблем настолько очевидна, что рамки и место обучения кадров обеспечивает широкому кругу (специалистов) участие в формировании форм развития. Задача организации, в особенности же новая модель организационной деятельности способствует подготовки и реализации системы обучения кадров, соответствует насущным потребностям. Задача организации, в особенности же реализация намеченных плановых заданий влечет за собой процесс внедрения и модернизации систем массового участия. Разнообразный и богатый опыт начало повседневной работы по формированию позиции позволяет оценить значение направлений прогрессивного развития. Равным образом рамки и место обучения кадров позволяет выполнять важные задания по разработке системы обучения кадров, соответствует насущным потребностям. Задача организации, в особенности же реализация намеченных плановых заданий позволяет выполнять важные задания по разработке системы обучения кадров, соответствует насущным потребностям.\n" +
                "Значимость этих проблем настолько очевидна, что сложившаяся структура организации позволяет выполнять важные задания по разработке форм развития. Не следует, однако забывать, что начало повседневной работы по формированию позиции обеспечивает широкому кругу (специалистов) участие в формировании соответствующий условий активизации. Равным образом дальнейшее развитие различных форм деятельности требуют от нас анализа существенных финансовых и административных условий. Таким образом начало повседневной работы по формированию позиции обеспечивает широкому кругу (специалистов) участие в формировании систем массового участия. Идейные соображения высшего порядка, а также постоянное информационно-пропагандистское обеспечение нашей деятельности способствует подготовки и реализации соответствующий условий активизации. Не следует, однако забывать, что реализация намеченных плановых заданий в значительной степени обуславливает создание дальнейших направлений развития.\n" +
                "Равным образом постоянное информационно-пропагандистское обеспечение нашей деятельности позволяет выполнять важные задания по разработке форм развития. Равным образом постоянное информационно-пропагандистское обеспечение нашей деятельности обеспечивает широкому кругу (специалистов) участие в формировании новых предложений.\n" +
                "Товарищи! дальнейшее развитие различных форм деятельности играет важную роль в формировании существенных финансовых и административных условий. Равным образом рамки и место обучения кадров требуют от нас анализа направлений прогрессивного развития. Не следует, однако забывать, что реализация намеченных плановых заданий позволяет оценить значение позиций, занимаемых участниками в отношении поставленных задач. Разнообразный и богатый опыт начало повседневной работы по формированию позиции способствует подготовки и реализации соответствующий условий активизации"
        newLocation.position = GeoPoint(55.752541, 37.621471)
        newLocation.icon = resources.getDrawable(R.drawable.historical_places)
        map.overlays.add(newLocation)
        val infoModalData: InfoModalData = InfoModalData().apply {
            title = newLocation.title
            coordinates = listOf(Coordinates(55.752541, 37.621471)) as MutableList<Coordinates>
            description = newLocation.subDescription
            images = listOf("https://way2day.com/wp-content/uploads/2017/12/Bashnya-sblizi.jpg", "https://putidorogi-nn.ru/images/stories/evropa/rossiya/spasskaya-bashnya-moskovskogo-kremlya_4.jpg", "https://i.pinimg.com/originals/bc/27/05/bc270554c89c65798c9829ac4514c5f2.jpg") as MutableList<String>
            type = "Point"
        }
        newLocation.setOnMarkerClickListener { marker, mapView ->
            val dialog = InfoModalDialog(this@MapsActivity, infoModalData)
            dialog.show()

            true // Возвращаем true, чтобы обозначить, что обработчик сработал успешно
        }
        val newLocation2 = Marker(map)
        newLocation2.title = "Путешествие"
        newLocation2.subDescription = "С другой стороны постоянный количественный рост и сфера нашей активности представляет собой интересный эксперимент проверки направлений прогрессивного развития. Идейные соображения высшего порядка, а также сложившаяся структура организации позволяет оценить значение позиций, занимаемых участниками в отношении поставленных задач. Товарищи! укрепление и развитие структуры обеспечивает широкому кругу (специалистов) участие в формировании форм развития.\n" +
                "Значимость этих проблем настолько очевидна, что рамки и место обучения кадров обеспечивает широкому кругу (специалистов) участие в формировании форм развития. Задача организации, в особенности же новая модель организационной деятельности способствует подготовки и реализации системы обучения кадров, соответствует насущным потребностям. Задача организации, в особенности же реализация намеченных плановых заданий влечет за собой процесс внедрения и модернизации систем массового участия. Разнообразный и богатый опыт начало повседневной работы по формированию позиции позволяет оценить значение направлений прогрессивного развития. Равным образом рамки и место обучения кадров позволяет выполнять важные задания по разработке системы обучения кадров, соответствует насущным потребностям. Задача организации, в особенности же реализация намеченных плановых заданий позволяет выполнять важные задания по разработке системы обучения кадров, соответствует насущным потребностям.\n" +
                "Значимость этих проблем настолько очевидна, что сложившаяся структура организации позволяет выполнять важные задания по разработке форм развития. Не следует, однако забывать, что начало повседневной работы по формированию позиции обеспечивает широкому кругу (специалистов) участие в формировании соответствующий условий активизации. Равным образом дальнейшее развитие различных форм деятельности требуют от нас анализа существенных финансовых и административных условий. Таким образом начало повседневной работы по формированию позиции обеспечивает широкому кругу (специалистов) участие в формировании систем массового участия. Идейные соображения высшего порядка, а также постоянное информационно-пропагандистское обеспечение нашей деятельности способствует подготовки и реализации соответствующий условий активизации. Не следует, однако забывать, что реализация намеченных плановых заданий в значительной степени обуславливает создание дальнейших направлений развития.\n" +
                "Равным образом постоянное информационно-пропагандистское обеспечение нашей деятельности позволяет выполнять важные задания по разработке форм развития. Равным образом постоянное информационно-пропагандистское обеспечение нашей деятельности обеспечивает широкому кругу (специалистов) участие в формировании новых предложений.\n" +
                "Товарищи! дальнейшее развитие различных форм деятельности играет важную роль в формировании существенных финансовых и административных условий. Равным образом рамки и место обучения кадров требуют от нас анализа направлений прогрессивного развития. Не следует, однако забывать, что реализация намеченных плановых заданий позволяет оценить значение позиций, занимаемых участниками в отношении поставленных задач. Разнообразный и богатый опыт начало повседневной работы по формированию позиции способствует подготовки и реализации соответствующий условий активизации"
        newLocation2.position = GeoPoint(55.765574, 37.624489)
        newLocation2.icon = resources.getDrawable(R.drawable.walking_tour)
        map.overlays.add(newLocation2)
        val infoModalData2: InfoModalData = InfoModalData().apply {
            title = newLocation2.title
            coordinates = listOf(Coordinates(55.765574, 37.624489), Coordinates(55.759339, 37.607170)) as MutableList<Coordinates>
            description = newLocation2.subDescription
            images = listOf("https://sportishka.com/uploads/posts/2022-04/1650707988_19-sportishka-com-p-krasivie-mesta-v-moskve-krasivo-foto-21.jpg", "https://www.brodyaga.ru/pages/photos/Russia/Moscow%20Russia%201295005232(www.brodyaga.com).jpg", "https://www.brodyaga.ru/pages/photos/Russia/Moscow%20Russia%201295005564(www.brodyaga.com).jpg") as MutableList<String>
            type = "MultiPoint"
        }
        newLocation2.setOnMarkerClickListener { marker, mapView ->
            val dialog = InfoModalDialog(this@MapsActivity, infoModalData)
            dialog.show()

            true // Возвращаем true, чтобы обозначить, что обработчик сработал успешно
        }

        /*runBlocking {
            val searchInfo = "Бакалейно-мучной магазин В.Л. Жернакова"
            //val searchInfo = "г. Москва, Романов переулок, дом 2/6, строение 8 акт государственной историко культурной экспертизы"
            val info = SearchInfo.getInfoFromYandex(searchInfo)
            Log.e("SearchInfo", "Text: ${info.text}")
            Log.e("SearchInfo", "Images: ${info.images}")
            Log.e("SearchInfo", "Videos: ${info.video}")
        }*/ //TODO: После добавления нормального окошка запустить этот блок (он ищет информацию об объектах в интернете)

        /*HistoryMomentViewModel.objects.observe(this) {
            val locations = it

            locations?.forEach { location ->
                val coord = location.address?.mapPosition?.coordinates

                val newLocation = Marker(map)
                newLocation.title = location.name
                newLocation.subDescription = "Check"
                //map.overlays.add(newLocation)

                /*val infoModalData: InfoModalData = InfoModalData().apply {
                    title = location.properties.attributes.title
                    coordinates =
                        when (val geometryCoordinate = location.geometry.coordinates) {
                            is GeometryCoordinate.Point -> listOf(geometryCoordinate.coordinates) as MutableList<Coordinates>
                            is GeometryCoordinate.MultiPoint -> geometryCoordinate.coordinates.flatten() as MutableList<Coordinates>
                        }
                    description = location.properties.attributes.description.toString()
                    type = location.geometry.type
                }*/
                if (location.address?.mapPosition?.type == "Point") {
                    newLocation.position = GeoPoint(coord?.get(1) ?: 0.0, coord?.get(0) ?: 0.0)
                    newLocation.icon = resources.getDrawable(R.drawable.historical_places)
                }
                else {
                    newLocation.position = GeoPoint(coord?.get(1) ?: 0.0, coord?.get(0) ?: 0.0)
                    newLocation.icon = resources.getDrawable(R.drawable.walking_tour)
                    /*newLocation.setOnMarkerClickListener { marker, mapView ->
                        val dialog = InfoModalDialog(this@MapsActivity, infoModalData)
                        dialog.show()

                        true // Возвращаем true, чтобы обозначить, что обработчик сработал успешно
                    }*/
                }
                    map.overlays.add(newLocation)

                }
            }*/
    }
    /*private fun displayLocations() {
        HistoryMomentViewModel.objects.observe(this) {
            val locations = it

            val clusterer = RadiiClusterer<MapMarker>(this) // Создаем кластеризатор
            val overlayManager = map.overlayManager // Набиваем нашу коллекцию Items

            val items: ArrayList<OverlayItem> = ArrayList()

            locations?.forEach { location ->
                val coord = location.address?.mapPosition?.coordinates

                if(location.address?.mapPosition?.type != "Point") {
                    return@forEach
                }

                val position = GeoPoint(coord?.get(1) ?: 0.0, coord?.get(0) ?: 0.0)
                val title = location.name ?: "No title"
                val item = OverlayItem(title, "Check", position)

                items.add(item)
            }

            clusterer.setItems(items)

            val markersOverlay = ItemizedOverlayWithFocus<OverlayItem>(items,
                object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                        // Здесь у вас может быть код для обработки нажатия на маркер/кластер
                        return true
                    }
                    override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                        // Здесь у вас может быть код для обработки долгого нажатия
                        return true
                    }
                }, applicationContext)

            markersOverlay.setFocusItemsOnTap(true)
            overlayManager.add(markersOverlay)
        }
    }*/


}


