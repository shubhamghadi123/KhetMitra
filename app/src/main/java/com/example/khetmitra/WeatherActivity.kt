package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
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

    private var currentLocationQuery = "19.0760,72.8777" // Default city if GPS fails

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val recyclerHourly = findViewById<RecyclerView>(R.id.recyclerHourly)
        recyclerHourly.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerHourly.adapter = HourlyAdapter(emptyList())

        val recyclerForecast = findViewById<RecyclerView>(R.id.recyclerForecast)
        recyclerForecast.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerForecast.adapter = ForecastAdapter(emptyList())

        checkLocationPermissionAndFetch()

        setupSummaryExpandLogic()
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

    private fun getConditionText(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1, 2, 3 -> "Partly cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Light drizzle"
            61, 63, 65 -> "Rain"
            80, 81, 82 -> "Heavy rain"
            95, 96, 99 -> "Thunderstorm"
            71, 73, 75, 77, 85, 86 -> "Snow"
            else -> "Overcast"
        }
    }

    private fun getIconForCondition(conditionRaw: String, isDay: Int = 1): Int {
        val text = conditionRaw.lowercase()
        return when {
            text.contains("clear") || text.contains("sunny") -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
            text.contains("partly") -> if (isDay == 1) R.raw.partly_cloudy_day else R.raw.partly_cloudy_night
            text.contains("cloudy") || text.contains("overcast") -> R.raw.overcast
            text.contains("drizzle") -> R.raw.drizzle
            text.contains("heavy rain") -> R.raw.rain
            text.contains("rain") -> R.raw.rain
            text.contains("thunder") -> R.raw.thunderstorms
            text.contains("fog") -> R.raw.fog
            else -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
        }
    }

    private fun fetchWeatherData(query: String) {
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
                            currentWeatherUI(weatherData, epaIndex)
                        }

                        override fun onFailure(call2: Call<AirQualityResponse>, t: Throwable) {
                            currentWeatherUI(weatherData, 2)
                        }
                    })
                }
            }
            override fun onFailure(call: Call<OpenMeteoResponse>, t: Throwable) {
                Toast.makeText(this@WeatherActivity, "Failed to load weather", Toast.LENGTH_SHORT).show()
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

    private fun currentWeatherUI(data: OpenMeteoResponse, aqiIndex: Int) {

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

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        fun t(text: String): String {
            if (langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode)
        }

        lottieIcon.setAnimation(getIconForCondition(conditionText, current.is_day))
        lottieIcon.playAnimation()

        tvCondition.text = t(conditionText)

        val tempNum = d(current.temperature_2m.toInt())
        val unit = t("°C")
        tvTemp.text = "${d(tempNum)}$unit"

        val feelsPrefix = t("Feels like")
        val feelsNum = d(current.apparent_temperature.toInt())
        tvFeelsLike.text = "$feelsPrefix $feelsNum$unit"

        val rawHigh = todayHigh.toInt()
        val rawLow = todayLow.toInt()
        tvHighLow.text = "↑${d(rawHigh)}° ↓${d(rawLow)}°"

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

            // Today's High Temp
            val todayHigh = data.daily.temperature_2m_max.firstOrNull()?.toInt() ?: 0
            sb.append("• ${t("Today's high will reach around")} ${d(todayHigh)}${t("°C")}\n")

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
                sb.append("• ${t("Soil is wet")} — ${t("Avoid heavy machinery")}")
            } else if (soilMoisture < 0.15) {
                sb.append("• ${t("Soil is dry")} — ${t("Consider irrigation")}")
            }

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
                val textToTranslate = if (hasBullet) {
                    trimmedLine.substringAfter("•").trim()
                } else {
                    trimmedLine
                }

                translateTextWithMLKit(textToTranslate, targetLang) { translatedText ->
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

        val sdfApi = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.getDefault())
        val sdfDigitsOnly = java.text.SimpleDateFormat("h:mm", java.util.Locale.ENGLISH)
        val sdfEnglishFull = java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH)

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

        fun num(n: Any): String = TranslationHelper.convertDigits(n.toString(), langCode ?: TranslateLanguage.ENGLISH)
        fun txt(s: String): String = if (langCode != TranslateLanguage.ENGLISH) TranslationHelper.getManualTranslation(s, langCode!!) ?: s else s

        val currentMillis = System.currentTimeMillis()

        for (i in hourly.time.indices) {
            try {
                val timeStr = hourly.time[i]
                val timeObj = sdfApi.parse(timeStr)

                if (timeObj != null && timeObj.time >= currentMillis - 3000000) {

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
                        val numberRegex = Regex("\\d+")
                        val translatedDigits = numberRegex.replace(rawDigits) { matchResult ->
                            num(matchResult.value)
                        }
                        finalTimeString = "$translatedDigits\n${txt(periodKey)}"
                    }

                    val tempVal = hourly.temperature_2m[i].toInt()
                    val temp = "${num(tempVal)}°"
                    val code = hourly.weathercode[i]
                    val condText = getConditionText(code)
                    val isDay = if (timeObj.hours in 6..18) 1 else 0

                    hourlyModels.add(HourlyModel(
                        finalTimeString,
                        temp,
                        getIconForCondition(condText, isDay)
                    ))
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
        val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
        val dayFmt = java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH)
        val dateFmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale.ENGLISH)

        for (i in daily.time.indices) {
            val rawDate = daily.time[i]
            val dateObj = try { inFmt.parse(rawDate) } catch (e: Exception) { null }

            val dayName = if (dateObj != null) dayFmt.format(dateObj) else rawDate
            val dateDisplay = if (dateObj != null) dateFmt.format(dateObj) else rawDate

            val high = "${daily.temperature_2m_max[i].toInt()}°"
            val low = "${daily.temperature_2m_min[i].toInt()}°"
            val condText = getConditionText(daily.weathercode[i])

            list.add(ForecastModel(dayName, dateDisplay, getIconForCondition(condText), high, low))
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerForecast)
        recycler.adapter = ForecastAdapter(list)
    }

    private fun agriculturalInsightsUI(data: OpenMeteoResponse) {
        val insightList = mutableListOf<InsightModel>()

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
        val todayRain = data.daily.precipitation_probability_max.firstOrNull() ?: 0

        if (todayRain > 50) {
            val part1 = getText("High chance of rain")
            val part2 = getText("Delay spraying pesticides")
            val finalMsg = "$part1 (${num(todayRain)}%). $part2"

            insightList.add(InsightModel(getText("Rainfall Alert"), finalMsg, R.drawable.rain_image))
        } else {
            val part1 = getText("Low chance of rain")
            val part2 = getText("Good time for irrigation")
            val finalMsg = "$part1. $part2"

            insightList.add(InsightModel(getText("Today's Activity"), finalMsg, R.drawable.irrigation_image))
        }

        // --- INSIGHT 2: FUTURE LOOKAHEAD (Next 3 Days) ---
        val daysLookahead = 3
        var upcomingRainDay: String? = null
        for (i in 1..daysLookahead) {
            if (i < data.daily.precipitation_probability_max.size &&
                data.daily.precipitation_probability_max[i] > 60) {

                val dateStr = data.daily.time[i]
                upcomingRainDay = getDayName(dateStr)
                break
            }
        }

        if (upcomingRainDay != null) {
            val translatedDay = if (langCode != TranslateLanguage.ENGLISH) {
                TranslationHelper.getManualTranslation(upcomingRainDay, langCode) ?: upcomingRainDay
            } else upcomingRainDay

            val part1 = getText("Heavy rain expected on")
            val part2 = getText("Plan drainage")

            insightList.add(InsightModel(getText("Upcoming Weather"), "$part1 $translatedDay. $part2", R.drawable.rain_image))
        } else {
            if (todayRain < 30) {
                val templateMsg = getText("No rain in the next {n} days")
                val part1 = templateMsg.replace("{n}", num(daysLookahead))
                val part2 = getText("Perfect time to irrigate")

                insightList.add(InsightModel(getText("Irrigation Advice"), "$part1. $part2", R.drawable.soilirrigation_image))
            }
        }

        // --- INSIGHT 3: SOIL MOISTURE ---
        val currentSoilMoisture = data.hourly.soil_moisture_3_9cm.firstOrNull() ?: 0.0

        if (currentSoilMoisture > 0.35 || todayRain > 70) {
            val part1 = getText("Soil is likely wet")
            val part2 = getText("Avoid heavy machinery")

            insightList.add(InsightModel(getText("Soil Status"), "$part1. $part2", R.drawable.wetsoil_image))
        } else {
            insightList.add(InsightModel(getText("Soil Status"), getText("Soil moisture is likely stable"), R.drawable.soilmoisture_image))
        }

        // --- INSIGHT 4: SPRAYING ADVICE (Wind) ---
        val windSpeed = data.current.wind_speed_10m

        if (windSpeed > 15) {
            val part1 = getText("Wind is too strong")
            val part2 = getText("Avoid spraying pesticides")
            val msg = "$part1 (${num(windSpeed)} km/h). $part2"

            insightList.add(InsightModel(getText("Spraying Alert"), msg, R.drawable.wind_warning_image))
        } else if (windSpeed < 3) {
            insightList.add(InsightModel(getText("Spraying Advice"), getText("Wind is very calm. Ensure good coverage."), R.drawable.spraying_image))
        } else {
            insightList.add(InsightModel(getText("Spraying Advice"), getText("Wind conditions are ideal for spraying."), R.drawable.spraying_image))
        }

        // --- INSIGHT 5: IRRIGATION (Evapotranspiration) ---
        val waterLoss = data.daily.et0_fao_evapotranspiration.firstOrNull() ?: 0.0

        if (waterLoss > 6.0) {
            val part1 = getText("High water loss today")
            val part2 = getText("Crops need extra water")
            val msg = "$part1 (${num(waterLoss)}mm). $part2"

            insightList.add(InsightModel(getText("Irrigation Alert"), msg, R.drawable.irrigation_image))
        }

        // --- INSIGHT 6: SOWING (Soil Temp) ---
        val soilTemp = data.hourly.soil_temperature_6cm.firstOrNull() ?: 0.0
        val part1 = getText("Soil Temperature")
        val msg = "$part1: ${num(soilTemp.toInt())}°C"
        insightList.add(InsightModel(getText("Soil Health"), msg, R.drawable.soil_temp_image))

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

    private fun translateTextWithMLKit(text: String, targetLang: String, callback: (String) -> Unit) {
        val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()
        val client = com.google.mlkit.nl.translate.Translation.getClient(options)

        client.downloadModelIfNeeded().addOnSuccessListener {
            client.translate(text).addOnSuccessListener { result ->
                callback(result)
            }.addOnFailureListener { callback(text) }
        }.addOnFailureListener { callback(text) }
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