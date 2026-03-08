package com.example.khetmitra

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SoilReportActivity : AppCompatActivity() {

    private var langCode: String = TranslateLanguage.ENGLISH

    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutContent: NestedScrollView
    private lateinit var tvFarmSubtitle: TextView
    private lateinit var tvMoistureValue: TextView
    private lateinit var tvIrrigationAlert: TextView
    private lateinit var tvSurfaceTemp: TextView
    private lateinit var tvDepthTemp: TextView
    private lateinit var tvSowingAdvice: TextView

    private lateinit var toggleGroupImagery: MaterialButtonToggleGroup
    private lateinit var ivSatelliteImage: ImageView
    private lateinit var tvSatelliteDate: TextView

    private var ndviUrl: String? = null
    private var trueColorUrl: String? = null

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String {
        return TranslationHelper.convertDigits(num.toString(), langCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soil_report)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        layoutLoading = findViewById(R.id.layoutLoading)
        layoutContent = findViewById(R.id.layoutContent)
        tvFarmSubtitle = findViewById(R.id.tvFarmSubtitle)
        tvMoistureValue = findViewById(R.id.tvMoistureValue)
        tvIrrigationAlert = findViewById(R.id.tvIrrigationAlert)
        tvSurfaceTemp = findViewById(R.id.tvSurfaceTemp)
        tvDepthTemp = findViewById(R.id.tvDepthTemp)
        tvSowingAdvice = findViewById(R.id.tvSowingAdvice)
        toggleGroupImagery = findViewById(R.id.toggleGroupImagery)
        ivSatelliteImage = findViewById(R.id.ivSatelliteImage)
        tvSatelliteDate = findViewById(R.id.tvSatelliteDate)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val farmName = intent.getStringExtra("FARM_NAME") ?: "My Farm"
        val farmSize = intent.getStringExtra("FARM_SIZE") ?: ""
        val coordinatesJson = intent.getStringExtra("FARM_COORDINATES") ?: "[]"

        tvFarmSubtitle.text = "$farmName • $farmSize"

        toggleGroupImagery.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val urlToLoad = if (checkedId == R.id.btnNdvi) ndviUrl else trueColorUrl
                loadSatelliteImage(urlToLoad)
            }
        }
        if (langCode != TranslateLanguage.ENGLISH) {
            findViewById<View>(android.R.id.content).post {
                TranslationHelper.translateViewHierarchy(findViewById(android.R.id.content), langCode) {}
            }
        }
        fetchAgroData(farmName, coordinatesJson)
    }

    @SuppressLint("SetTextI18n")
    private fun fetchAgroData(farmName: String, coordinatesJson: String) {
        layoutLoading.visibility = View.VISIBLE
        layoutContent.visibility = View.GONE

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

                if (polygonPoints.isEmpty() || polygonPoints[0] == listOf(0.0, 0.0)) {
                    throw Exception("Coordinates are empty. Check database!")
                }

                if (polygonPoints.first() != polygonPoints.last()) {
                    polygonPoints.add(polygonPoints.first())
                }

                var finalCoordinates: List<List<List<Double>>> = listOf(polygonPoints.toList())
                var polygonRequest = PolygonRequest(
                    name = farmName,
                    geo_json = GeoJson(geometry = Geometry(coordinates = finalCoordinates))
                )

                val apiKey = BuildConfig.AGRO_API_KEY
                var polyId = ""

                // 1. FIRST ATTEMPT: Try to create the polygon
                var polyResponse = AgroRetrofitClient.api.createPolygon(apiKey, polygonRequest)

                if (polyResponse.isSuccessful && polyResponse.body() != null) {
                    polyId = polyResponse.body()!!.id
                } else if (polyResponse.code() == 422) {
                    val errorStr = polyResponse.errorBody()?.string() ?: ""

                    if (errorStr.contains("duplicated")) {
                        // TRICK 1: ALREADY EXISTS! Extract the ID from the error message using Regex
                        val match = "'([a-z0-9]+)'".toRegex().find(errorStr)
                        if (match != null) {
                            polyId = match.groupValues[1]
                        } else {
                            throw Exception("Duplicated, but couldn't extract ID.")
                        }
                    } else if (errorStr.contains("Area of the polygon")) {
                        // TRICK 2: SMART EXPANSION FOR SMALL FARMS
                        var sumLon = 0.0
                        var sumLat = 0.0
                        for (p in polygonPoints) {
                            sumLon += p[0]
                            sumLat += p[1]
                        }
                        val centerLon = sumLon / polygonPoints.size
                        val centerLat = sumLat / polygonPoints.size

                        val offset = 0.0006
                        val expandedBox = listOf(
                            listOf(centerLon - offset, centerLat - offset),
                            listOf(centerLon + offset, centerLat - offset),
                            listOf(centerLon + offset, centerLat + offset),
                            listOf(centerLon - offset, centerLat + offset),
                            listOf(centerLon - offset, centerLat - offset)
                        )

                        finalCoordinates = listOf(expandedBox)
                        polygonRequest = PolygonRequest(
                            name = "$farmName (Expanded)",
                            geo_json = GeoJson(geometry = Geometry(coordinates = finalCoordinates))
                        )

                        // Try creating the expanded box
                        polyResponse = AgroRetrofitClient.api.createPolygon(apiKey, polygonRequest)

                        if (polyResponse.isSuccessful && polyResponse.body() != null) {
                            polyId = polyResponse.body()!!.id
                        } else {
                            // Check if our expanded box was ALSO already created from a previous test!
                            val secondError = polyResponse.errorBody()?.string() ?: ""
                            if (secondError.contains("duplicated")) {
                                val match2 = "'([a-z0-9]+)'".toRegex().find(secondError)
                                if (match2 != null) polyId = match2.groupValues[1]
                                else throw Exception("Expanded box duplicated, couldn't extract ID.")
                            } else {
                                throw Exception("Server Rejected Expanded Box: $secondError")
                            }
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SoilReportActivity, t("Field is small. Showing expanded regional satellite view."), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        throw Exception("Server Rejected: $errorStr")
                    }
                } else {
                    throw Exception("Failed to create boundary. Code: ${polyResponse.code()}")
                }

                // --- AT THIS POINT, WE GUARANTEE WE HAVE A VALID POLY_ID! ---

                // Get Soil Data
                val soilResponse = AgroRetrofitClient.api.getSoilData(polyId, apiKey)

                // Get Satellite Images (Look back 30 days)
                val endTime = System.currentTimeMillis() / 1000
                val startTime = endTime - (30 * 24 * 60 * 60)
                val imageResponse = AgroRetrofitClient.api.getSatelliteImages(polyId, startTime, endTime, apiKey)

                withContext(Dispatchers.Main) {
                    if (soilResponse.isSuccessful && soilResponse.body() != null) {
                        updateSoilUI(soilResponse.body()!!)
                    }

                    if (imageResponse.isSuccessful && !imageResponse.body().isNullOrEmpty()) {
                        val latestImage = imageResponse.body()!!.maxByOrNull { it.dt }
                        if (latestImage != null) {
                            ndviUrl = latestImage.image.ndvi
                            trueColorUrl = latestImage.image.truecolor

                            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(latestImage.dt * 1000))
                            tvSatelliteDate.text = "${t("Captured")}: $dateStr"

                            loadSatelliteImage(ndviUrl)
                        }
                    }

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

    @SuppressLint("SetTextI18n")
    private fun updateSoilUI(soilData: SoilDataResponse) {
        val moisturePercent = (soilData.moisture * 100).toInt()
        tvMoistureValue.text = "${d(moisturePercent)}%"

        findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressMoisture).progress = moisturePercent

        val surfaceCelsius = (soilData.t0 - 273.15).toInt()
        val depthCelsius = (soilData.t10 - 273.15).toInt()

        tvSurfaceTemp.text = "${d(surfaceCelsius)}°C"
        tvDepthTemp.text = "${d(depthCelsius)}°C"

        if (moisturePercent < 20) {
            tvIrrigationAlert.text = t("Alert: Moisture is critically low. Immediate irrigation is highly recommended.")
            tvIrrigationAlert.setTextColor("#D32F2F".toColorInt())
            tvIrrigationAlert.setBackgroundColor("#FFEBEE".toColorInt())
        } else if (moisturePercent in 20..40) {
            tvIrrigationAlert.text = t("Note: Soil is moderately dry. Plan irrigation soon.")
            tvIrrigationAlert.setTextColor("#F57C00".toColorInt())
            tvIrrigationAlert.setBackgroundColor("#FFF3E0".toColorInt())
        } else {
            tvIrrigationAlert.text = t("Good: Soil moisture is optimal. No immediate irrigation needed.")
            tvIrrigationAlert.setTextColor("#388E3C".toColorInt())
            tvIrrigationAlert.setBackgroundColor("#E8F5E9".toColorInt())
        }

        if (depthCelsius in 20..30) {
            tvSowingAdvice.text = t("Perfect temperature conditions for sowing most crops at 10cm depth.")
        } else if (depthCelsius < 20) {
            tvSowingAdvice.text = t("Soil is quite cool. Sowing might have delayed germination.")
        } else {
            tvSowingAdvice.text = t("Soil is quite hot. Ensure adequate moisture if sowing.")
        }
    }

    private fun loadSatelliteImage(url: String?) {
        if (!url.isNullOrEmpty()) {
            // Force AgroMonitoring's image URLs to be secure so Android doesn't block them!
            val secureUrl = url.replace("http://", "https://")

            Glide.with(this)
                .load(secureUrl)
                .centerCrop()
                .into(ivSatelliteImage)
        }
    }
}