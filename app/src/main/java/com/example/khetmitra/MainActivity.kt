package com.example.khetmitra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.mlkit.nl.translate.TranslateLanguage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : BaseActivity() {

    private lateinit var adapter: DashboardAdapter
    private val dashboardItems = ArrayList<DataModels>()
    private var currentLangCode = TranslateLanguage.ENGLISH
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Default to Mumbai coordinates if GPS fails
    private val DEFAULT_CITY = "19.07,72.87"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        TranslationHelper.initTranslations(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        setupInitialData()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val spacingInPixels = (5 * resources.displayMetrics.density).toInt()
        recyclerView.addItemDecoration(VerticalSpacingItemDecoration(spacingInPixels))

        adapter = DashboardAdapter(dashboardItems) { selectedItem ->
            val title = selectedItem.title
            if (title == t("Weather") || title == "Weather") {
                startActivity(Intent(this, WeatherActivity::class.java))
            } else if (title == t("Market") || title == "Market") {
                startActivity(Intent(this, MarketActivity::class.java))
            } else if (title == t("Chat") || title == "Chat") {
                startActivity(Intent(this, ChatbotActivity::class.java))
            }
        }
        recyclerView.adapter = adapter
        checkLocationPermissionAndFetch()
        setupLanguageSpinner()
    }

    private fun checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getUserLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getUserLocation()
        } else {
            fetchWeather(DEFAULT_CITY)
        }
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latLon = "${location.latitude},${location.longitude}"
                fetchWeather(latLon)
            } else {
                fetchWeather(DEFAULT_CITY)
            }
        }.addOnFailureListener {
            fetchWeather(DEFAULT_CITY)
        }
    }

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    private fun d(num: String): String {
        return TranslationHelper.convertDigits(num, currentLangCode)
    }

    private fun setupInitialData() {
        dashboardItems.clear()

        val weatherSubtitle = "${t("Loading")}..."
        val plansSubtitle = "${d("3")} ${t("tasks for today")}"
        val chatSubtitle = "${d("2")} ${t("new messages")}"
        val marketSubtitle = "${t("Up by")} ${d("10")}%"

        // Default icon before loading
        dashboardItems.add(DataModels(t("Weather"), weatherSubtitle, R.drawable.ic_weather))
        dashboardItems.add(DataModels(t("Plans"), plansSubtitle, R.drawable.ic_plans))
        dashboardItems.add(DataModels(t("Chat"), chatSubtitle, R.drawable.ic_chat))
        dashboardItems.add(DataModels(t("Market"), marketSubtitle, R.drawable.ic_market))
    }
    private fun fetchWeather(query: String) {
        var lat: Double
        var lon: Double

        try {
            val parts = query.split(",")
            lat = parts[0].trim().toDouble()
            lon = parts[1].trim().toDouble()
        } catch (e: Exception) {
            lat = 19.07
            lon = 72.87
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)
        service.getForecast(lat, lon).enqueue(object : Callback<OpenMeteoResponse> {
            override fun onResponse(call: Call<OpenMeteoResponse>, response: Response<OpenMeteoResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!

                    val currentTemp = data.current.temperature_2m
                    val weatherCode = data.current.weathercode
                    val isDay = data.current.is_day

                    val rawCondition = getConditionText(weatherCode)
                    val tempText = currentTemp.toInt().toString()

                    val iconRes = getIconForCondition(rawCondition, isDay)
                    val manualTranslation = TranslationHelper.getManualTranslation(rawCondition, currentLangCode)

                    if (manualTranslation != null) {
                        updateWeatherCard(manualTranslation, tempText, iconRes)
                    } else {
                        translateWithMLKit(rawCondition) { translatedText ->
                            updateWeatherCard(translatedText, tempText, iconRes)
                        }
                    }
                }
            }

            override fun onFailure(call: Call<OpenMeteoResponse>, t: Throwable) {
                Log.e("MainActivity", "Weather fetch failed: ${t.message}", t)
            }
        })
    }

    private fun getConditionText(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Cloudy"
            45 -> "Mist"
            48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Sleet"
            61, 63, 65 -> "Rain"
            66, 67 -> "Sleet"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain"
            85, 86 -> "Snow"
            95 -> "Thunderstorm"
            96, 99 -> "Hail"
            else -> "Unknown"
        }
    }

    private fun updateWeatherCard(condition: String, temp: String, iconRes: Int) {
        val newSubtitle = "$condition, ${d(temp)}${t("°C")}"

        if (dashboardItems.isNotEmpty()) {
            dashboardItems[0] = DataModels(t("Weather"), newSubtitle, iconRes)
            adapter.notifyItemChanged(0)
        }
    }

    private fun translateWithMLKit(text: String, callback: (String) -> Unit) {
        if (currentLangCode == TranslateLanguage.ENGLISH) {
            callback(text)
            return
        }

        val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(currentLangCode)
            .build()
        val client = com.google.mlkit.nl.translate.Translation.getClient(options)

        client.downloadModelIfNeeded().addOnSuccessListener {
            client.translate(text).addOnSuccessListener { result ->
                callback(result)
            }.addOnFailureListener {
                callback(text)
            }
        }.addOnFailureListener {
            callback(text)
        }
    }

    private fun getIconForCondition(conditionRaw: String, isDay: Int = 1): Int {
        val text = conditionRaw.lowercase()
        return when {
            // Clear / Sunny
            text.contains("clear") || text.contains("sunny") -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night

            // Clouds
            text.contains("partly") -> if (isDay == 1) R.raw.partly_cloudy_day else R.raw.partly_cloudy_night
            text.contains("cloudy") -> R.raw.cloudy
            text.contains("overcast") -> R.raw.overcast

            // Atmosphere
            text.contains("mist") -> R.raw.mist
            text.contains("fog") -> R.raw.fog
            text.contains("haze") -> R.raw.haze
            text.contains("dust") -> R.raw.dust

            // Rain / Drizzle
            text.contains("drizzle") -> R.raw.drizzle
            text.contains("sleet") -> R.raw.sleet

            // Thunderstorms
            text.contains("thunder") && text.contains("rain") -> R.raw.thunderstorms_rain
            text.contains("hail") -> R.raw.hail
            text.contains("thunder") -> R.raw.thunderstorms

            // Rain / Snow
            text.contains("rain") -> R.raw.rain
            text.contains("snow") -> R.raw.snow

            // Extreme
            text.contains("tornado") -> R.raw.tornado
            text.contains("hurricane") -> R.raw.hurricane
            text.contains("wind") -> R.raw.wind

            else -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
        }
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.languageSpinner)
        val languages = listOf("English", "हिंदी", "मराठी", "ગુજરાતી", "ಕನ್ನಡ", "தமிழ்", "తెలుగు", "বাংলা")
        val codes = listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.HINDI,
            TranslateLanguage.MARATHI,
            TranslateLanguage.GUJARATI,
            TranslateLanguage.KANNADA,
            TranslateLanguage.TAMIL,
            TranslateLanguage.TELUGU,
            TranslateLanguage.BENGALI
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val index = codes.indexOf(currentLangCode)
        if (index >= 0) spinner.setSelection(index, false)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCode = codes[position]
                if (selectedCode != currentLangCode) {
                    val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                    prefs.edit { putString("Language", selectedCode) }
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
}