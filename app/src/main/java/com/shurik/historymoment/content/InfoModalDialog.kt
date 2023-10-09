package com.shurik.historymoment.content

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.shurik.historymoment.R

class InfoModalDialog(context: Context, private val data: InfoModalData) : Dialog(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.info_modal)
        setupData()
    }

    private fun setupData() {
        val titleTextView: TextView = findViewById(R.id.titleTextView)
        val coordinatesTextView: TextView = findViewById(R.id.coordinatesTextView)
        val descriptionTextView: TextView = findViewById(R.id.descriptionTextView)
        val imagesLinearLayout: LinearLayout = findViewById(R.id.imagesLinearLayout)

        titleTextView.text = data.title
        coordinatesTextView.text = "Coordinates: ${data.coordinates.latitude}, ${data.coordinates.longitude}"
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
    }
}
