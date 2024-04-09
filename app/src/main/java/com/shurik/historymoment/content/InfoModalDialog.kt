package com.shurik.historymoment.content

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.shurik.historymoment.R
import java.lang.Math.abs

class InfoModalDialog(context: Context, private val data: InfoModalData) : BottomSheetDialog(context, R.style.AppBottomSheetDialogTheme) {

    private lateinit var imagesViewPager: ViewPager2
    private lateinit var handler: Handler
    private lateinit var imageList: ArrayList<Int>
    private lateinit var adapter: ImagePagerAdapter

    private lateinit var textToSpeechSystem: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.info_modal)
        window?.setGravity(Gravity.BOTTOM)
        window?.setWindowAnimations(R.style.DialogAnimation)
        //window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setElevation(16f)
        setCanceledOnTouchOutside(true)

        setupData()

    }

    var isSpeechPlaying = false
    var pausedPosition = 0 // переменная для сохранения позиции остановки озвучки


    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun setupData() {
        val titleTextView: TextView? = findViewById(R.id.titleTextView)
        val coordinatesTextView: TextView? = findViewById(R.id.coordinatesTextView)
        val descriptionTextView: TextView? = findViewById(R.id.descriptionTextView)
        imagesViewPager = findViewById(R.id.imagesViewPager)!!
        val speechButton: Button? = findViewById(R.id.speechButton)
        val parentRelative: FrameLayout? = findViewById(R.id.parentRelative)

        titleTextView?.text = data.title
        coordinatesTextView?.text = "Coordinates: ${data.coordinates[0].latitude}, ${data.coordinates[0].longitude}"
        descriptionTextView?.text = data.description

        adapter = ImagePagerAdapter(data.images, imagesViewPager)
        imagesViewPager.adapter = adapter



        // Создание уведомления
        val notificationManager =
            ContextCompat.getSystemService(context, NotificationManager::class.java) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Channel Name"
            val descriptionText = "Channel Description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("channelId", name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "channelId")
            .setContentTitle("Озвучка активна")
            .setContentText("Текст озвучивается")
            .setSmallIcon(R.drawable.speaker_icon)
            .build()

        // Запуск уведомления
        notificationManager.notify(1, notification)


        speechButton?.setOnClickListener {
            if (isSpeechPlaying) {
                pausedPosition = textToSpeechSystem.stop() // остановка озвучки, если она уже играет
                isSpeechPlaying = false
            } else {
                if (pausedPosition > 0) {
                    //val textToSay = data.description.substring(pausedPosition) // получение подстроки для продолжения озвучивания
                    val resultArray = splitString(data.description.substring(pausedPosition), 1000)
                    for (textToSay in resultArray) {
                        textToSpeechSystem.speak(
                            textToSay,
                            TextToSpeech.QUEUE_ADD,
                            null,
                            null
                        ) // воспроизведение с сохраненной позиции
                    }
                }
                else {
                    textToSpeechSystem = TextToSpeech(context) { status ->
                        if (status == TextToSpeech.SUCCESS) {
//                            val textToSay = data.description
//                            textToSpeechSystem.speak(textToSay, TextToSpeech.QUEUE_ADD, null, null)
                            val resultArray = splitString(data.description.substring(pausedPosition), 1000)
                            for (textToSay in resultArray) {
                                textToSpeechSystem.speak(
                                    textToSay,
                                    TextToSpeech.QUEUE_ADD,
                                    null,
                                    null
                                ) // воспроизведение с сохраненной позиции
                            }
                        }
                    }
                }
                isSpeechPlaying = true
            }
        }
    }

    fun splitString(input: String, maxLength: Int): List<String> {
        val regex = ".{1,$maxLength}".toRegex()
        return regex.findAll(input).map { it.value }.toList()
    }

    class ImagePagerAdapter(private val images: List<String>, private val viewPager2: ViewPager2) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {
        private var currentPosition = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.photos_places, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imagePosition = position % images.size
            Glide.with(holder.imageView)
                .load(images[imagePosition])
                .into(holder.imageView)
        }

        override fun getItemCount(): Int {
            // Мы хотим, чтобы отображалось бесконечное количество изображений, поэтому возвращаем большее значение, чем размер списка изображений
            return Int.MAX_VALUE
        }

        class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.imageView)

        }

        init {
            // Сделаем текущую позицию большим значением, так что позиция может быть отрицательной или слишком большой
            val initialPosition = Int.MAX_VALUE / 2
            viewPager2.post { viewPager2.setCurrentItem(initialPosition, false) }
        }

        fun setCurrentPosition(position: Int) {
            currentPosition = position
            notifyItemChanged(position)
        }
    }


//    class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

}
