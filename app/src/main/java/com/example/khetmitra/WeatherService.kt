package com.example.khetmitra

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("forecast.json")
    fun getForecast(
        @Query("key") apiKey: String,
        @Query("q") city: String,
        @Query("days") days: Int = 7,
        @Query("aqi") aqi: String = "yes"
    ): Call<WeatherResponse>
}