package com.example.khetmitra

data class DataModels(
    val title: String,
    val subtitle: String,
    val iconDrawable: Int
)

data class ForecastModel(
    val dayName: String,
    val weatherIcon: Int,
    val dayTemp: String
)

data class InsightModel(
    val title: String,
    val description: String
)