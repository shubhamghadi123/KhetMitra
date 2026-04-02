package com.example.khetmitra

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherAlertWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

            val isEnabled = prefs.getBoolean("WeatherAlertsEnabled", true)
            if (!isEnabled) {
                Log.d("WeatherWorker", "Alerts are disabled in settings. Aborting.")
                return@withContext Result.success()
            }

            val lat = prefs.getString("LastWeatherLat", "19.0760")?.toDoubleOrNull() ?: 19.0760
            val lon = prefs.getString("LastWeatherLon", "72.8777")?.toDoubleOrNull() ?: 72.8777

            fun t(text: String): String {
                if (langCode == TranslateLanguage.ENGLISH) return text
                return TranslationHelper.getManualTranslation(text, langCode) ?: text
            }
            fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

            val isMph = prefs.getString("WindUnit", "km/h")?.contains("mph") == true
            val windSymbol = if (isMph) t("m/h") else t("km/h")

            val response = RetrofitClient.weatherService.getForecast(lat, lon)

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!

                val todayMaxTemp = data.daily.temperature_2m_max.firstOrNull() ?: 0.0
                val todayMinTemp = data.daily.temperature_2m_min.firstOrNull() ?: 0.0
                val todayRain = data.daily.precipitation_probability_max.firstOrNull() ?: 0
                val windSpeedMetric = data.current.wind_speed_10m

                val windSpeedDisplay = if (isMph) (windSpeedMetric * 0.621371).toInt() else windSpeedMetric.toInt()

                var alertTitle = ""
                var alertMessage = ""

                if (todayMinTemp < 5.0) {
                    alertTitle = t("Frost Warning") + " ❄️"
                    alertMessage = "${t("Temperatures dropping to")} ${d(todayMinTemp.toInt())}°. ${t("Apply light irrigation to protect crops from frost")}."
                } else if (todayMaxTemp > 38.0) {
                    alertTitle = t("Heat Stress Alert") + " ☀️"
                    alertMessage = "${t("Extreme heat")} (${d(todayMaxTemp.toInt())}°). ${t("Ensure adequate soil moisture and avoid afternoon spraying")}."
                } else if (windSpeedMetric > 15) {
                    alertTitle = t("Spraying Alert") + " 💨"
                    alertMessage = "${t("Wind is too strong")} (${d(windSpeedDisplay)} $windSymbol). ${t("Avoid spraying to prevent chemical drift")}."
                } else if (todayRain > 50) {
                    alertTitle = t("Rainfall Alert") + " 🌧️"
                    alertMessage = "${t("High chance of rain")} (${d(todayRain)}%). ${t("Delay spraying fertilizers and pesticides")}."
                }

                if (alertTitle.isNotEmpty()) {
                    NotificationHelper.showNotification(context, alertTitle, alertMessage)
                    Log.d("WeatherWorker", "Alert Triggered: $alertTitle")
                }

                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Worker failed", e)
            Result.failure()
        }
    }
}