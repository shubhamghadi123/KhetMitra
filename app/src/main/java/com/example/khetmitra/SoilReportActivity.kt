package com.example.khetmitra

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoilReportActivity : AppCompatActivity() {
    private var langCode: String = TranslateLanguage.ENGLISH
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutContent: NestedScrollView
    private lateinit var tvFarmSubtitle: TextView
    private lateinit var tvMoistureValue: TextView
    private lateinit var tvIrrigationAlert: TextView
    private lateinit var tvSurfaceTemp: TextView
    private lateinit var tvDepthTemp: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvNdviScore: TextView
    private lateinit var tvSowingAdvice: TextView
    private lateinit var toggleGroupImagery: MaterialButtonToggleGroup
    private lateinit var ivSatelliteImage: PolygonCropImageView
    private var ndviUrl: String? = null
    private var trueColorUrl: String? = null
    private var actualFarmShape: List<List<Double>> = emptyList()
    private var downloadedBoxShape: List<List<Double>> = emptyList()

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soil_report)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        layoutLoading       = findViewById(R.id.layoutLoading)
        layoutContent       = findViewById(R.id.layoutContent)
        tvFarmSubtitle      = findViewById(R.id.tvFarmSubtitle)
        tvMoistureValue     = findViewById(R.id.tvMoistureValue)
        tvIrrigationAlert   = findViewById(R.id.tvIrrigationAlert)
        tvSurfaceTemp       = findViewById(R.id.tvSurfaceTemp)
        tvDepthTemp         = findViewById(R.id.tvDepthTemp)
        tvHumidity          = findViewById(R.id.tvHumidity)
        tvNdviScore         = findViewById(R.id.tvNdviScore)
        tvSowingAdvice      = findViewById(R.id.tvSowingAdvice)
        toggleGroupImagery  = findViewById(R.id.toggleGroupImagery)
        ivSatelliteImage    = findViewById(R.id.ivSatelliteImage)

        findViewById<TextView>(R.id.tvSoilDepthLabel)?.text = t("Soil (10cm)")
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }

        toggleGroupImagery.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnNdvi      -> loadSatelliteImage(ndviUrl)
                    R.id.btnTrueColor -> loadSatelliteImage(trueColorUrl)
                }
            }
        }

        val farmName        = intent.getStringExtra("FARM_NAME") ?: "My Farm"
        val farmSize        = intent.getStringExtra("FARM_SIZE") ?: ""
        val coordinatesJson = intent.getStringExtra("FARM_COORDINATES") ?: "[]"

        val translatedFarmName = farmName.replace("Farm", t("Farm")).replace("Field", t("Field"))
        val translatedSize     = farmSize.replace("Guntas", t("Guntas")).replace("Acres", t("Acres"))
        val finalSubtitle      = "${d(translatedFarmName)} • ${d(translatedSize)}"

        tvFarmSubtitle.text = finalSubtitle

        if (langCode != TranslateLanguage.ENGLISH) {
            findViewById<View>(android.R.id.content).post {
                TranslationHelper.translateViewHierarchy(
                    findViewById(android.R.id.content), langCode
                ) { tvFarmSubtitle.text = finalSubtitle }
            }
        }

        fetchAgroData(farmName, coordinatesJson)
    }

    @SuppressLint("SetTextI18n")
    private fun fetchAgroData(farmName: String, coordinatesJson: String) {
        layoutLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE
        val existingPolyId = intent.getStringExtra("POLYGON_ID")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val listType = object : TypeToken<List<SavedCoordinate>>() {}.type
                val savedPoints: List<SavedCoordinate> = Gson().fromJson(coordinatesJson, listType)
                val polygonPoints = mutableListOf<List<Double>>()
                for (point in savedPoints) {
                    val lat = point.latitude ?: point.lat ?: 0.0
                    val lon = point.longitude ?: point.lng ?: 0.0
                    polygonPoints.add(listOf(lon, lat))
                }
                if (polygonPoints.isEmpty() || polygonPoints[0] == listOf(0.0, 0.0))
                    throw Exception("Coordinates are empty. Check database!")
                if (polygonPoints.first() != polygonPoints.last())
                    polygonPoints.add(polygonPoints.first())

                actualFarmShape = polygonPoints.toList()
                downloadedBoxShape = polygonPoints.toList()

                val apiKey = BuildConfig.AGRO_API_KEY
                var polyId: String

                if (!existingPolyId.isNullOrEmpty()) {
                    polyId = existingPolyId
                    Log.d("Khetmitra", "Using existing polygon_id: $polyId")
                } else {
                    var finalCoordinates: List<List<List<Double>>> = listOf(polygonPoints.toList())
                    var polygonRequest = PolygonRequest(
                        name    = farmName,
                        geo_json = GeoJson(geometry = Geometry(coordinates = finalCoordinates))
                    )

                    var polyResponse = AgroRetrofitClient.api.createPolygon(apiKey, polygonRequest)

                    if (polyResponse.isSuccessful && polyResponse.body() != null) {
                        polyId = polyResponse.body()!!.id
                    } else if (polyResponse.code() == 422) {
                        val errorStr = polyResponse.errorBody()?.string() ?: ""
                        if (errorStr.contains("duplicated")) {
                            polyId = "'([a-z0-9]+)'".toRegex().find(errorStr)?.groupValues?.get(1)
                                ?: throw Exception("Duplicated, but couldn't extract ID.")
                        } else if (errorStr.contains("Area of the polygon")) {
                            var sumLon = 0.0; var sumLat = 0.0
                            polygonPoints.forEach { sumLon += it[0]; sumLat += it[1] }
                            val cLon = sumLon / polygonPoints.size
                            val cLat = sumLat / polygonPoints.size
                            val off = 0.0006
                            val expandedBox = listOf(
                                listOf(cLon - off, cLat - off), listOf(cLon + off, cLat - off),
                                listOf(cLon + off, cLat + off), listOf(cLon - off, cLat + off),
                                listOf(cLon - off, cLat - off)
                            )

                            downloadedBoxShape = expandedBox
                            finalCoordinates = listOf(expandedBox)

                            polygonRequest = PolygonRequest(
                                name     = "$farmName (Expanded)",
                                geo_json = GeoJson(geometry = Geometry(coordinates = finalCoordinates))
                            )
                            polyResponse = AgroRetrofitClient.api.createPolygon(apiKey, polygonRequest)
                            polyId = if (polyResponse.isSuccessful && polyResponse.body() != null) {
                                polyResponse.body()!!.id
                            } else {
                                val secondError = polyResponse.errorBody()?.string() ?: ""
                                if (secondError.contains("duplicated"))
                                    "'([a-z0-9]+)'".toRegex().find(secondError)?.groupValues?.get(1)
                                        ?: throw Exception("Expanded box duplicated, couldn't extract ID.")
                                else throw Exception("Server Rejected Expanded Box: $secondError")
                            }
                        } else {
                            throw Exception("Server Rejected: $errorStr")
                        }
                    } else {
                        throw Exception("Failed to create boundary. Code: ${polyResponse.code()}")
                    }

                    try {
                        val user = SupabaseManager.client.auth.currentUserOrNull()
                        if (user != null) {
                            SupabaseManager.client.postgrest["farms"].update(
                                { set("polygon_id", polyId) }
                            ) {
                                filter {
                                    eq("farmer_id", user.id)
                                    eq("name", farmName)
                                }
                            }
                            Log.d("Khetmitra", "Successfully synced polygon_id $polyId to master farms table!")
                        }
                    } catch (e: Exception) {
                        Log.e("Khetmitra", "Failed to sync polygon_id to master table: ${e.message}")
                    }
                }

                val soilResponse = AgroRetrofitClient.api.getSoilData(polyId, apiKey)
                var airHumidity: Double? = null
                var airTempCelsius: Double? = null
                var weatherCondition: String? = null

                try {
                    val weatherResponse = AgroRetrofitClient.api.getCurrentWeather(polyId, apiKey)
                    if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                        val wBody = weatherResponse.body()!!
                        airHumidity = wBody.main.humidity
                        airTempCelsius = wBody.main.temp - 273.15
                        weatherCondition = wBody.weather.firstOrNull()?.main
                    }
                } catch (e: Exception) {
                    Log.e("AgroAPI", "Weather failed: ${e.message}")
                }

                val endTime   = System.currentTimeMillis() / 1000
                val startTime = endTime - (30 * 24 * 60 * 60)
                val imageResponse = AgroRetrofitClient.api.getSatelliteImages(polyId, startTime, endTime, apiKey)
                var finalNdviScore: Double? = null

                if (imageResponse.isSuccessful && !imageResponse.body().isNullOrEmpty()) {
                    val latestImage = imageResponse.body()!!.maxByOrNull { it.dt }
                    if (latestImage != null) {
                        ndviUrl      = latestImage.image.ndvi
                        trueColorUrl = latestImage.image.truecolor
                        val statUrl = latestImage.stats?.ndvi
                        if (statUrl != null) {
                            try {
                                val secureStatUrl = statUrl.replace("http://", "https://")
                                val statResponse = AgroRetrofitClient.api.getNdviStats(secureStatUrl)
                                if (statResponse.isSuccessful && statResponse.body() != null)
                                    finalNdviScore = statResponse.body()!!.mean
                            } catch (e: Exception) {
                                Log.e("AgroAPI", "NDVI Stat fetch failed: ${e.message}")
                            }
                        }
                    }
                }

                if (soilResponse.isSuccessful && soilResponse.body() != null) {
                    val soilBody = soilResponse.body()!!
                    saveAgromonitoringData(
                        fieldName  = farmName,
                        polyId     = polyId,
                        temp       = airTempCelsius,
                        hum        = airHumidity,
                        condition  = weatherCondition,
                        soilMoist  = soilBody.moisture,
                        soilTemp   = soilBody.t10 - 273.15,
                        ndviScore  = finalNdviScore
                    )
                }

                withContext(Dispatchers.Main) {
                    if (soilResponse.isSuccessful && soilResponse.body() != null) {
                        updateSoilUI(soilResponse.body()!!, airHumidity, finalNdviScore)
                    }
                    loadSatelliteImage(ndviUrl)
                    layoutLoading.visibility = View.GONE
                    layoutContent.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@SoilReportActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun updateSoilUI(soilData: SoilDataResponse, humidity: Double?, ndviScore: Double?) {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val tempUnitPref = prefs.getString("TempUnit", "Celsius (°C)")

        val moisturePercent = (soilData.moisture * 100).toInt()
        tvMoistureValue.text = "${d(moisturePercent)}%"
        findViewById<LinearProgressIndicator>(R.id.progressMoisture).progress = moisturePercent

        val surfaceCelsius = (soilData.t0  - 273.15).toInt()
        val depthCelsius   = (soilData.t10 - 273.15).toInt()

        if (tempUnitPref == "Fahrenheit (°F)") {
            val surfaceF = (surfaceCelsius * 9.0 / 5.0) + 32
            val depthF = (depthCelsius * 9.0 / 5.0) + 32
            tvSurfaceTemp.text = "${d(surfaceF.toInt())}${t("°F")}"
            tvDepthTemp.text   = "${d(depthF.toInt())}${t("°F")}"
        } else {
            tvSurfaceTemp.text = "${d(surfaceCelsius)}${t("°C")}"
            tvDepthTemp.text   = "${d(depthCelsius)}${t("°C")}"
        }

        tvHumidity.text = if (humidity != null) "${d(humidity.toInt())}%" else "--"
        tvNdviScore.text = if (ndviScore != null) d(String.format("%.2f", ndviScore)) else "--"

        // 1. WATERING (IRRIGATION) LOGIC
        val (alertText, alertTextColor, alertBg) = when {
            moisturePercent < 20   -> Triple(
                t("Alert: Moisture is critically low. Immediate irrigation is highly recommended."),
                "#D32F2F", "#FFEBEE"
            )
            moisturePercent in 20..40 -> Triple(
                t("Note: Soil is moderately dry. Plan irrigation soon."),
                "#F57C00", "#FFF3E0"
            )
            else -> Triple(
                t("Good: Soil moisture is optimal. No immediate irrigation needed."),
                "#388E3C", "#E8F5E9"
            )
        }
        tvIrrigationAlert.text = alertText
        tvIrrigationAlert.setTextColor(alertTextColor.toColorInt())
        findViewById<MaterialCardView>(R.id.cardIrrigationAlert).setCardBackgroundColor(alertBg.toColorInt())

        // 2. FERTILIZER LOGIC
        val fertilizerAdvice = when {
            moisturePercent < 20 -> t("Soil is too dry. Avoid fertilizing now to prevent root burn. Irrigate first.")
            moisturePercent > 70 -> t("Soil is very wet. Avoid fertilizing to prevent nutrient leaching and runoff.")
            else -> t("Moisture levels are optimal for applying granular fertilizers.")
        }

        // 3. PESTICIDE / FUNGICIDE LOGIC
        val pesticideAdvice = when {
            humidity != null && humidity > 75.0 && surfaceCelsius in 20..32 ->
                t("High humidity and warm temperatures increase fungal & pest risk. Consider preventive spraying.")
            ndviScore != null && ndviScore < 0.4 ->
                t("Low crop health detected. Inspect field for pest damage or diseases before spraying.")
            else ->
                t("Weather conditions are stable. Apply pesticides only if active pest damage is visible.")
        }

        // 4. SOWING LOGIC
        val sowingAdvice = when {
            depthCelsius < 20      -> t("Soil is quite cool. Sowing might have delayed germination.")
            depthCelsius in 20..30 -> d(t("Perfect temperature conditions for sowing most crops at 10cm depth."))
            else                   -> t("Soil is quite hot. Ensure adequate moisture if sowing.")
        }

        // 5. COMBINE INTO ACTION PLAN
        tvSowingAdvice.text = "🌱 ${t("Sowing")}:\n$sowingAdvice\n\n" +
                "🧪 ${t("Fertilizer")}:\n$fertilizerAdvice\n\n" +
                "🛡️ ${t("Pesticide")}:\n$pesticideAdvice"
    }

    private fun loadSatelliteImage(url: String?) {
        if (!url.isNullOrEmpty()) {
            ivSatelliteImage.setFarmMask(
                expandedBox = downloadedBoxShape,
                exactFarmPoints = actualFarmShape
            )
            Glide.with(this)
                .load(url.replace("http://", "https://"))
                .fitCenter()
                .into(ivSatelliteImage)
        }
    }
}