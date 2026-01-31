package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
import androidx.core.graphics.toColorInt

class WeatherActivity : BaseActivity() {

    private var currentLocationQuery = "19.0760,72.8777" // Default city if GPS fails
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        swipeRefreshLayout.setOnRefreshListener {
            checkLocationPermissionAndFetch()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val recyclerHourly = findViewById<RecyclerView>(R.id.recyclerHourly)
        recyclerHourly.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerHourly.adapter = HourlyAdapter(emptyList())

        val recyclerForecast = findViewById<RecyclerView>(R.id.recyclerForecast)
        recyclerForecast.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerForecast.adapter = ForecastAdapter(emptyList())

        setupSummaryExpandLogic()
    }

    override fun onResume() {
        super.onResume()
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            swipeRefreshLayout.isRefreshing = false
            return
        }
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latLon = "${location.latitude},${location.longitude}"
                fetchWeatherData(latLon)
            } else {
                getLastKnownLocation()
            }
        }.addOnFailureListener {
            getLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latLon = "${location.latitude},${location.longitude}"
                Toast.makeText(this, "Using saved location", Toast.LENGTH_SHORT).show()
                fetchWeatherData(latLon)
            } else {
                Toast.makeText(this, "GPS Signal Lost. Showing Mumbai.", Toast.LENGTH_LONG).show()
                fetchWeatherData(currentLocationQuery)
            }
        }
    }

    private fun getAddressName(lat: Double, lon: Double): String {
        var cityName = "Unknown Location"
        try {
            val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                cityName = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Unknown"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cityName
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

    private fun fetchWeatherData(query: String) {
        loadingOverlay.visibility = android.view.View.VISIBLE
        // Coordinates for Mumbai
        var lat = 19.07
        var lon = 72.87
        try {
            val parts = query.split(",")
            lat = parts[0].trim().toDouble()
            lon = parts[1].trim().toDouble()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Weather
        val weatherRetrofit = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val weatherService = weatherRetrofit.create(WeatherService::class.java)

        // AQI
        val aqiRetrofit = Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val aqiService = aqiRetrofit.create(AirQualityService::class.java)

        weatherService.getForecast(lat, lon).enqueue(object : Callback<OpenMeteoResponse> {
            override fun onResponse(call: Call<OpenMeteoResponse>, response: Response<OpenMeteoResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val weatherData = response.body()!!

                    aqiService.getAirQuality(lat, lon).enqueue(object : Callback<AirQualityResponse> {
                        override fun onResponse(call2: Call<AirQualityResponse>, response2: Response<AirQualityResponse>) {
                            val rawAqi = response2.body()?.current?.us_aqi ?: 50
                            val epaIndex = convertAqiToEpa(rawAqi)
                            currentWeatherUI(weatherData, epaIndex, lat, lon)
                            swipeRefreshLayout.isRefreshing = false
                        }
                        override fun onFailure(call2: Call<AirQualityResponse>, t: Throwable) {
                            currentWeatherUI(weatherData, 2, lat, lon)
                            swipeRefreshLayout.isRefreshing = false
                        }
                    })
                }
                else {
                    loadingOverlay.visibility = android.view.View.GONE
                    swipeRefreshLayout.isRefreshing = false
                }
            }
            override fun onFailure(call: Call<OpenMeteoResponse>, t: Throwable) {
                loadingOverlay.visibility = android.view.View.GONE
                Toast.makeText(this@WeatherActivity, "Failed to load weather", Toast.LENGTH_SHORT).show()
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    private fun convertAqiToEpa(rawAqi: Int): Int {
        return when {
            rawAqi <= 50 -> 1  // Good
            rawAqi <= 100 -> 2 // Moderate
            rawAqi <= 150 -> 3 // Unhealthy for Sensitive
            rawAqi <= 200 -> 4 // Unhealthy
            rawAqi <= 300 -> 5 // Very Unhealthy
            else -> 6          // Hazardous
        }
    }

    private fun updateAqiPill(tvAqi: TextView, aqiIndex: Int, t: (String) -> String) {
        val (status, colorHex) = when (aqiIndex) {
            1 -> Pair("Good", "#4CAF50")      // Green
            2 -> Pair("Moderate", "#FFC107")  // Yellow/Amber
            3 -> Pair("Sensitive", "#FF9800") // Orange
            4 -> Pair("Unhealthy", "#FF5252") // Red
            5 -> Pair("Very Bad", "#9C27B0")  // Purple
            else -> Pair("Hazardous", "#B71C1C") // Maroon
        }

        tvAqi.text = "${t("AQI")}: ${t(status)}"

        try {
            tvAqi.background.setTint(colorHex.toColorInt())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun currentWeatherUI(data: OpenMeteoResponse, aqiIndex: Int, lat: Double, lon: Double) {

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        fun t(text: String): String {
            if (langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode)
        }

        weeklyForecastUI(data.daily)
        hourlyForecastUI(data.hourly)
        agriculturalInsightsUI(data)

        val current = data.current
        val todayHigh = data.daily.temperature_2m_max.firstOrNull() ?: 0.0
        val todayLow = data.daily.temperature_2m_min.firstOrNull() ?: 0.0
        val conditionText = getConditionText(current.weathercode)

        val tvCondition = findViewById<TextView>(R.id.tvWeatherCondition)
        val tvTemp = findViewById<TextView>(R.id.tvTempBig)
        val tvFeelsLike = findViewById<TextView>(R.id.tvFeelsLike)
        val lottieIcon = findViewById<com.airbnb.lottie.LottieAnimationView>(R.id.iconCurrentWeather)
        val tvHighLow = findViewById<TextView>(R.id.tvHighLow)
        val tvSummaryBody = findViewById<TextView>(R.id.tvSummaryBody)
        val tvLocation = findViewById<TextView>(R.id.tvLocation)

        val tvAqi = findViewById<TextView>(R.id.tvAqi)
        val tvHumidity = findViewById<TextView>(R.id.tvHumidity)
        val tvWind = findViewById<TextView>(R.id.tvWind)
        val tvDewPoint = findViewById<TextView>(R.id.tvDewPoint)

        lottieIcon.setAnimation(getIconForCondition(conditionText, current.is_day))
        lottieIcon.playAnimation()

        tvCondition.text = t(conditionText)

        val tempNum = d(current.temperature_2m.toInt())
        val unit = t("°C")
        tvTemp.text = "${d(tempNum)}$unit"

        val feelsPrefix = t("Feels Like")
        val feelsNum = d(current.apparent_temperature.toInt())
        tvFeelsLike.text = "$feelsPrefix $feelsNum$unit"

        val rawHigh = todayHigh.toInt()
        val rawLow = todayLow.toInt()
        tvHighLow.text = "↑${d(rawHigh)}° ↓${d(rawLow)}°"

        val cityName = getAddressName(lat, lon)
        TranslationHelper.smartTranslate(cityName, langCode) { translatedCity ->
            tvLocation.text = translatedCity
            loadingOverlay.visibility = android.view.View.GONE
        }

        tvHumidity.text = "${d(current.relative_humidity_2m)}%"

        val windSpeed = d(current.wind_speed_10m.toInt())
        tvWind.text = "$windSpeed ${t("km/h")}"

        val dewPoint = d(current.dew_point_2m.toInt())
        tvDewPoint.text = "$dewPoint°"

        updateAqiPill(tvAqi, aqiIndex, ::t)

        val initialSummary = generateQuickSummary(data, aqiIndex, langCode)
        tvSummaryBody.text = initialSummary

        if (langCode != TranslateLanguage.ENGLISH) {
            if (containsEnglish(initialSummary)) {
                processMixedSummary(initialSummary, langCode) { finalMixedText ->
                    tvSummaryBody.text = finalMixedText
                }
            }
        }
    }

    private fun generateQuickSummary(data: OpenMeteoResponse, aqiIndex: Int, langCode: String?): String {
        fun t(text: String): String {
            if (langCode == null || langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode ?: TranslateLanguage.ENGLISH)
        }

        try {
            val sb = StringBuilder()

            val rainChance = data.daily.precipitation_probability_max.firstOrNull() ?: 0
            val uvMax = data.daily.uv_index_max.firstOrNull() ?: 0.0
            val humidity = data.current.relative_humidity_2m
            val temp = data.current.temperature_2m
            val dewPoint = data.current.dew_point_2m
            val soilMoisture = data.hourly.soil_moisture_3_9cm.firstOrNull() ?: 0.0
            val soilTemp = data.hourly.soil_temperature_6cm.firstOrNull() ?: 0.0

            var headerPart1 = ""
            var headerPart2 = ""

            if (humidity > 80 && temp > 24) {
                headerPart1 = t("Expect a humid, clingy morning")
                headerPart2 = t("sweaty conditions expected")
            } else if (rainChance > 50) {
                headerPart1 = t("Expect a rainy, wet start")
                headerPart2 = t("Carrying an umbrella is advised")
            } else {
                headerPart1 = t("Expect a clear, bright start")
                headerPart2 = t("Perfect for outdoor tasks")
            }

            // AQI Status
            val aqiStatus = if (aqiIndex > 3) t("Air quality may be unhealthy") else t("Air quality is acceptable")

            // Combine Header
            sb.append("$headerPart1 — $headerPart2.\n$aqiStatus.\n")

            // AQI Warning
            if (aqiIndex > 3) {
                sb.append("• ${t("Air quality is poor")} — ${t("Consider limiting time outside")}\n")
            }

            // UV Warning
            if (uvMax > 5) {
                sb.append("• ${t("High UV levels could pose a risk outdoors")}\n")
            }

            // Humidity & Dew Point
            if (humidity > 70) {
                sb.append("• ${t("Feels humid later")} — ${t("Dew point near")} ${d(dewPoint.toInt())}°\n")
            }

            // SUNRISE, SUNSET & DAY LENGTH
            val sunriseRaw = data.daily.sunrise.firstOrNull()
            val sunsetRaw = data.daily.sunset.firstOrNull()

            if (sunriseRaw != null && sunsetRaw != null) {
                val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.getDefault())

                val dateRise = isoFormat.parse(sunriseRaw)
                val dateSet = isoFormat.parse(sunsetRaw)

                fun getSmartTime(date: java.util.Date): String {
                    if (langCode == TranslateLanguage.ENGLISH) {
                        return java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(date)
                    }
                    val cal = java.util.Calendar.getInstance()
                    cal.time = date
                    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)

                    val periodKey = when (hour) {
                        in 5..11 -> "Morning"    // e.g. "सकाळी"
                        in 12..16 -> "Afternoon" // e.g. "दुपारी"
                        in 17..19 -> "Evening"   // e.g. "संध्याकाळी"
                        else -> "Night"          // e.g. "रात्री"
                    }

                    val rawTime = java.text.SimpleDateFormat("h:mm", java.util.Locale.ENGLISH).format(date)

                    return "${d(rawTime)} ${t(periodKey)}"
                }

                if (dateRise != null && dateSet != null) {
                    val sRise = getSmartTime(dateRise)
                    val sSet = getSmartTime(dateSet)

                    sb.append("• ${t("Sunrise")}: $sRise, ${t("Sunset")}: $sSet\n")

                    val diff = dateSet.time - dateRise.time
                    val hours = (diff / (1000 * 60 * 60)).toInt()
                    val minutes = ((diff / (1000 * 60)) % 60).toInt()

                    val hrStr = t("hrs")
                    val minStr = t("mins")
                    sb.append("• ${t("Day length")}: ${d(hours)} $hrStr ${d(minutes)} $minStr\n")
                }
            }

            // Soil Moisture
            if (soilMoisture > 0.35) {
                sb.append("• ${t("Soil is wet")} — ${t("Avoid heavy machinery")}\n")
            } else if (soilMoisture < 0.15) {
                sb.append("• ${t("Soil is dry")} — ${t("Consider irrigation")}\n")
            }

            // Soil Health (Temperature)
            sb.append("• ${t("Soil Temperature")}: ${d(soilTemp.toInt())}${t("°C")}")

            return sb.toString().trim()

        } catch (e: Exception) {
            e.printStackTrace()
            return t("Weather data is currently unavailable.")
        }
    }

    private fun setupSummaryExpandLogic() {
        val headerSummary = findViewById<android.widget.LinearLayout>(R.id.headerSummary)
        val tvSummaryBody = findViewById<TextView>(R.id.tvSummaryBody)
        val btnArrow = findViewById<ImageView>(R.id.btnToggleSummary)
        var isExpanded = false

        tvSummaryBody.maxLines = 2
        tvSummaryBody.ellipsize = null

        headerSummary.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                tvSummaryBody.maxLines = Int.MAX_VALUE
                btnArrow.animate().rotation(180f).setDuration(300).start()
            } else {
                tvSummaryBody.maxLines = 2
                tvSummaryBody.ellipsize = null
                btnArrow.animate().rotation(0f).setDuration(300).start()
            }
        }
    }

    private fun processMixedSummary(fullText: String, targetLang: String, callback: (String) -> Unit) {
        val lines = fullText.split("\n")
        val processedLines = arrayOfNulls<String>(lines.size)
        var pendingTranslations = 0

        for ((index, line) in lines.withIndex()) {
            if (line.isBlank()) {
                processedLines[index] = line
                continue
            }

            if (containsEnglish(line)) {
                pendingTranslations++
                val trimmedLine = line.trim()
                val hasBullet = trimmedLine.startsWith("•")
                val textToTranslate = if (hasBullet) trimmedLine.substringAfter("•").trim() else trimmedLine

                TranslationHelper.smartTranslate(textToTranslate, targetLang) { translatedText ->
                    processedLines[index] = if (hasBullet) "• $translatedText" else translatedText

                    pendingTranslations--
                    if (pendingTranslations == 0) {
                        callback(processedLines.filterNotNull().joinToString("\n"))
                    }
                }
            } else {
                processedLines[index] = line
            }
        }

        if (pendingTranslations == 0) {
            callback(fullText)
        }
    }

    private fun hourlyForecastUI(hourly: HourlyUnits) {
        val hourlyModels = ArrayList<HourlyModel>()

        if (hourly.time.isNullOrEmpty() || hourly.temperature_2m.isNullOrEmpty()) return

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        fun t(text: String): String {
            if (langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode)
        }

        val sdfApi = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.getDefault())
        val sdfDigitsOnly = java.text.SimpleDateFormat("h:mm", java.util.Locale.ENGLISH)
        val sdfEnglishFull = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)

        val currentMillis = System.currentTimeMillis()

        for (i in hourly.time.indices) {
            if (i >= hourly.temperature_2m.size || i >= hourly.weathercode.size) break

            try {
                val timeStr = hourly.time[i]
                val timeObj = sdfApi.parse(timeStr)

                if (timeObj != null && timeObj.time >= currentMillis - 300000) {
                    var finalTimeString = ""

                    if (langCode == TranslateLanguage.ENGLISH) {
                        finalTimeString = sdfEnglishFull.format(timeObj).replace(" ", "\n")
                    } else {
                        val hourOfDay = timeObj.hours
                        val periodKey = when (hourOfDay) {
                            in 5..11 -> "Morning"
                            in 12..16 -> "Afternoon"
                            in 17..19 -> "Evening"
                            else -> "Night"
                        }
                        val rawDigits = sdfDigitsOnly.format(timeObj)
                        finalTimeString = "${d(rawDigits)}\n${t(periodKey)}"
                    }

                    val tempVal = hourly.temperature_2m.getOrNull(i)
                    val code = hourly.weathercode.getOrNull(i) ?: 0

                    if (tempVal != null) {
                        val temp = "${d(tempVal.toInt())}°"
                        val rawCond = getConditionText(code)
                        val displayCond = t(rawCond)

                        val isDay = if (timeObj.hours in 6..18) 1 else 0
                        hourlyModels.add(HourlyModel(finalTimeString, temp, getIconForCondition(displayCond, isDay)))
                    }

                    if (hourlyModels.size >= 24) break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val recyclerHourly = findViewById<RecyclerView>(R.id.recyclerHourly)

        if (recyclerHourly.itemDecorationCount == 0) {
            val spacingInPixels = (0 * resources.displayMetrics.density).toInt()
            recyclerHourly.addItemDecoration(HorizontalSpacingItemDecoration(spacingInPixels))
        }

        recyclerHourly.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerHourly.adapter = HourlyAdapter(hourlyModels)
    }

    private fun weeklyForecastUI(daily: DailyUnits) {
        val list = ArrayList<ForecastModel>()

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        fun t(text: String): String {
            if (langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode)
        }

        val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
        val dayFmt = java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH)
        val dateFmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale.ENGLISH)

        if (daily.time.isNullOrEmpty()) return

        for (i in daily.time.indices) {
            if (i >= daily.temperature_2m_max.size || i >= daily.temperature_2m_min.size) break

            val rawDate = daily.time[i]
            val dateObj = try { inFmt.parse(rawDate) } catch (e: Exception) { null }

            val dayNameEng = if (dateObj != null) dayFmt.format(dateObj) else rawDate
            val dateDisplayEng = if (dateObj != null) dateFmt.format(dateObj) else rawDate

            val maxTemp = daily.temperature_2m_max.getOrNull(i)
            val minTemp = daily.temperature_2m_min.getOrNull(i)
            val weatherCode = daily.weathercode.getOrNull(i) ?: 0

            val high = if (maxTemp != null) "${d(maxTemp.toInt())}°" else "--"
            val low = if (minTemp != null) "${d(minTemp.toInt())}°" else "--"

            val dayNameFinal = t(dayNameEng)
            val dateFinal = d(dateDisplayEng)
            val condText = getConditionText(weatherCode)

            list.add(ForecastModel(dayNameFinal, dateFinal, getIconForCondition(condText), high, low))
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerForecast)
        recycler.adapter = ForecastAdapter(list)
    }

    private fun agriculturalInsightsUI(data: OpenMeteoResponse) {
        val insightList = mutableListOf<InsightModel>()

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        fun t(text: String): String {
            if (langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode)
        }

        val todayRain = data.daily.precipitation_probability_max.firstOrNull() ?: 0
        val windSpeed = data.current.wind_speed_10m
        val currentSoilMoisture = data.hourly.soil_moisture_3_9cm.firstOrNull() ?: 0.0

        var hasAlert = false

        // 1. Rain Alert
        if (todayRain > 50) {
            insightList.add(InsightModel(
                t("Rainfall Alert"),
                "${t("High chance of rain")} (${d(todayRain)}%). ${t("Delay spraying pesticides")}.",
                R.drawable.rain_image
            ))
            hasAlert = true
        }

        // 2. Wind Alert
        if (windSpeed > 15) {
            insightList.add(InsightModel(
                t("Spraying Alert"),
                "${t("Wind is too strong")} (${d(windSpeed)} ${t("km/h")}). ${t("Avoid spraying pesticides")}.",
                R.drawable.wind_warning_image
            ))
            hasAlert = true
        }

        // 3. Wet Soil Alert
        if (currentSoilMoisture > 0.35) {
            insightList.add(InsightModel(
                t("Soil Status"),
                "${t("Soil is likely wet")}. ${t("Avoid heavy machinery")}.",
                R.drawable.wetsoil_image
            ))
            hasAlert = true
        }

        // 4. All Clear
        if (!hasAlert) {
            insightList.add(InsightModel(
                t("Today's Activity"),
                "${t("Conditions are clear")}. ${t("Good time for irrigation")}.",
                R.drawable.spraying_image
            ))
        }

        // FUTURE FORECAST
        var heavyRainDay: String? = null
        val lookaheadDays = 14

        for (i in 1 until minOf(data.daily.precipitation_probability_max.size, lookaheadDays + 1)) {
            if (data.daily.precipitation_probability_max[i] > 60) {
                if (heavyRainDay == null) heavyRainDay = getDayName(data.daily.time[i])
            }
        }

        if (heavyRainDay != null) {
            val translatedDay = t(heavyRainDay)

            insightList.add(InsightModel(
                t("Upcoming Weather"),
                "${t("Heavy rain expected on")} $translatedDay. ${t("Plan drainage")}.",
                R.drawable.rain_image
            ))
        } else {
            insightList.add(InsightModel(
                t("Upcoming Weather"),
                "${t("No rain in the next")} ${d(lookaheadDays)} ${t("days")}. ${t("Perfect time to irrigate")}.",
                R.drawable.irrigation_image
            ))
        }

        // MONTHLY ADVICE
        val calendar = java.util.Calendar.getInstance()
        val currentMonthIndex = calendar.get(java.util.Calendar.MONTH)

        fun getSeasonalTip(monthIndex: Int): String {
            return when (monthIndex % 12) {
                0 -> "Monitor wheat for frost. Apply irrigation if needed."
                1 -> "Temperature rising. Watch for aphids on mustard crops."
                2 -> "Harvest rabi crops. Prepare land for summer vegetables."
                3 -> "Sowing of summer crops (Zaid). Maintain soil moisture."
                4 -> "Deep ploughing to kill pests. Prepare for Kharif season."
                5 -> "Monsoon arrival. Start sowing paddy and cotton."
                6 -> "Active monsoon. Ensure drainage in waterlogged fields."
                7 -> "Weeding is crucial now. Monitor for pest attacks."
                8 -> "Late monsoon rains. Plan harvesting of early varieties."
                9 -> "Post-harvest soil prep. Sowing of early rabi crops."
                10 -> "Main sowing month for Wheat and Gram. Irrigate pre-sowing."
                11 -> "Protect crops from cold waves. Mulching recommended."
                else -> "Maintain general field hygiene."
            }
        }

        insightList.add(InsightModel(
            t("This Month's Advice"),
            t(getSeasonalTip(currentMonthIndex)),
            R.drawable.soilirrigation_image
        ))

        insightList.add(InsightModel(
            t("Next Month's Plan"),
            t(getSeasonalTip(currentMonthIndex + 1)),
            R.drawable.soilmoisture_image
        ))

        val recyclerInsights = findViewById<RecyclerView>(R.id.recyclerInsights)
        if (recyclerInsights.itemDecorationCount == 0) {
            val spacingInPixels = (0 * resources.displayMetrics.density).toInt()
            recyclerInsights.addItemDecoration(HorizontalSpacingItemDecoration(spacingInPixels))
        }
        recyclerInsights.layoutManager = LinearLayoutManager(this)
        recyclerInsights.adapter = InsightAdapter(insightList)
    }

    private fun getDayName(dateString: String): String {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val outFmt = SimpleDateFormat("EEE", Locale.ENGLISH)
        return try { outFmt.format(inFmt.parse(dateString)!!) } catch (e: Exception) { dateString }
    }

    private fun containsEnglish(text: String): Boolean {
        return Regex("[a-zA-Z]{3,}").containsMatchIn(text)
    }

}