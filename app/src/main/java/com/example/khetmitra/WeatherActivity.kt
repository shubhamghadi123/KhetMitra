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
                    setupHourlyRecycler(weatherData.forecast.forecastday)
                    setupInsightsRecycler(weatherData.forecast.forecastday)

                    checkAndTranslateList(findViewById(R.id.recyclerHourly))
                    checkAndTranslateList(findViewById(R.id.recyclerInsights))
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

        val rawCondition = current.condition.text
        lottieIcon.setAnimation(getIconForCondition(rawCondition, current.is_day))
        lottieIcon.playAnimation()
        tvCondition.text = t(rawCondition)

        val tempNum = d(current.temp_c.toInt())
        val unit = t("°C")
        tvTemp.text = "$tempNum$unit"

        val feelsPrefix = t("Feels like")
        val feelsNum = d(current.feelslike_c.toInt())
        tvFeelsLike.text = "$feelsPrefix $feelsNum$unit"

        val rawHigh = todayForecast.day.maxtemp_c.toInt()
        val rawLow = todayForecast.day.mintemp_c.toInt()
        tvHighLow.text = "↑${d(rawHigh)}° ↓${d(rawLow)}°"

        val initialSummary = generateQuickSummary(current, todayForecast, langCode)
        tvSummaryBody.text = initialSummary

        if (langCode != TranslateLanguage.ENGLISH) {
            if (containsEnglish(initialSummary)) {
                processMixedSummary(initialSummary, langCode) { finalMixedText ->
                    tvSummaryBody.text = finalMixedText
                }
            }
        }
    }

    private fun containsEnglish(text: String): Boolean {
        return Regex("[a-zA-Z]{3,}").containsMatchIn(text)
    }

    private fun processMixedSummary(fullText: String, targetLang: String, callback: (String) -> Unit) {
        val lines = fullText.split("\n")
        val processedLines = arrayOfNulls<String>(lines.size)
        var pendingTranslations = 0

        for ((index, line) in lines.withIndex()) {
            if (containsEnglish(line)) {
                pendingTranslations++
                translateTextWithMLKit(line, targetLang) { translatedLine ->
                    processedLines[index] = translatedLine
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

    private fun generateQuickSummary(current: Current, today: ForecastDay, langCode: String?): String {
        fun t(text: String): String {
            if (langCode == null || langCode == TranslateLanguage.ENGLISH) return text
            return TranslationHelper.getManualTranslation(text, langCode) ?: text
        }

        fun d(num: Any): String {
            return TranslationHelper.convertDigits(num.toString(), langCode ?: TranslateLanguage.ENGLISH)
        }

        try {
            val sb = StringBuilder()

            val rainChance = today.day.daily_chance_of_rain
            val humidity = current.humidity
            val temp = current.temp_c
            val aqi = current.air_quality?.us_epa_index ?: 1

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

            // Aqi Status
            val aqiStatus = if (aqi > 3) t("Air quality may be unhealthy") else t("Air quality is acceptable")
            sb.append("$headerPart1 — $aqiStatus. $headerPart2.\n")

            // AQI Warning
            if (aqi > 3) {
                sb.append("• ${t("Air quality is poor right now")} — ${t("Consider limiting time outside")}\n")
            }

            // UV Warning
            if (current.uv > 5) {
                sb.append("• ${t("High UV levels could pose a risk outdoors")}\n")
            }

            // Humidity
            if (humidity > 70) {
                val dewPoint = d(current.dewpoint_c.toInt())
                sb.append("• ${t("Feels humid later")} — ${t("Dew point near")} $dewPoint°\n")
            }

            // Today's High
            val highTemp = d(today.day.maxtemp_c.toInt())
            sb.append("• ${t("Today's high will reach around")} $highTemp${t("°C")}\n")

            // Sunrise/Sunset
            val sunrise = today.astro.sunrise
            val sunset = today.astro.sunset
            if (!sunrise.isNullOrEmpty() && !sunset.isNullOrEmpty()) {
                val am = t("AM")
                val pm = t("PM")
                val sRise = d(sunrise.replace("AM", "").trim()) + " " + am
                val sSet = d(sunset.replace("PM", "").trim()) + " " + pm
                sb.append("• ${t("Sunrise")}: $sRise, ${t("Sunset")}: $sSet\n")
            }

            // Day Length
            val duration = calculateDayLength(today.astro.sunrise, today.astro.sunset)
            if (duration != null) {
                val (hours, minutes) = duration
                val hrStr = t("hrs")
                val minStr = t("mins")
                sb.append("• ${t("Day length")}: ${d(hours)} $hrStr ${d(minutes)} $minStr")
            }

            return sb.toString().trim()

        } catch (e: Exception) {
            e.printStackTrace()
            return t("Weather data is currently unavailable.")
        }
    }

    private fun calculateDayLength(sunrise: String, sunset: String): Pair<Int, Int>? {
        return try {
            val format = SimpleDateFormat("hh:mm aa", Locale.ENGLISH)
            val dateSunrise = format.parse(sunrise)
            val dateSunset = format.parse(sunset)

            if (dateSunrise != null && dateSunset != null) {
                val diff = dateSunset.time - dateSunrise.time
                val hours = (diff / (1000 * 60 * 60)).toInt()
                val minutes = ((diff / (1000 * 60)) % 60).toInt()
                Pair(hours, minutes)
            } else null
        } catch (e: Exception) {
            null
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

    private fun setupHourlyRecycler(days: List<ForecastDay>) {
        val hourlyModels = ArrayList<HourlyModel>()

        val currentMillis = System.currentTimeMillis()
        val sdfApi = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)
        val sdfDigitsOnly = SimpleDateFormat("h:mm", Locale.ENGLISH)
        val sdfEnglishFull = SimpleDateFormat("h:mm a", Locale.ENGLISH)

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

        fun num(n: Any): String = TranslationHelper.convertDigits(n.toString(), langCode ?: TranslateLanguage.ENGLISH)
        fun txt(s: String): String = if (langCode != TranslateLanguage.ENGLISH) TranslationHelper.getManualTranslation(s, langCode!!) ?: s else s

        val allHours = ArrayList<Hour>()
        if (days.isNotEmpty()) allHours.addAll(days[0].hour)
        if (days.size > 1) allHours.addAll(days[1].hour)

        for (hourObj in allHours) {
            try {
                val timeObj = sdfApi.parse(hourObj.time)
                if (timeObj != null) {
                    if (timeObj.time >= currentMillis - 3000000) {

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

                        val temp = "${num(hourObj.temp_c.toInt())}°"
                        val iconRes = getIconForCondition(hourObj.condition.text, hourObj.is_day)
                        hourlyModels.add(HourlyModel(finalTimeString, temp, iconRes))
                        if (hourlyModels.size >= 24) break
                    }
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

    private fun setupForecastRecycler(days: List<ForecastDay>) {
        val forecastList = ArrayList<ForecastModel>()

        val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val dayFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
        val dateFormat = SimpleDateFormat("dd/MM", Locale.ENGLISH)

        for (day in days) {
            val dateObj = inFormat.parse(day.date)

            val dayName = if (dateObj != null) dayFormat.format(dateObj) else day.date
            val dateStr = if (dateObj != null) dateFormat.format(dateObj) else ""

            // Get High/Low temps
            val high = "${day.day.maxtemp_c.toInt()}°"
            val low = "${day.day.mintemp_c.toInt()}°"

            val iconRes = getIconForCondition(day.day.condition.text, 1)

            forecastList.add(ForecastModel(dayName, dateStr, iconRes, high, low))
        }

        val recyclerForecast = findViewById<RecyclerView>(R.id.recyclerForecast)

        if (recyclerForecast.itemDecorationCount == 0) {
            val spacingInPixels = (0.6 * resources.displayMetrics.density).toInt()
            recyclerForecast.addItemDecoration(HorizontalSpacingItemDecoration(spacingInPixels))
        }

        recyclerForecast.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerForecast.adapter = ForecastAdapter(forecastList)

        checkAndTranslateList(recyclerForecast)
    }

    private fun setupInsightsRecycler(days: List<ForecastDay>) {
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
                R.drawable.irrigation_image
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
                    R.drawable.soilirrigation_image
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

        val recycler = findViewById<RecyclerView>(R.id.recyclerInsights)

        if (recycler.itemDecorationCount == 0) {
            recycler.addItemDecoration(VerticalSpacingItemDecoration((12 * resources.displayMetrics.density).toInt()))
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = InsightAdapter(insightList)

    }

    private fun getDayName(dateString: String): String {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val outFmt = SimpleDateFormat("EEE", Locale.ENGLISH)
        return try { outFmt.format(inFmt.parse(dateString)!!) } catch (e: Exception) { dateString }
    }

    private fun getIconForCondition(condition: String, isDay: Int = 1): Int {
        val text = condition.lowercase().trim()

        android.util.Log.d("WeatherDebug", "API Condition: '$text'")

        return when {
            // --- SUNNY / CLEAR ---
            text == "sunny" || text == "clear" -> {
                if (isDay == 1) R.raw.clear_day else R.raw.clear_night
            }

            // --- CLOUDY ---
            text == "partly cloudy" -> {
                if (isDay == 1) R.raw.partly_cloudy_day else R.raw.partly_cloudy_night
            }
            text == "cloudy" -> R.raw.cloudy
            text == "overcast" -> R.raw.overcast

            // --- RAIN ---
            text.contains("moderate rain") || text.contains("heavy rain") || text.contains("shower") -> R.raw.rain
            text.contains("rain") || text.contains("drizzle") -> R.raw.drizzle

            // --- THUNDER ---
            text.contains("thunder") -> {
                if (text.contains("rain")) R.raw.thunderstorms_rain
                else R.raw.thunderstorms
            }

            // --- SNOW / ICE ---
            text.contains("snow") || text.contains("blizzard") -> R.raw.snow
            text.contains("sleet") || text.contains("ice") || text.contains("hail") -> R.raw.hail

            // --- ATMOSPHERE ---
            text.contains("mist") -> R.raw.mist
            text.contains("fog") || text.contains("freezing fog") -> R.raw.fog
            text.contains("haze") || text.contains("smoke") -> R.raw.haze
            text.contains("dust") || text.contains("sand") -> R.raw.dust

            // --- EXTREME ---
            text.contains("wind") -> R.raw.wind
            text.contains("tornado") -> R.raw.tornado
            text.contains("hurricane") -> R.raw.hurricane

            // --- DEFAULT ---
            else -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
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