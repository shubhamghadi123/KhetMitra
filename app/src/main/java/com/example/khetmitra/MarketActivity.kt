package com.example.khetmitra

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class MarketActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val spinnerMarket = findViewById<Spinner>(R.id.spinnerMarket)

        val marketAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.market_locations,
            R.layout.item_spinner_selected // <--- This makes the text Big and Bold
        )
        // Use standard layout for the dropdown list (when it opens)
        marketAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMarket.adapter = marketAdapter


        // 3. Setup Crop Spinner
        val spinnerCrop = findViewById<Spinner>(R.id.spinnerCrop)

        val cropAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.crop_names,
            R.layout.item_spinner_selected // <--- Reusing the same custom bold layout
        )
        cropAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCrop.adapter = cropAdapter
    }
}