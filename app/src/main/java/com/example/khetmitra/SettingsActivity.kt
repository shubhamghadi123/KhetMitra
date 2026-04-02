package com.example.khetmitra

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.mlkit.nl.translate.TranslateLanguage

class SettingsActivity : AppCompatActivity() {
    private var langCode: String = TranslateLanguage.ENGLISH
    private var activeToast: Toast? = null

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun showInstantToast(message: String) {
        activeToast?.cancel()
        activeToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        activeToast?.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // --- VOICE SWITCH ---
        val switchVoice = findViewById<SwitchMaterial>(R.id.switchVoice)
        switchVoice.isChecked = prefs.getBoolean("VoiceOutputEnabled", true)
        switchVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("VoiceOutputEnabled", isChecked) }
            val status = if (isChecked) "enabled" else "disabled"
            showInstantToast("${t("Voice Output")} ${t(status)}")
        }

        // --- LANGUAGE SELECTION ---
        val tvCurrentLanguage = findViewById<TextView>(R.id.tvCurrentLanguage)
        val languages = arrayOf("English", "हिंदी", "मराठी", "ગુજરાતી", "ಕನ್ನಡ", "தமிழ்", "తెలుగు", "বাংলা")
        val codes = arrayOf(
            TranslateLanguage.ENGLISH, TranslateLanguage.HINDI, TranslateLanguage.MARATHI,
            TranslateLanguage.GUJARATI, TranslateLanguage.KANNADA, TranslateLanguage.TAMIL,
            TranslateLanguage.TELUGU, TranslateLanguage.BENGALI
        )

        tvCurrentLanguage.text = ""
        findViewById<LinearLayout>(R.id.rowLanguage).setOnClickListener {
            val latestCode = prefs.getString("Language", TranslateLanguage.ENGLISH)
            val selectedIndex = codes.indexOf(latestCode).takeIf { it >= 0 } ?: 0
            android.app.AlertDialog.Builder(this)
                .setTitle(t("Select App Language"))
                .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                    val newCode = codes[which]
                    val newName = languages[which]
                    prefs.edit { putString("Language", newCode) }
                    getSharedPreferences("AppNotifications", MODE_PRIVATE).edit { putString("history", "[]") }
                    tvCurrentLanguage.text = newName
                    showInstantToast("${t("Language changed to")} $newName")
                    dialog.dismiss()
                    val intent = android.content.Intent(this, MainActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }

        // --- TEMPERATURE UNIT SELECTION ---
        val tvCurrentTempUnit = findViewById<TextView>(R.id.tvCurrentTempUnit)
        val tempUnitsEnglish = arrayOf("Celsius (°C)", "Fahrenheit (°F)")
        val tempUnitsTranslated = tempUnitsEnglish.map { t(it) }.toTypedArray()

        tvCurrentTempUnit.text = ""
        findViewById<LinearLayout>(R.id.rowTempUnit).setOnClickListener {
            val latestUnit = prefs.getString("TempUnit", "Celsius (°C)")
            val selectedIndex = tempUnitsEnglish.indexOf(latestUnit).takeIf { it >= 0 } ?: 0

            android.app.AlertDialog.Builder(this)
                .setTitle(t("Select Temperature Unit"))
                .setSingleChoiceItems(tempUnitsTranslated, selectedIndex) { dialog, which ->
                    val newUnitEnglish = tempUnitsEnglish[which]
                    prefs.edit { putString("TempUnit", newUnitEnglish) }
                    tvCurrentTempUnit.text = tempUnitsTranslated[which]
                    showInstantToast("${t("Temperature Unit")}: ${tempUnitsTranslated[which]}")
                    dialog.dismiss()
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }

        // --- WIND SPEED UNIT SELECTION ---
        val tvCurrentWindUnit = findViewById<TextView>(R.id.tvCurrentWindUnit)
        val windUnitsEnglish = arrayOf("km/h", "m/h")
        val windUnitsTranslated = windUnitsEnglish.map { t(it) }.toTypedArray()

        tvCurrentWindUnit.text = ""
        findViewById<LinearLayout>(R.id.rowWindUnit).setOnClickListener {
            val latestUnit = prefs.getString("WindUnit", "km/h")
            val selectedIndex = windUnitsEnglish.indexOf(latestUnit).takeIf { it >= 0 } ?: 0

            android.app.AlertDialog.Builder(this)
                .setTitle(t("Select Wind Speed Unit"))
                .setSingleChoiceItems(windUnitsTranslated, selectedIndex) { dialog, which ->
                    val newUnitEnglish = windUnitsEnglish[which]
                    prefs.edit { putString("WindUnit", newUnitEnglish) }
                    tvCurrentWindUnit.text = windUnitsTranslated[which]
                    showInstantToast("${t("Wind Speed Unit")}: ${windUnitsTranslated[which]}")
                    dialog.dismiss()
                }
                .setNegativeButton(t("Cancel"), null)
                .show()
        }

        // --- NOTIFICATION SWITCHES ---
        val switchWeather = findViewById<SwitchMaterial>(R.id.switchWeatherAlerts)
        val switchMarket = findViewById<SwitchMaterial>(R.id.switchMarketAlerts)
        val switchTasks = findViewById<SwitchMaterial>(R.id.switchTaskReminders)
        switchWeather.isChecked = prefs.getBoolean("WeatherAlertsEnabled", true)
        switchMarket.isChecked = prefs.getBoolean("MarketAlertsEnabled", true)
        switchTasks.isChecked = prefs.getBoolean("TaskAlertsEnabled", true)
        switchWeather.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("WeatherAlertsEnabled", isChecked) }
            if (isChecked) {
                val weatherWorkRequest = androidx.work.PeriodicWorkRequestBuilder<WeatherAlertWorker>(12, java.util.concurrent.TimeUnit.HOURS).build()
                androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "WeatherAlertJob",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    weatherWorkRequest
                )
                showInstantToast("${t("Weather Alerts")} ${t("enabled")}")
            } else {
                androidx.work.WorkManager.getInstance(this).cancelUniqueWork("WeatherAlertJob")
                showInstantToast("${t("Weather Alerts")} ${t("disabled")}")
            }
        }
        switchMarket.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("MarketAlertsEnabled", isChecked) }
            val status = if (isChecked) "enabled" else "disabled"
            showInstantToast("${t("Market Price Alerts")} ${t(status)}")
        }

        switchTasks.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("TaskAlertsEnabled", isChecked) }
            val status = if (isChecked) "enabled" else "disabled"
            showInstantToast("${t("Daily Task Reminders")} ${t(status)}")
        }

        val loadDynamicContent = {
            val currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
            var currentIndex = codes.indexOf(currentLangCode)
            if (currentIndex < 0) currentIndex = 0
            tvCurrentLanguage.text = languages[currentIndex]
            val currentTempUnit = prefs.getString("TempUnit", "Celsius (°C)") ?: "Celsius (°C)"
            val tempIndex = tempUnitsEnglish.indexOf(currentTempUnit).takeIf { it >= 0 } ?: 0
            tvCurrentTempUnit.text = tempUnitsTranslated[tempIndex]
            val currentWindUnit = prefs.getString("WindUnit", "km/h") ?: "km/h"
            val windIndex = windUnitsEnglish.indexOf(currentWindUnit).takeIf { it >= 0 } ?: 0
            tvCurrentWindUnit.text = windUnitsTranslated[windIndex]
        }
        if (langCode != TranslateLanguage.ENGLISH) {
            val rootView = findViewById<View>(android.R.id.content)
            rootView.post {
                TranslationHelper.translateViewHierarchy(rootView, langCode) {
                    rootView.postDelayed({
                        loadDynamicContent()
                    }, 200)
                }
            }
        } else {
            loadDynamicContent()
        }
    }
}