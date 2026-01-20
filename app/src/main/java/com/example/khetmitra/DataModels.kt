package com.example.khetmitra

import android.graphics.Bitmap
import android.net.Uri

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

data class ChatMessage(
    val message: String,
    val isUser: Boolean,
    val imageBitmap: Bitmap? = null,
    val fileUri: Uri? = null,
    val isImage: Boolean = true,
    val fileName: String = ""
)