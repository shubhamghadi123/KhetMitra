package com.example.khetmitra

import android.graphics.Bitmap
import android.net.Uri

// DASHBOARD MODEL (Fixes Unresolved Reference)
data class DataModels(
    val title: String,
    val subtitle: String,
    val iconDrawable: Int
)

// WEATHER API RESPONSE MODELS
data class WeatherResponse(
    val location: Location,
    val current: Current,
    val forecast: Forecast
)

data class Location(
    val name: String,
    val region: String,
    val country: String
)

data class Current(
    val temp_c: Double,
    val condition: Condition,
    val wind_kph: Double,
    val humidity: Int,
    val feelslike_c: Double,
    val uv: Double,
    val air_quality: AirQuality?,
    val dewpoint_c: Double,
    val is_day: Int
)

data class Condition(
    val text: String,
    val icon: String,
    val code: Int
)

data class AirQuality(
    val us_epa_index: Int
)

data class Forecast(
    val forecastday: List<ForecastDay>
)

data class ForecastDay(
    val date: String,
    val day: Day,
    val astro: Astro,
    val hour: List<Hour>
)

data class Day(
    val maxtemp_c: Double,
    val mintemp_c: Double,
    val avgtemp_c: Double,
    val condition: Condition,
    val daily_chance_of_rain: Int,
    val uv: Double
)

data class Astro(
    val sunrise: String,
    val sunset: String
)

data class Hour(
    val time: String,
    val temp_c: Double,
    val condition: Condition,
    val is_day: Int
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