package com.example.khetmitra

data class DataModels(
    val title: String,
    val subtitle: String,
    val iconDrawable: Int
)

data class ForecastModel(
    val day: String,
    val date: String,
    val icon: Int,
    val highTemp: String,
    val lowTemp: String
)

data class InsightModel(
    val title: String,
    val description: String,
    val imageRes: Int
)