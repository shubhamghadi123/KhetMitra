package com.example.khetmitra

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FarmPlanActivity : AppCompatActivity() {

    private var savedFarms: List<FarmEntry> = emptyList()
    private var availableCrops: List<CropRow> = emptyList()
    private var selectedFarm: FarmEntry? = null
    private var selectedCrop: CropRow? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_farm_plan)

        rvPlanStages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        lifecycleScope.launch {
            loadFarms()
            loadCrops()
        }

        btnGeneratePlan.setOnClickListener {
            if (selectedFarm == null || selectedCrop == null) {
                Toast.makeText(this, "Please select a farm and a crop", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generatePlanWorkflow()
        }
    }

    private suspend fun loadFarms() {
        // Fetch farms from Supabase for the current user
        // Populate dropdownFarm and set selectedFarm on item click
    }

    private suspend fun loadCrops() {
        // Fetch crops from Supabase
        // Populate dropdownCrop and set selectedCrop on item click
    }

    private fun generatePlanWorkflow() {
        layoutLoading.visibility = View.VISIBLE
        rvPlanStages.visibility = View.GONE
        tvPlanTitle.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Get Coordinates from selectedFarm
                val coords = selectedFarm?.coordinates

                withContext(Dispatchers.Main) { tvLoadingStatus.text = "Fetching future weather forecasts..." }
                // 2. Fetch Weather Data (Open-Meteo API using coords)
                // val weatherData = fetchFutureWeather(coords.lat, coords.lng)

                withContext(Dispatchers.Main) { tvLoadingStatus.text = "Analyzing Agromonitoring soil data..." }
                // 3. Fetch Agromonitoring Data
                // val soilData = fetchAgroMonitoringData(coords.polyId)

                withContext(Dispatchers.Main) { tvLoadingStatus.text = "Generating AI Farm Plan..." }
                // 4. GENERATE PLAN (Pass Weather, Soil, Crop, and Farm size to AI)
                val generatedStages = buildAIPlan(selectedFarm!!, selectedCrop!!.cropName /*, weatherData, soilData */)

                withContext(Dispatchers.Main) {
                    // Update UI
                    layoutLoading.visibility = View.GONE
                    tvPlanTitle.visibility = View.VISIBLE
                    rvPlanStages.visibility = View.VISIBLE
                    rvPlanStages.adapter = PlanStageAdapter(generatedStages)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(this@FarmPlanActivity, "Failed to generate plan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // THE BRAIN: This is where you would call the Gemini API.
    // For now, it builds a dynamic mock based on the selected crop.
    private suspend fun buildAIPlan(farm: FarmEntry, cropName: String): List<FarmPlanStage> {
        delay(2000) // Simulating AI processing time

        // In reality, you would build a prompt:
        // "I am planting $cropName in ${farm.soil_type} soil. The weather next week is rainy. Generate a 5-stage farming plan in JSON format."

        return listOf(
            FarmPlanStage(1, "1. SOIL PREP", R.drawable.ic_tractor, listOf("- Plough field", "- Apply base fertilizer based on soil report"), "20h", 20, 20, Color.parseColor("#D28F6B")),
            FarmPlanStage(2, "2. CROPPING", R.drawable.ic_seeds, listOf("- Sow $cropName seeds", "- Maintain spacing"), "20h", 20, 20, Color.parseColor("#74A582")),
            FarmPlanStage(3, "3. MAINTAINING", R.drawable.ic_tools, listOf("- Weeding", "- Monitor weather alerts for rain"), "20h", 30, 30, Color.parseColor("#6FA7C7")),
            FarmPlanStage(4, "4. FERTILIZING", R.drawable.ic_fertilizer, listOf("- Apply Nitrogen", "- Spray pesticides if required"), "10h", 10, 10, Color.parseColor("#74A582")),
            FarmPlanStage(5, "5. HARVESTING", R.drawable.ic_harvest, listOf("- Check $cropName maturity", "- Cut and process"), "20h", 20, 20, Color.parseColor("#DDA255"))
        )
    }
}