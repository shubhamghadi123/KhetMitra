package com.example.khetmitra

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("v1/forecast")
    fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,

        // 1. REMOVED "models" (This fixes the 0° issue)

        // 2. Keep this at 16 to prevent "Next 14 Days" crashes
        @Query("forecast_days") forecastDays: Int = 16,

        // 3. Keep "auto" to fix the High/Low temperature accuracy
        @Query("timezone") timezone: String = "auto",

        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weathercode,surface_pressure,dew_point_2m,wind_speed_10m",
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,weathercode,soil_moisture_3_9cm,soil_temperature_6cm",
        @Query("daily") daily: String = "weathercode,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max,et0_fao_evapotranspiration"
    ): Call<OpenMeteoResponse>
}

interface AirQualityService {
    @GET("v1/air-quality")
    fun getAirQuality(
        @Query("latitude") lat: Double,
        @Query("longitude") long: Double,
        @Query("current") current: String = "us_aqi"
    ): Call<AirQualityResponse>
}