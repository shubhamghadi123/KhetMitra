package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WeatherActivity : BaseActivity() {
    private var currentLocationQuery = "19.0760,72.8777" // Default city if GPS fails
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather)

        updateLangCode()
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
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
        updateLangCode()
        checkLocationPermissionAndFetch()
    }

    private fun updateLangCode() {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
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
        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
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
            val geocoder = Geocoder(this, Locale.getDefault())
            @Suppress("DEPRECATION") val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                cityName = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Unknown"
            }
        } catch (e: Exception) { e.printStackTrace() }
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
            text.contains("clear") || text.contains("sunny") -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
            text.contains("partly") -> if (isDay == 1) R.raw.partly_cloudy_day else R.raw.partly_cloudy_night
            text.contains("cloudy") -> R.raw.cloudy
            text.contains("overcast") -> R.raw.overcast
            text.contains("mist") -> R.raw.mist
            text.contains("fog") -> R.raw.fog
            text.contains("haze") -> R.raw.haze
            text.contains("dust") -> R.raw.dust
            text.contains("drizzle") -> R.raw.drizzle
            text.contains("sleet") -> R.raw.sleet
            text.contains("thunder") && text.contains("rain") -> R.raw.thunderstorms_rain
            text.contains("hail") -> R.raw.hail
            text.contains("thunder") -> R.raw.thunderstorms
            text.contains("rain") -> R.raw.rain
            text.contains("snow") -> R.raw.snow
            text.contains("tornado") -> R.raw.tornado
            text.contains("hurricane") -> R.raw.hurricane
            text.contains("wind") -> R.raw.wind
            else -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
        }
    }

    private fun fetchWeatherData(query: String) {
        if (!swipeRefreshLayout.isRefreshing) {
            loadingOverlay.visibility = View.VISIBLE
        }

        var lat = 19.07
        var lon = 72.87
        try {
            val parts = query.split(",")
            lat = parts[0].trim().toDouble()
            lon = parts[1].trim().toDouble()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val weatherDeferred = async { RetrofitClient.weatherService.getForecast(lat, lon) }
                val aqiDeferred = async { RetrofitClient.aqiService.getAirQuality(lat, lon) }
                val weatherResponse = weatherDeferred.await()
                val aqiResponse = aqiDeferred.await()
                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weatherData = weatherResponse.body()!!
                    val rawAqi = if (aqiResponse.isSuccessful) {
                        aqiResponse.body()?.current?.us_aqi ?: 50
                    } else {
                        50
                    }
                    val epaIndex = convertAqiToEpa(rawAqi)
                    withContext(Dispatchers.Main) {
                        currentWeatherUI(weatherData, epaIndex, lat, lon)
                        swipeRefreshLayout.isRefreshing = false
                        loadingOverlay.visibility = View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loadingOverlay.visibility = View.GONE
                        swipeRefreshLayout.isRefreshing = false
                        Toast.makeText(this@WeatherActivity, "Failed to load weather", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this@WeatherActivity, "Network Error", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    @SuppressLint("SetTextI18n")
    private fun updateAqiPill(tvAqi: TextView, aqiIndex: Int) {
        val (status, colorHex) = when (aqiIndex) {
            1    -> Pair("Good",      "#4CAF50")
            2    -> Pair("Moderate",  "#FFC107")
            3    -> Pair("Sensitive", "#FF9800")
            4    -> Pair("Unhealthy", "#FF5252")
            5    -> Pair("Very Bad",  "#9C27B0")
            else -> Pair("Hazardous", "#B71C1C")
        }
        tvAqi.text = "${t("AQI")}: ${t(status)}"
        val cardAqi = tvAqi.parent as? MaterialCardView
        if (cardAqi != null) {
            cardAqi.setCardBackgroundColor(colorHex.toColorInt())
        } else {
            try { tvAqi.background?.setTint(colorHex.toColorInt()) } catch (_: Exception) {}
        }
    }

    private fun isFahrenheit(prefs: SharedPreferences): Boolean {
        val tempUnitPref = prefs.getString("TempUnit", "Celsius (°C)") ?: "Celsius (°C)"
        return tempUnitPref.contains("Fahrenheit")
    }

    private fun isMph(prefs: SharedPreferences): Boolean {
        val windUnitPref = prefs.getString("WindUnit", "km/h") ?: "km/h"
        return windUnitPref == "m/h" || windUnitPref == "mph"
    }

    private fun convertTemp(celsius: Double, isFahrenheit: Boolean): Int {
        return if (isFahrenheit) {
            ((celsius * 9 / 5) + 32).toInt()
        } else {
            celsius.toInt()
        }
    }

    private fun convertWind(kmh: Double, isMph: Boolean): Int {
        return if (isMph) {
            (kmh * 0.621371).toInt()
        } else {
            kmh.toInt()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun currentWeatherUI(data: OpenMeteoResponse, aqiIndex: Int, lat: Double, lon: Double) {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val useFahrenheit = isFahrenheit(prefs)
        val useMph = isMph(prefs)
        val tempSymbol = if (useFahrenheit) t("°F") else t("°C")
        val windSymbol = if (useMph) t("m/h") else t("km/h")

        weeklyForecastUI(data.daily, useFahrenheit)
        hourlyForecastUI(data.hourly, useFahrenheit)
        agriculturalInsightsUI(data, useMph)

        val current = data.current
        val todayHigh = data.daily.temperature_2m_max.firstOrNull() ?: 0.0
        val todayLow = data.daily.temperature_2m_min.firstOrNull() ?: 0.0
        val conditionText = getConditionText(current.weathercode)
        val tvCondition = findViewById<TextView>(R.id.tvWeatherCondition)
        val tvTemp = findViewById<TextView>(R.id.tvTempBig)
        val tvFeelsLike = findViewById<TextView>(R.id.tvFeelsLike)
        val lottieIcon = findViewById<LottieAnimationView>(R.id.iconCurrentWeather)
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

        val tempNum = d(convertTemp(current.temperature_2m, useFahrenheit))
        tvTemp.text = "$tempNum$tempSymbol"
        val feelsPrefix = t("Feels Like")
        val feelsNum = d(convertTemp(current.apparent_temperature, useFahrenheit))
        tvFeelsLike.text = "$feelsPrefix $feelsNum$tempSymbol"
        val convertedHigh = convertTemp(todayHigh, useFahrenheit)
        val convertedLow = convertTemp(todayLow, useFahrenheit)
        tvHighLow.text = "↑${d(convertedHigh)}° ↓${d(convertedLow)}°"
        val cityName = getAddressName(lat, lon)
        TranslationHelper.smartTranslate(cityName, langCode) { translatedCity ->
            tvLocation.text = translatedCity
            loadingOverlay.visibility = View.GONE
        }

        tvHumidity.text = "${d(current.relative_humidity_2m)}%"
        val windSpeed = d(convertWind(current.wind_speed_10m, useMph))
        tvWind.text = "$windSpeed $windSymbol"
        val dewPoint = d(convertTemp(current.dew_point_2m, useFahrenheit))
        tvDewPoint.text = "$dewPoint°"
        updateAqiPill(tvAqi, aqiIndex)
        val initialSummary = generateQuickSummary(data, aqiIndex, useFahrenheit)
        tvSummaryBody.text = initialSummary

        if (langCode != TranslateLanguage.ENGLISH) {
            if (containsEnglish(initialSummary)) {
                processMixedSummary(initialSummary, langCode) { finalMixedText ->
                    tvSummaryBody.text = finalMixedText
                }
            }
        }
    }

    private fun generateQuickSummary(data: OpenMeteoResponse, aqiIndex: Int, isFahrenheit: Boolean): String {
        val tempSymbol = if (isFahrenheit) t("°F") else t("°C")

        try {
            val sb = StringBuilder()
            val rainChance = data.daily.precipitation_probability_max.firstOrNull() ?: 0
            val uvMax = data.daily.uv_index_max.firstOrNull() ?: 0.0
            val humidity = data.current.relative_humidity_2m
            val temp = data.current.temperature_2m
            val dewPoint = data.current.dew_point_2m
            val soilMoisture = data.hourly.soil_moisture_3_9cm.firstOrNull() ?: 0.0
            val soilTemp = data.hourly.soil_temperature_6cm.firstOrNull() ?: 0.0

            val weatherIntro = if (humidity > 80 && temp > 24) {
                t("Expect a humid, clingy morning — sweaty conditions expected")
            } else if (rainChance > 50) {
                t("Expect a rainy, wet start — Carrying an umbrella is advised")
            } else {
                t("Expect a clear, bright start — Perfect for outdoor tasks")
            }

            val aqiStatus = if (aqiIndex > 3) t("Air quality may be unhealthy") else t("Air quality is acceptable")
            sb.append("$weatherIntro.\n$aqiStatus.\n")

            if (aqiIndex > 3) {
                sb.append("• ${t("Air quality is poor — Consider limiting time outside")}\n")
            }

            if (uvMax > 5) {
                sb.append("• ${t("High UV levels could pose a risk outdoors")}\n")
            }

            if (humidity > 70) {
                val displayDewPoint = convertTemp(dewPoint, isFahrenheit)
                val tempString = "${d(displayDewPoint)}$tempSymbol"
                val humidText = t("Feels humid — Dew point near [TEMP]")
                    .replace("[TEMP]", tempString)
                sb.append("• $humidText\n")
            }

            val sunriseRaw = data.daily.sunrise.firstOrNull()
            val sunsetRaw = data.daily.sunset.firstOrNull()
            if (sunriseRaw != null && sunsetRaw != null) {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                val dateRise = isoFormat.parse(sunriseRaw)
                val dateSet = isoFormat.parse(sunsetRaw)
                fun getSmartTime(date: Date): String {
                    if (langCode == TranslateLanguage.ENGLISH) {
                        return SimpleDateFormat("h:mm a", Locale.ENGLISH).format(date)
                    }
                    val cal = Calendar.getInstance()
                    cal.time = date
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val periodKey = when (hour) {
                        in 5..11 -> "Morning"
                        in 12..16 -> "Afternoon"
                        in 17..19 -> "Evening"
                        else -> "Night"
                    }
                    val rawTime = SimpleDateFormat("h:mm", Locale.ENGLISH).format(date)
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

            if (soilMoisture > 0.35) {
                sb.append("• ${t("Soil is wet")} — ${t("Avoid heavy machinery")}\n")
            } else if (soilMoisture < 0.15) {
                sb.append("• ${t("Soil is dry")} — ${t("Consider irrigation")}\n")
            }

            val displaySoilTemp = convertTemp(soilTemp, isFahrenheit)
            sb.append("• ${t("Soil Temperature")}: ${d(displaySoilTemp)}$tempSymbol")

            return sb.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            return t("Weather data is currently unavailable.")
        }
    }

    private fun setupSummaryExpandLogic() {
        val headerSummary = findViewById<LinearLayout>(R.id.headerSummary)
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

    @Suppress("DEPRECATION")
    private fun hourlyForecastUI(hourly: HourlyUnits, isFahrenheit: Boolean) {
        val hourlyModels = ArrayList<HourlyModel>()
        if (hourly.time.isEmpty() || hourly.temperature_2m.isEmpty()) return
        val sdfApi = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val sdfDigitsOnly = SimpleDateFormat("h:mm", Locale.ENGLISH)
        val sdfEnglishFull = SimpleDateFormat("h:mm a", Locale.ENGLISH)
        val currentMillis = System.currentTimeMillis()
        for (i in hourly.time.indices) {
            if (i >= hourly.temperature_2m.size || i >= hourly.weathercode.size) break
            try {
                val timeStr = hourly.time[i]
                val timeObj = sdfApi.parse(timeStr)
                if (timeObj != null && timeObj.time >= currentMillis - 300000) {
                    var finalTimeString: String
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
                    val tempValRaw = hourly.temperature_2m.getOrNull(i)
                    val code = hourly.weathercode.getOrNull(i) ?: 0
                    if (tempValRaw != null) {
                        val tempVal = convertTemp(tempValRaw, isFahrenheit)
                        val temp = "${d(tempVal)}°"
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

    private fun weeklyForecastUI(daily: DailyUnits, isFahrenheit: Boolean) {
        val list = ArrayList<ForecastModel>()
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val dayFmt = SimpleDateFormat("EEE", Locale.ENGLISH)
        val dateFmt = SimpleDateFormat("dd/MM", Locale.ENGLISH)
        if (daily.time.isEmpty()) return
        val daysToShow = minOf(daily.time.size, 7)
        for (i in 0 until daysToShow) {
            if (i >= daily.temperature_2m_max.size || i >= daily.temperature_2m_min.size) break

            val rawDate = daily.time[i]
            val dateObj = try { inFmt.parse(rawDate) } catch (_: Exception) { null }

            val dayNameEng = if (dateObj != null) dayFmt.format(dateObj) else rawDate
            val dateDisplayEng = if (dateObj != null) dateFmt.format(dateObj) else rawDate

            val maxTempRaw = daily.temperature_2m_max.getOrNull(i)
            val minTempRaw = daily.temperature_2m_min.getOrNull(i)
            val weatherCode = daily.weathercode.getOrNull(i) ?: 0

            val high = if (maxTempRaw != null) "${d(convertTemp(maxTempRaw, isFahrenheit))}°" else "--"
            val low = if (minTempRaw != null) "${d(convertTemp(minTempRaw, isFahrenheit))}°" else "--"

            val dayNameFinal = t(dayNameEng)
            val dateFinal = d(dateDisplayEng)
            val condText = getConditionText(weatherCode)
            list.add(ForecastModel(dayNameFinal, dateFinal, getIconForCondition(condText), high, low))
        }
        val recycler = findViewById<RecyclerView>(R.id.recyclerForecast)
        recycler.adapter = ForecastAdapter(list)
    }

    private fun agriculturalInsightsUI(data: OpenMeteoResponse, isMph: Boolean) {
        val insightList = mutableListOf<InsightModel>()
        val windSymbol = if (isMph) t("m/h") else t("km/h")

        // Data Extraction
        val todayRain           = data.daily.precipitation_probability_max.firstOrNull() ?: 0
        val windSpeedMetric     = data.current.wind_speed_10m
        val windSpeedDisplay    = convertWind(windSpeedMetric, isMph)
        val currentSoilMoisture = data.hourly.soil_moisture_3_9cm.firstOrNull() ?: 0.0
        val todayMaxTemp        = data.daily.temperature_2m_max.firstOrNull() ?: 0.0
        val todayMinTemp        = data.daily.temperature_2m_min.firstOrNull() ?: 0.0

        var hasAlert = false

        // 1. Extreme Temperature Alerts (Heatwave / Frost)
        if (todayMaxTemp > 38.0) {
            insightList.add(InsightModel(
                title       = t("Heat Stress Alert"),
                description = "${t("Extreme heat")} (${d(todayMaxTemp.toInt())}°). ${t("Ensure adequate soil moisture and avoid afternoon spraying")}.",
                imageRes    = R.drawable.soilmoisture_image,
                tag         = "advisory"
            ))
            hasAlert = true
        } else if (todayMinTemp < 5.0) {
            insightList.add(InsightModel(
                title       = t("Frost Warning"),
                description = "${t("Temperatures dropping to")} ${d(todayMinTemp.toInt())}°. ${t("Apply light irrigation to protect crops from frost")}.",
                imageRes    = R.drawable.soilirrigation_image,
                tag         = "irrigation"
            ))
            hasAlert = true
        }

        // 2. Rain Alert
        if (todayRain > 50) {
            insightList.add(InsightModel(
                title       = t("Rainfall Alert"),
                description = "${t("High chance of rain")} (${d(todayRain)}%). ${t("Delay spraying fertilizers and pesticides")}.",
                imageRes    = R.drawable.rain_image,
                tag         = "rain"
            ))
            hasAlert = true
        }

        // 3. Wind Alert
        if (windSpeedMetric > 15) {
            insightList.add(InsightModel(
                title       = t("Spraying Alert"),
                description = "${t("Wind is too strong")} (${d(windSpeedDisplay)} $windSymbol). ${t("Avoid spraying to prevent chemical drift")}.",
                imageRes    = R.drawable.wind_warning_image,
                tag         = "wind"
            ))
            hasAlert = true
        }

        // 4. Wet Soil Alert
        if (currentSoilMoisture > 0.35) {
            insightList.add(InsightModel(
                title       = t("Soil Status"),
                description = "${t("Soil is currently wet")}. ${t("Avoid heavy machinery to prevent soil compaction")}.",
                imageRes    = R.drawable.wetsoil_image,
                tag         = "soil"
            ))
            hasAlert = true
        }

        // 5. All Clear Logic
        if (!hasAlert) {
            if (currentSoilMoisture < 0.20) {
                insightList.add(InsightModel(
                    title       = t("Today's Activity"),
                    description = "${t("Conditions are clear but soil is dry")}. ${t("Perfect time to irrigate")}.",
                    imageRes    = R.drawable.irrigation_image,
                    tag         = "irrigation"
                ))
            } else {
                insightList.add(InsightModel(
                    title       = t("Today's Activity"),
                    description = "${t("Conditions are ideal")}. ${t("Good time for general field maintenance and spraying")}.",
                    imageRes    = R.drawable.spraying_image,
                    tag         = "advisory"
                ))
            }
        }

        // 6. Future Forecast
        var heavyRainDay: String? = null
        val lookaheadDays = 14
        val maxDaysToScan = minOf(data.daily.precipitation_probability_max.size, lookaheadDays + 1)

        for (i in 1 until maxDaysToScan) {
            if (data.daily.precipitation_probability_max[i] > 60) {
                heavyRainDay = getDayName(data.daily.time[i])
                break
            }
        }

        if (heavyRainDay != null) {
            insightList.add(InsightModel(
                title       = t("Upcoming Weather"),
                description = "${t("Heavy rain expected on")} ${t(heavyRainDay)}. ${t("Ensure proper field drainage")}.",
                imageRes    = R.drawable.rain_image,
                tag         = "rain"
            ))
        } else {
            insightList.add(InsightModel(
                title       = t("Upcoming Weather"),
                description = "${t("No heavy rain in the next")} ${d(lookaheadDays)} ${t("days")}. ${t("Plan irrigation accordingly")}.",
                imageRes    = R.drawable.irrigation_image,
                tag         = "irrigation"
            ))
        }

        // 7. Monthly Advice
        val calendar = Calendar.getInstance()
        val currentMonthIndex = calendar.get(Calendar.MONTH)

        fun getSeasonalTip(monthIndex: Int): String {
            return when (monthIndex % 12) {
                0  -> "Monitor wheat for frost. Apply irrigation if needed."
                1  -> "Temperature rising. Watch for aphids on mustard crops."
                2  -> "Harvest rabi crops. Prepare land for summer vegetables."
                3  -> "Sowing of summer crops (Zaid). Maintain soil moisture."
                4  -> "Deep ploughing to kill pests. Prepare for Kharif season."
                5  -> "Monsoon arrival. Start sowing paddy and cotton."
                6  -> "Active monsoon. Ensure drainage in waterlogged fields."
                7  -> "Weeding is crucial now. Monitor for pest attacks."
                8  -> "Late monsoon rains. Plan harvesting of early varieties."
                9  -> "Post-harvest soil prep. Sowing of early rabi crops."
                10 -> "Main sowing month for Wheat and Gram. Irrigate pre-sowing."
                11 -> "Protect crops from cold waves. Mulching recommended."
                else -> "Maintain general field hygiene."
            }
        }

        fun seasonalTag(monthIndex: Int): String {
            return when (monthIndex % 12) {
                0, 6, 11 -> "advisory"
                1, 7     -> "pest"
                2, 8     -> "harvest"
                3, 5, 10 -> "planting"
                4, 9     -> "soil"
                else     -> "advisory"
            }
        }

        insightList.add(InsightModel(
            title       = t("This Month's Advice"),
            description = t(getSeasonalTip(currentMonthIndex)),
            imageRes    = R.drawable.soilirrigation_image,
            tag         = seasonalTag(currentMonthIndex)
        ))

        insightList.add(InsightModel(
            title       = t("Next Month's Plan"),
            description = t(getSeasonalTip(currentMonthIndex + 1)),
            imageRes    = R.drawable.soilmoisture_image,
            tag         = seasonalTag(currentMonthIndex + 1)
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
        return try { outFmt.format(inFmt.parse(dateString)!!) } catch (_: Exception) { dateString }
    }

    private fun containsEnglish(text: String): Boolean {
        return Regex("[a-zA-Z]{3,}").containsMatchIn(text)
    }
}