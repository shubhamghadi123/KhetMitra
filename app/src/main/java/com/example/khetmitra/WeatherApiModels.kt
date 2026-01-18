package com.example.khetmitra

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
    val condition: Condition,
    val humidity: Int,
    val uv: Double,
    val dewpoint_c: Double,
    val air_quality: AirQuality?
)

data class AirQuality(
    @com.google.gson.annotations.SerializedName("us-epa-index")
    val us_epa_index: Int
)

data class Forecast(
    val forecastday: List<ForecastDay>
)

data class ForecastDay(
    val date: String,
    val day: Day,
    val astro: Astro
)

data class Astro(
    val sunrise: String,
    val sunset: String
)

data class Day(
    val avgtemp_c: Double,
    val maxtemp_c: Double,
    val mintemp_c: Double,
    val daily_chance_of_rain: Int,
    val condition: Condition
)

data class Condition(
    val text: String,
    val code: Int
)