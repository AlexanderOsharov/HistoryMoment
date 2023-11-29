package com.shurik.historymoment.content

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.bumptech.glide.Glide
import com.shurik.historymoment.MapsActivity
import com.shurik.historymoment.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.*

class InfoModalDialog(context: Context, private val data: InfoModalData) : Dialog(context, R.style.FullScreenDialogStyle) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.info_modal)
        window?.setGravity(Gravity.BOTTOM)
        window?.setWindowAnimations(R.style.DialogAnimation)
        //window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        setCanceledOnTouchOutside(true)
        setupData()
    }

    @SuppressLint("SetTextI18n")
    private fun setupData() {
        val titleTextView: TextView = findViewById(R.id.titleTextView)
        val coordinatesTextView: TextView = findViewById(R.id.coordinatesTextView)
        val descriptionTextView: TextView = findViewById(R.id.descriptionTextView)
        val imagesLinearLayout: LinearLayout = findViewById(R.id.imagesLinearLayout)
        val parentRelative: RelativeLayout = findViewById(R.id.parentRelative)

        titleTextView.text = data.title
        coordinatesTextView.text = "Coordinates: ${data.coordinates[0].latitude}, ${data.coordinates[0].longitude}"
        descriptionTextView.text = data.description

        if (data.images.isNotEmpty()) {
            for (imageUrl in data.images) {
                val imageView = ImageView(context)
                imageView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                Glide.with(context).load(imageUrl).into(imageView)
                imagesLinearLayout.addView(imageView)
            }
        }

        // Добавляем новую кнопку "начать путешествие", если у нас есть несколько координат
        if (data.coordinates.size > 1) {
            /*val layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.addRule(RelativeLayout.BELOW, R.id.imagesScrollView)*/
            val startJourneyButton = Button(context).apply {
                text = "Start Journey"
                setOnClickListener {
                    for (i in 0 until data.coordinates.size - 1) {
                        val directionMarker = Marker(MapsActivity.map)
                        directionMarker.position = GeoPoint(
                            data.coordinates[i].latitude,
                            data.coordinates[i].longitude
                        )
                        // надо подобрать угол
                        //val bitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.walking_direction)
                        /*val directionMarkerIcon = resources.getDrawable(R.drawable.walking_direction, null)
                        val bitmap = (directionMarkerIcon as BitmapDrawable).bitmap
                        val matrix = Matrix()
                        matrix.postRotate(
                            bearingBetweenLocations(
                                data.coordinates[i].latitude,
                                data.coordinates[i].latitude,
                                data.coordinates[i + 1].latitude,
                                data.coordinates[i + 1].latitude
                            )
                        )
                        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        val drawable: Drawable = BitmapDrawable(resources, rotatedBitmap)
                        directionMarker.icon = drawable*/
                        directionMarker.icon = resources.getDrawable(R.drawable.walking_direction)
                        MapsActivity.map.overlays.add(directionMarker)
                    }

                    /* Последний маркер является финишем, поэтому мы используем другую иконку */
                    val finishMarker = Marker(MapsActivity.map)
                    finishMarker.position = GeoPoint(
                        data.coordinates.last().longitude,
                        data.coordinates.last().longitude
                    )
                    finishMarker.icon = resources.getDrawable(R.drawable.walking_finish)
                    MapsActivity.map.overlays.add(finishMarker)

                    // обновление карты
                    MapsActivity.map.invalidate()

                    // Закрываем окошко после начала "путешествия"
                    dismiss()
                }
                setBackgroundResource(R.drawable.startjourneybutton_bg)
                val layoutParams = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
                layoutParams.addRule(RelativeLayout.BELOW, R.id.imagesScrollView)
                this.layoutParams = layoutParams
            }
            //startJourneyButton.layoutParams = layoutParams
            parentRelative.addView(startJourneyButton)
        }
    }

    companion object {
        fun bearingBetweenLocations(
            lat1: Double,
            long1: Double,
            lat2: Double,
            long2: Double
        ): Float {
            val brng = Math.atan2(
                Math.sin(long2 - long1) * Math.cos(lat2),
                Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(long2 - long1)
            )
            return Math.toDegrees((brng + 2 * Math.PI) % (2 * Math.PI)).toFloat()
        }
    }
}
