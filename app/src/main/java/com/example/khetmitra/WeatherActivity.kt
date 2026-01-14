package com.example.khetmitra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.text.SimpleDateFormat
import java.util.Locale

class WeatherActivity : BaseActivity() {

    private val API_KEY = BuildConfig.WEATHER_API_KEY // Your WeatherAPI Key
    private var currentLocationQuery = "Mumbai" // Default city if GPS fails
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Start the chain: Check Permission -> Get GPS -> Call API
        checkLocationPermissionAndFetch()
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
            Toast.makeText(this, "Location denied. Showing default city.", Toast.LENGTH_SHORT).show()
            fetchWeatherData(currentLocationQuery)
        }
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                // WeatherAPI accepts "lat,lon" string directly
                val latLon = "${location.latitude},${location.longitude}"
                fetchWeatherData(latLon)
            } else {
                fetchWeatherData(currentLocationQuery)
            }
        }.addOnFailureListener {
            fetchWeatherData(currentLocationQuery)
        }
    }

    private fun fetchWeatherData(query: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)

        service.getForecast(API_KEY, query, 7).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val weatherData = response.body()!!

                    // 1. Update Big Card
                    updateCurrentWeatherUI(weatherData.current)

                    // 2. Update Forecast List
                    setupForecastRecycler(weatherData.forecast.forecastday)

                    // 3. Update Insights List
                    setupInsightsRecycler(weatherData.forecast.forecastday)
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Toast.makeText(this@WeatherActivity, "Failed to load weather", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateCurrentWeatherUI(current: Current) {
        val tvCondition = findViewById<TextView>(R.id.tvWeatherCondition)
        val tvTemp = findViewById<TextView>(R.id.tvTempBig)
        val tvFeelsLike = findViewById<TextView>(R.id.tvFeelsLike)
        val ivIcon = findViewById<ImageView>(R.id.iconCurrentWeather)

        // 1. Raw Data (English)
        val rawCondition = current.condition.text
        val rawTemp = "${current.temp_c.toInt()}°C"
        val rawFeelsLike = "${current.feelslike_c.toInt()}°C"

        // 2. Set Icon
        ivIcon.setImageResource(getIconForCondition(rawCondition))

        // 3. TRANSLATION LOGIC (Crucial for "Feels like" issue)
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

        if (langCode == TranslateLanguage.ENGLISH) {
            tvCondition.text = rawCondition
            tvTemp.text = rawTemp
            tvFeelsLike.text = "Feels like $rawFeelsLike"
        } else {
            val translatedCond = TranslationHelper.getManualTranslation(rawCondition, langCode!!) ?: rawCondition
            tvCondition.text = translatedCond

            tvTemp.text = TranslationHelper.convertDigits(rawTemp, langCode)

            val prefix = TranslationHelper.getManualTranslation("Feels like", langCode) ?: "Feels like"
            val feelsNum = TranslationHelper.convertDigits(rawFeelsLike, langCode)
            tvFeelsLike.text = "$prefix $feelsNum"

            // Clear tags to prevent overwriting
            tvCondition.tag = null
            tvTemp.tag = null
            tvFeelsLike.tag = null

            // Fallback for condition
            if (TranslationHelper.getManualTranslation(rawCondition, langCode) == null) {
                tvCondition.tag = rawCondition
                TranslationHelper.translateViewHierarchy(tvCondition, langCode) {}
            }
        }
    }

    private fun setupForecastRecycler(days: List<ForecastDay>) {
        val forecastList = ArrayList<ForecastModel>()
        for (day in days) {
            val dayName = getDayName(day.date)
            val temp = "${day.day.avgtemp_c.toInt()}°C"
            val icon = getIconForCondition(day.day.condition.text)
            forecastList.add(ForecastModel(dayName, icon, temp))
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerForecast)
        if (recycler.itemDecorationCount == 0) {
            recycler.addItemDecoration(HorizontalSpacingItemDecoration((10 * resources.displayMetrics.density).toInt()))
        }
        recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recycler.adapter = ForecastAdapter(forecastList)
        checkAndTranslateList(recycler)
    }

    private fun setupInsightsRecycler(days: List<ForecastDay>) {
        val insightList = mutableListOf<InsightModel>()

        // 1. TODAY'S INSIGHT
        val todayRain = days[0].day.daily_chance_of_rain
        if (todayRain > 50) {
            insightList.add(InsightModel(
                "Rainfall Alert",
                "High chance of rain today ($todayRain%). Delay spraying pesticides.",
                R.drawable.ic_rainyweather // Use your rain image
            ))
        } else {
            insightList.add(InsightModel(
                "Today's Activity",
                "Conditions are clear. Good for field work.",
                R.drawable.ic_sunnyweather // Use your sun/field image
            ))
        }

        // 2. FUTURE LOOKAHEAD
        var upcomingRainDay: String? = null
        for (i in 1..3) {
            if (i < days.size && days[i].day.daily_chance_of_rain > 60) {
                upcomingRainDay = getDayName(days[i].date)
                break
            }
        }

        if (upcomingRainDay != null) {
            insightList.add(InsightModel(
                "Upcoming Weather",
                "Heavy rain expected on $upcomingRainDay. Plan drainage.",
                R.drawable.ic_rainyweather // Rain image
            ))
        } else {
            if (todayRain < 30) {
                insightList.add(InsightModel(
                    "Irrigation Advice",
                    "No rain in the next 3 days. Perfect time to irrigate.",
                    R.drawable.ic_clearweather // Water/Irrigation image
                ))
            }
        }

        // 3. SOIL MOISTURE
        if (todayRain > 70) {
            insightList.add(InsightModel(
                "Soil Status",
                "Soil is likely wet. Avoid heavy machinery.",
                R.drawable.ic_cloudyweather // Mud/Soil image
            ))
        } else {
            insightList.add(InsightModel(
                "Soil Status",
                "Soil moisture is likely stable.",
                R.drawable.ic_clearweather // Plant/Soil image
            ))
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerInsights)

        if (recycler.itemDecorationCount == 0) {
            recycler.addItemDecoration(VerticalSpacingItemDecoration((12 * resources.displayMetrics.density).toInt()))
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = InsightAdapter(insightList)

        checkAndTranslateList(recycler)
    }

    // --- Helpers ---
    private fun getDayName(dateString: String): String {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val outFmt = SimpleDateFormat("EEE", Locale.ENGLISH)
        return try { outFmt.format(inFmt.parse(dateString)!!) } catch (e: Exception) { dateString }
    }

    private fun getIconForCondition(condition: String): Int {
        val text = condition.lowercase()
        return when {
            text.contains("sunny") -> R.drawable.ic_sunnyweather
            text.contains("rain") -> R.drawable.ic_rainyweather
            text.contains("cloud") -> R.drawable.ic_cloudyweather
            text.contains("clear") -> R.drawable.ic_clearweather
            text.contains("overcast") -> R.drawable.ic_overcastweather
            else -> R.drawable.ic_weather
        }
    }

    private fun checkAndTranslateList(view: android.view.View) {
        view.post {
            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val lang = prefs.getString("Language", TranslateLanguage.ENGLISH)
            if (lang != TranslateLanguage.ENGLISH) {
                TranslationHelper.translateViewHierarchy(view, lang!!) {}
            }
        }
    }
}