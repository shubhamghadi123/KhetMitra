package com.example.khetmitra

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreatePlanActivity : AppCompatActivity() {
    private lateinit var spinnerFarm: android.widget.Spinner
    private lateinit var spinnerCrop: android.widget.Spinner
    private lateinit var btnGeneratePlan: MaterialCardView
    private var farmsList: List<FarmEntry> = emptyList()
    private var cropsList: List<CropRow> = emptyList()
    private var selectedFarm: FarmEntry? = null
    private var selectedCrop: CropRow? = null
    private lateinit var progressGenerate: ProgressBar
    private lateinit var tvBtnGenerateLabel: TextView
    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun android.widget.Spinner.applyCustomStyle(items: List<String>) {
        val adapter = ArrayAdapter(context, R.layout.custom_spinner_item, items)
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item)
        this.adapter = adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_plan)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        
        spinnerFarm = findViewById(R.id.spinnerFarm)
        spinnerCrop = findViewById(R.id.spinnerCrop)
        btnGeneratePlan = findViewById(R.id.btnGeneratePlan)
        progressGenerate = findViewById(R.id.progressGenerate)
        tvBtnGenerateLabel = findViewById(R.id.tvBtnGenerateLabel)
        
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }

        if (langCode != TranslateLanguage.ENGLISH) {
            window.decorView.post {
                TranslationHelper.translateViewHierarchy(window.decorView.rootView, langCode) {}
                translateHints()
            }
        }

        lifecycleScope.launch {
            loadFarms()
            loadCrops()
        }

        btnGeneratePlan.setOnClickListener {
            if (selectedFarm == null || selectedCrop == null) {
                Toast.makeText(this, t("Please select a field and crop"), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generatePlanWorkflow()
        }
    }

    private fun translateHints() {
        if (langCode == TranslateLanguage.ENGLISH) return
        findViewById<TextView>(R.id.tvFarmLabel)?.text = t("Select Field")
        findViewById<TextView>(R.id.tvCropLabel)?.text = t("Select Crop")
    }

    private suspend fun loadFarms() {
        try {
            val user = SupabaseManager.client.auth.currentUserOrNull() ?: return
            farmsList = SupabaseManager.client.postgrest["farms"]
                .select { filter { eq("farmer_id", user.id) } }
                .decodeList<FarmEntry>()
            val names = farmsList.map { farm ->
                val farmName = farm.name ?: "Unnamed Farm"
                if (farm.land_size.isNotBlank()) {
                    "$farmName (${farm.land_size})"
                } else {
                    farmName
                }
            }
            withContext(Dispatchers.Main) {
                spinnerFarm.applyCustomStyle(names)
                spinnerFarm.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position in farmsList.indices) {
                            selectedFarm = farmsList[position]
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadCrops() {
        try {
            cropsList = SupabaseManager.client.postgrest["crops"]
                .select { filter { eq("status", 1) }
                    filter { eq("crop_group_id", 1) }
                    order("crop_name", Order.ASCENDING)
                }
                .decodeList<CropRow>()
            val names = cropsList.map { t(it.cropName) }
            withContext(Dispatchers.Main) {
                spinnerCrop.applyCustomStyle(names)
                spinnerCrop.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position in cropsList.indices) {
                            selectedCrop = cropsList[position]
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generatePlanWorkflow() {
        btnGeneratePlan.isClickable = false
        progressGenerate.visibility = View.VISIBLE
        tvBtnGenerateLabel.text = t("Checking existing plans...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull() ?: throw Exception("User not logged in")

                val targetFarmName = selectedFarm?.name ?: "Unknown Farm"
                val targetCropName = selectedCrop?.cropName ?: "Unknown Crop"

                val existingPlans = SupabaseManager.client.postgrest["farm_plans"]
                    .select {
                        filter {
                            eq("farmer_id", user.id)
                            eq("farm_name", targetFarmName)
                            eq("crop_name", targetCropName)
                        }
                    }.decodeList<SavedFarmPlan>()
                if (existingPlans.isNotEmpty()) {
                    val existingPlan = existingPlans.first()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CreatePlanActivity,
                            t("A $targetCropName plan already exists for $targetFarmName. Opening it now!"),
                            Toast.LENGTH_LONG
                        ).show()
                        val intent = android.content.Intent(this@CreatePlanActivity, ViewPlanActivity::class.java)
                        intent.putExtra("PLAN_JSON", existingPlan.plan_json)
                        intent.putExtra("FARM_NAME", existingPlan.farm_name)
                        intent.putExtra("CROP_NAME", existingPlan.crop_name)
                        startActivity(intent)
                        finish()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) { tvBtnGenerateLabel.text = t("Fetching live weather...") }
                var lat = 19.07
                var lon = 72.87
                try {
                    val coords = selectedFarm!!.coordinates
                    val parts = coords.split(",")
                    lat = parts[0].trim().toDouble()
                    lon = parts[1].trim().toDouble()
                } catch (_: Exception) {
                    Log.e("PlanWorkflow", "Using default coordinates due to parsing error.")
                }
                var liveWeatherSummary = "Assume typical seasonal conditions."
                try {
                    val weatherResponse = RetrofitClient.weatherService.getForecast(lat, lon)
                    if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                        val dailyData = weatherResponse.body()?.daily
                        liveWeatherSummary = "Live 16-day forecast JSON data: " + Gson().toJson(dailyData)
                    }
                } catch (_: Exception) {
                    Log.e("PlanWorkflow", "Failed to fetch weather.")
                }

                withContext(Dispatchers.Main) { tvBtnGenerateLabel.text = t("Analyzing Soil Data...") }
                var liveFieldData = "No live satellite/sensor data available for this farm."
                try {
                    val allMonitoringData = SupabaseManager.client.postgrest["field_monitoring"]
                        .select { filter { eq("user_id", user.id) } }
                        .decodeList<FieldMonitoring>()

                    val farmMonitoring = allMonitoringData.find {
                        it.polygon_id == selectedFarm?.polygon_id || it.field_name == selectedFarm?.name
                    }
                    if (farmMonitoring != null) {
                        liveFieldData = """
                        Live Soil & Satellite Status:
                        - Soil Moisture: ${farmMonitoring.soil_moisture ?: "Unknown"}
                        - Soil Temperature: ${farmMonitoring.soil_temperature ?: "Unknown"} °C
                        - Health Score (NDVI): ${farmMonitoring.ndvi_score ?: "Unknown"}
                        - Temperature: ${farmMonitoring.temperature ?: "Unknown"}
                        - Humidity: ${farmMonitoring.humidity ?: "Unknown"}
                        - Weather Condition: ${farmMonitoring.weather_condition ?: "Unknown"}
                    """.trimIndent()
                    }
                } catch (e: Exception) {
                    Log.e("PlanWorkflow", "Failed to fetch field monitoring data.", e)
                }

                withContext(Dispatchers.Main) { tvBtnGenerateLabel.text = t("Generating AI Plan...") }
                val aiResponse = buildAIPlan(
                    farm = selectedFarm!!,
                    cropName = selectedCrop!!.cropName,
                    weatherSummary = liveWeatherSummary,
                    liveFieldData = liveFieldData,
                    langCode = langCode
                )

                if (aiResponse != null) {
                    withContext(Dispatchers.Main) { tvBtnGenerateLabel.text = t("Saving plan...") }
                    savePlanToSupabase(aiResponse)
                    withContext(Dispatchers.Main) {
                        val intent = android.content.Intent(this@CreatePlanActivity, ViewPlanActivity::class.java)
                        intent.putExtra("PLAN_JSON", Gson().toJson(aiResponse))
                        intent.putExtra("FARM_NAME", selectedFarm?.name ?: "Unknown Farm")
                        intent.putExtra("CROP_NAME", selectedCrop?.cropName ?: "Unknown Crop")
                        startActivity(intent)
                        finish()
                    }
                } else {
                    throw Exception("AI returned null")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    btnGeneratePlan.isClickable = true
                    progressGenerate.visibility = View.GONE
                    tvBtnGenerateLabel.text = t("Generate AI Plan")
                    Toast.makeText(this@CreatePlanActivity, t("Failed to generate plan: ${e.message}"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun savePlanToSupabase(plan: FarmPlanResponse) {
        try {
            val user = SupabaseManager.client.auth.currentUserOrNull() ?: return
            val jsonString = Gson().toJson(plan)
            val newPlan = SavedFarmPlan(
                farmer_id = user.id,
                farm_name = selectedFarm?.name ?: "Unknown Farm",
                crop_name = selectedCrop?.cropName ?: "Unknown Crop",
                plan_json = jsonString
            )
            SupabaseManager.client.postgrest["farm_plans"].insert(newPlan)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun buildAIPlan(
        farm: FarmEntry,
        cropName: String,
        weatherSummary: String,
        liveFieldData: String,
        langCode: String
    ): FarmPlanResponse? {
        return try {
            val config = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.4f
            }
            val generativeModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = BuildConfig.GEMINI_API_KEY,
                generationConfig = config
            )
            val farmSize = farm.land_size
            val soilType = farm.soil_type
            val languageName = when(langCode) {
                "mr" -> "Marathi" "hi" -> "Hindi" "gu" -> "Gujarati"
                "kn" -> "Kannada" "ta" -> "Tamil" "te" -> "Telugu" "bn" -> "Bengali"
                else -> "English"
            }
            val prompt = """
                You are an expert agronomist in India. Generate a highly detailed, end-to-end crop management plan.
                
                CONTEXT:
                - Crop: $cropName
                - Land Area: $farmSize
                - Soil Type: $soilType
                - Upcoming Weather: $weatherSummary
                - Current Field Status: $liveFieldData
                - Language: $languageName
                
                INSTRUCTIONS:
                1. Output STRICTLY as a JSON object. Do not include markdown formatting.
                2. ALL string values MUST be translated into $languageName.
                3. Create exactly 5 stages: Soil Preparation, Cropping/Sowing, Maintaining, Fertilizing, Harvesting.
                4. CRITICAL: The "steps" array MUST be extremely short, punchy bullet points. NEVER write paragraphs. MAXIMUM 8 WORDS PER STEP.
                
                EXPECTED JSON SCHEMA:
                {
                  "estimatedYield": "string (e.g., '12-15 Quintals')",
                  "totalDurationDays": "string (e.g., '120 Days')",
                  "stages": [
                    {
                      "stageNumber": 1,
                      "stageTitle": "string (e.g., '1. SOIL PREPARATION')",
                      "steps": [
                        "string (MAX 8 WORDS. Extremely concise step 1)", 
                        "string (MAX 8 WORDS. Extremely concise step 2)"
                      ],
                      "durationText": "string (e.g., '5 Days')",
                      "effortPercent": 20,
                      "criticality": 80
                    }
                  ]
                }
            """.trimIndent()
            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text ?: "{}"
            val planResponse = Gson().fromJson(jsonText, FarmPlanResponse::class.java)
            planResponse.stages.forEach { applyUIStyling(it) }
            planResponse
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applyUIStyling(stage: FarmPlanStage) {
        when (stage.stageNumber) {
            1 -> { stage.iconRes = R.drawable.ic_tractor; stage.cardColor = "#D28F6B".toColorInt() }
            2 -> { stage.iconRes = R.drawable.ic_seeds; stage.cardColor = "#74A582".toColorInt() }
            3 -> { stage.iconRes = R.drawable.ic_tools; stage.cardColor = "#6FA7C7".toColorInt() }
            4 -> { stage.iconRes = R.drawable.ic_fertilizer; stage.cardColor = "#74A582".toColorInt() }
            5 -> { stage.iconRes = R.drawable.ic_harvest; stage.cardColor = "#DDA255".toColorInt()
            }
            else -> { stage.iconRes = android.R.drawable.ic_menu_info_details; stage.cardColor =
                "#999999".toColorInt() }
        }
    }
}