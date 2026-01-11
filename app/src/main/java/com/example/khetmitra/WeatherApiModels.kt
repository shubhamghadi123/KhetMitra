package com.example.khetmitra

// The main response container
data class WeatherResponse(
    val location: LocationData,
    val current: Current,
    val forecast: Forecast
)

data class LocationData(
    val name: String,
    val region: String
)

data class Current(
    val temp_c: Double,
    val feelslike_c: Double,
    val condition: Condition
)

data class Forecast(
    val forecastday: List<ForecastDay>
)

data class ForecastDay(
    val date: String, // "2023-10-25"
    val day: Day
)

data class Day(
    val avgtemp_c: Double,
    val condition: Condition,
    val daily_chance_of_rain: Int // useful for insights
)

data class Condition(
    val text: String, // e.g., "Sunny", "Partly cloudy"
    val code: Int
)