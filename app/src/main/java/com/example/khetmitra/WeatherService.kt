package com.example.khetmitra

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("forecast.json")
    fun getForecast(
        @Query("key") key: String,
        @Query("q") q: String,
        @Query("days") days: Int,
        @Query("aqi") aqi: String = "yes",
        @Query("alerts") alerts: String = "no"
    ): Call<WeatherResponse>
}