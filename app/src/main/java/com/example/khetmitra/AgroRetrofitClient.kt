package com.example.khetmitra

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AgroRetrofitClient {

    val api: AgroMonitoringApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.AGRO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgroMonitoringApi::class.java)
    }
}