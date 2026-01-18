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

    private var isSummaryExpanded = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkLocationPermissionAndFetch()

        setupSummaryExpandLogic()
    }

    private fun setupSummaryExpandLogic() {
        val headerSummary = findViewById<android.widget.LinearLayout>(R.id.headerSummary)
        val tvSummaryBody = findViewById<TextView>(R.id.tvSummaryBody)
        val btnArrow = findViewById<ImageView>(R.id.btnToggleSummary)

        headerSummary.setOnClickListener {
            isSummaryExpanded = !isSummaryExpanded
            if (isSummaryExpanded) {
                tvSummaryBody.maxLines = Int.MAX_VALUE
                tvSummaryBody.ellipsize = null
                btnArrow.animate().rotation(180f).setDuration(200).start()
            } else {
                tvSummaryBody.maxLines = 2
                tvSummaryBody.ellipsize = android.text.TextUtils.TruncateAt.END
                btnArrow.animate().rotation(0f).setDuration(200).start()
            }
        }
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

                    updateCurrentWeatherUI(weatherData.current, weatherData.forecast.forecastday[0])

                    setupForecastRecycler(weatherData.forecast.forecastday)

                    setupInsightsRecycler(weatherData.forecast.forecastday)
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Toast.makeText(this@WeatherActivity, "Failed to load weather", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateCurrentWeatherUI(current: Current, todayForecast: ForecastDay) {
        val tvCondition = findViewById<TextView>(R.id.tvWeatherCondition)
        val tvTemp = findViewById<TextView>(R.id.tvTempBig)
        val tvFeelsLike = findViewById<TextView>(R.id.tvFeelsLike)
        val ivIcon = findViewById<ImageView>(R.id.iconCurrentWeather)
        val tvHighLow = findViewById<TextView>(R.id.tvHighLow) // New View
        val tvSummaryBody = findViewById<TextView>(R.id.tvSummaryBody) // New View

        // Raw Data
        val rawCondition = current.condition.text
        val rawTemp = "${current.temp_c.toInt()}°C"
        val rawFeelsLike = "${current.feelslike_c.toInt()}°C"
        val rawHigh = "${todayForecast.day.maxtemp_c.toInt()}°"
        val rawLow = "${todayForecast.day.mintemp_c.toInt()}°"

        ivIcon.setImageResource(getIconForCondition(rawCondition))

        // Get Language
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

        if (langCode == TranslateLanguage.ENGLISH) {
            tvCondition.text = rawCondition
            tvTemp.text = rawTemp
            tvFeelsLike.text = "Feels like $rawFeelsLike"
            tvHighLow.text = "↑$rawHigh ↓$rawLow"
            tvSummaryBody.text = generateQuickSummary(current, todayForecast, langCode)
        } else {
            // --- TRANSLATION LOGIC ---

            // 1. Condition
            val translatedCond = TranslationHelper.getManualTranslation(rawCondition, langCode!!) ?: rawCondition
            tvCondition.text = translatedCond

            // 2. Temp
            tvTemp.text = TranslationHelper.convertDigits(rawTemp, langCode)

            // 3. Feels Like
            val prefix = TranslationHelper.getManualTranslation("Feels like", langCode) ?: "Feels like"
            val feelsNum = TranslationHelper.convertDigits(rawFeelsLike, langCode)
            tvFeelsLike.text = "$prefix $feelsNum"

            // 4. High / Low
            val highNum = TranslationHelper.convertDigits(rawHigh, langCode)
            val lowNum = TranslationHelper.convertDigits(rawLow, langCode)
            tvHighLow.text = "↑$highNum ↓$lowNum"

            // 5. Summary
            tvSummaryBody.text = generateQuickSummary(current, todayForecast, langCode)

            // Manual fallback for condition if not in map
            if (TranslationHelper.getManualTranslation(rawCondition, langCode) == null) {
                tvCondition.tag = rawCondition
                TranslationHelper.translateViewHierarchy(tvCondition, langCode) {}
            }
        }
    }

    private fun generateQuickSummary(current: Current, today: ForecastDay, langCode: String?): String {
        fun t(text: String): String {
            if (langCode == null || langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode ?: TranslateLanguage.ENGLISH)
        }

        val sb = StringBuilder()

        val rainChance = today.day.daily_chance_of_rain
        val humidity = current.humidity
        val temp = current.temp_c
        val conditionText = current.condition.text.lowercase().trim()

        val headerKey = when {
            rainChance > 60 -> "Rainy and wet start"
            humidity > 80 && temp > 25 -> "A muggy and warm start"
            conditionText.contains("sunny") -> "A bright and sunny start"
            conditionText.contains("cloud") || conditionText.contains("overcast") -> "A cloudy start"
            conditionText.contains("clear") -> "A clear start"
            else -> "A clear start"
        }

        val translatedHeader = t(headerKey)

        val aqi = current.air_quality?.us_epa_index ?: 1
        val aqiDescKey = if (aqi > 3) "Air quality is unhealthy" else "Air quality is acceptable"
        val translatedAqiDesc = t(aqiDescKey)

        sb.append("$translatedHeader — $translatedAqiDesc.\n\n")

        // AIR QUALITY DETAILS
        if (aqi > 2) {
            sb.append("• ${t("Air quality is poor right now")} — ${t("Consider limiting time outside")}.\n\n")
        } else {
            sb.append("• ${t("Air quality is good")} — ${t("Perfect for outdoor activities")}.\n\n")
        }

        // UV INDEX
        if (current.uv > 6) {
            sb.append("• ${t("High UV levels could pose a risk outdoors")}.\n\n")
        }

        // HUMIDITY
        if (humidity > 70) {
            val dewPoint = d(current.dewpoint_c.toInt())
            sb.append("• ${t("Feels humid")} — ${t("Dew point near")} $dewPoint°.\n\n")
        }

        // TODAY'S HIGH
        val highTemp = d(today.day.maxtemp_c.toInt())
        sb.append("• ${t("Today's high will reach around")} $highTemp°.\n\n")

        // SUNRISE / SUNSET
        fun formatTime(timeStr: String): String {
            var fixedTime = d(timeStr)
            if (timeStr.contains("AM", ignoreCase = true)) {
                val amTranslation = t("AM")
                fixedTime = fixedTime.replace("AM", "", ignoreCase = true).trim() + " " + amTranslation
            } else if (timeStr.contains("PM", ignoreCase = true)) {
                val pmTranslation = t("PM")
                fixedTime = fixedTime.replace("PM", "", ignoreCase = true).trim() + " " + pmTranslation
            }
            return fixedTime
        }

        val sunrise = formatTime(today.astro.sunrise)
        val sunset = formatTime(today.astro.sunset)

        sb.append("• ${t("Sunrise")}: $sunrise, ${t("Sunset")}: $sunset")

        return sb.toString()
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

        // 1. Get Current Language
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        fun getText(englishText: String): String {
            if (langCode == TranslateLanguage.ENGLISH) return englishText
            return TranslationHelper.getManualTranslation(englishText, langCode) ?: englishText
        }

        fun num(number: Any): String {
            return TranslationHelper.convertDigits(number.toString(), langCode)
        }

        // --- INSIGHT 1: TODAY'S RAIN ---
        val todayRain = days[0].day.daily_chance_of_rain

        if (todayRain > 50) {
            val part1 = getText("High chance of rain")
            val part2 = getText("Delay spraying pesticides")

            val finalMsg = "$part1 (${num(todayRain)}%). $part2"

            insightList.add(InsightModel(
                getText("Rainfall Alert"),
                finalMsg,
                R.drawable.rain_image
            ))
        } else {
            val part1 = getText("Low chance of rain")
            val part2 = getText("Good time for irrigation")
            val finalMsg = "$part1. $part2"

            insightList.add(InsightModel(
                getText("Today's Activity"),
                finalMsg,
                R.drawable.soilirrigation_image
            ))
        }

        // --- INSIGHT 2: FUTURE LOOKAHEAD ---
        var upcomingRainDay: String? = null
        for (i in 1..3) {
            if (i < days.size && days[i].day.daily_chance_of_rain > 60) {
                upcomingRainDay = getDayName(days[i].date)
                break
            }
        }

        if (upcomingRainDay != null) {
            val translatedDay = if (langCode != TranslateLanguage.ENGLISH) {
                TranslationHelper.getManualTranslation(upcomingRainDay, langCode) ?: upcomingRainDay
            } else upcomingRainDay

            val part1 = getText("Heavy rain expected on")
            val part2 = getText("Plan drainage")

            insightList.add(InsightModel(
                getText("Upcoming Weather"),
                "$part1 $translatedDay. $part2",
                R.drawable.rain_image
            ))
        } else {
            if (todayRain < 30) {
                val part1 = getText("No rain in the next 3 days")
                val part2 = getText("Perfect time to irrigate")

                insightList.add(InsightModel(
                    getText("Irrigation Advice"),
                    "$part1. $part2",
                    R.drawable.irrigation_image
                ))
            }
        }

        // --- INSIGHT 3: SOIL MOISTURE ---
        if (todayRain > 70) {
            val part1 = getText("Soil is likely wet")
            val part2 = getText("Avoid heavy machinery")

            insightList.add(InsightModel(
                getText("Soil Status"),
                "$part1. $part2",
                R.drawable.wetsoil_image
            ))
        } else {
            insightList.add(InsightModel(
                getText("Soil Status"),
                getText("Soil moisture is likely stable"),
                R.drawable.soilmoisture_image
            ))
        }

        // --- SETUP RECYCLER ---
        val recycler = findViewById<RecyclerView>(R.id.recyclerInsights)

        if (recycler.itemDecorationCount == 0) {
            recycler.addItemDecoration(VerticalSpacingItemDecoration((12 * resources.displayMetrics.density).toInt()))
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = InsightAdapter(insightList)

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