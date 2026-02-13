package com.example.khetmitra

import android.graphics.Bitmap
import android.net.Uri

// DASHBOARD MODEL (Fixes Unresolved Reference)
data class DataModels(
    val title: String,
    val subtitle: String,
    val iconDrawable: Int
)

// --- OPEN-METEO API MODELS ---
data class OpenMeteoResponse(
    val hourly: HourlyUnits,
    val daily: DailyUnits,
    val current: CurrentUnits
)

data class CurrentUnits(
    val time: String,
    val temperature_2m: Double,
    val relative_humidity_2m: Int,
    val apparent_temperature: Double,
    val is_day: Int,
    val weathercode: Int,
    val surface_pressure: Double,
    val dew_point_2m: Double,
    val wind_speed_10m: Double
)

data class HourlyUnits(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val relative_humidity_2m: List<Int>,
    val weathercode: List<Int>,
    val soil_moisture_3_9cm: List<Double>,
    val soil_temperature_6cm: List<Double>
)

data class DailyUnits(
    val time: List<String>,
    val weathercode: List<Int>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>,
    val precipitation_probability_max: List<Int>,
    val sunrise: List<String>,
    val sunset: List<String>,
    val uv_index_max: List<Double>,
    val et0_fao_evapotranspiration: List<Double>
)

data class AirQualityResponse(
    val current: CurrentAQI
)

data class CurrentAQI(
    val us_aqi: Int // This gives values like 45, 120, 300 (Raw AQI)
)

// UI MODELS (For RecyclerViews)
data class ForecastModel(
    val day: String,
    val date: String,
    val icon: Int,
    val highTemp: String,
    val lowTemp: String
)

data class HourlyModel(
    val time: CharSequence,
    val temp: String,
    val icon: Int
)

data class InsightModel(
    val title: String,
    val description: String,
    val imageRes: Int
)

data class ChatMessage(
    val message: String,
    val isUser: Boolean,
    val imageBitmap: Bitmap? = null,
    val fileUri: Uri? = null,
    val isImage: Boolean = true,
    val fileName: String = ""
)

data class SoilType(
    val id: Int,
    val nameEn: String,
    val imageRes: Int
)