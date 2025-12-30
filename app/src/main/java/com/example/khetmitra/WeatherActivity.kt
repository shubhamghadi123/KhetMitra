package com.example.khetmitra

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class WeatherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        // 1. Setup the Back Button Logic
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish() // This closes the current activity and returns to the previous one
        }

        // 2. Setup the 5-Day Forecast List
        setupForecastRecycler()
        setupInsightsRecycler()
    }

    private fun setupForecastRecycler() {
        // Prepare the data (You can replace the icons with R.drawable.your_icon later)
        val forecastList = listOf(
            ForecastModel("Mon", R.drawable.ic_weather, "25°C"),
            ForecastModel("Tue", R.drawable.ic_weather, "23°C"),
            ForecastModel("Wed", R.drawable.ic_weather, "23°C"),
            ForecastModel("Thu", R.drawable.ic_weather, "23°C"),
            ForecastModel("Fri", R.drawable.ic_weather, "23°C"),
            ForecastModel("Sat", R.drawable.ic_weather, "23°C"),
            ForecastModel("Sun", R.drawable.ic_weather, "23°C")
        )

        // Find the RecyclerView from the XML
        val recyclerForecast = findViewById<RecyclerView>(R.id.recyclerForecast)

        val spacingInPixels = (10 * resources.displayMetrics.density).toInt()
        recyclerForecast.addItemDecoration(HorizontalSpacingItemDecoration(spacingInPixels))


        // Set it to scroll Horizontally
        recyclerForecast.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        // Attach the Adapter
        recyclerForecast.adapter = ForecastAdapter(forecastList)
    }

    private fun setupInsightsRecycler() {
        val insightList = listOf(
            InsightModel("Rainfall Probability", "Low chance of rain in the next 5 days"),
            InsightModel("Soil Moisture", "Optimal soil moisture for planting"),
            InsightModel("Pest Alert", "No pest activity detected in your region")
        )

        val recyclerInsights = findViewById<RecyclerView>(R.id.recyclerInsights)
        recyclerInsights.layoutManager = LinearLayoutManager(this)

        // Add Vertical Spacing (Reuse the class from MainActivity)
        val spacingInPixels = (20 * resources.displayMetrics.density).toInt()
        recyclerInsights.addItemDecoration(VerticalSpacingItemDecoration(spacingInPixels))

        recyclerInsights.adapter = InsightAdapter(insightList)
    }
}