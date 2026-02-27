package com.example.khetmitra

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.mlkit.nl.translate.TranslateLanguage

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)


        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val switchVoice = findViewById<SwitchMaterial>(R.id.switchVoice)
        switchVoice.isChecked = prefs.getBoolean("VoiceOutputEnabled", true)

        switchVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("VoiceOutputEnabled", isChecked) }
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(this, "Voice output $status", Toast.LENGTH_SHORT).show()
        }

        // --- 3. Setup Language Click ---
        val tvCurrentLanguage = findViewById<TextView>(R.id.tvCurrentLanguage)

        // Arrays for display names and their ML Kit codes
        val languages = arrayOf("English", "हिंदी", "मराठी", "ગુજરાતી", "ಕನ್ನಡ", "தமிழ்", "తెలుగు", "বাংলা")
        val codes = arrayOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.HINDI,
            TranslateLanguage.MARATHI,
            TranslateLanguage.GUJARATI,
            TranslateLanguage.KANNADA,
            TranslateLanguage.TAMIL,
            TranslateLanguage.TELUGU,
            TranslateLanguage.BENGALI
        )

        // Find currently saved language to show on the screen right away
        val currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        var currentIndex = codes.indexOf(currentLangCode)
        if (currentIndex < 0) currentIndex = 0

        tvCurrentLanguage.text = languages[currentIndex]

        // Handle the row click
        findViewById<LinearLayout>(R.id.rowLanguage).setOnClickListener {

            // Get the latest saved index in case it changed
            val latestCode = prefs.getString("Language", TranslateLanguage.ENGLISH)
            val selectedIndex = codes.indexOf(latestCode).takeIf { it >= 0 } ?: 0

            // Build and show the pop-up dialog
            android.app.AlertDialog.Builder(this)
                .setTitle("Select App Language")
                .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                    val newCode = codes[which]
                    val newName = languages[which]

                    // Save the new language
                    prefs.edit { putString("Language", newCode) }

                    // Update the text in the settings menu
                    tvCurrentLanguage.text = newName
                    Toast.makeText(this, "Language changed to $newName", Toast.LENGTH_SHORT).show()

                    dialog.dismiss()

                    // OPTIONAL BUT RECOMMENDED:
                    // Restart MainActivity so the whole app translates immediately
                    val intent = android.content.Intent(this, MainActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<LinearLayout>(R.id.rowMarket).setOnClickListener {
            Toast.makeText(this, "Open Market Selector", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.rowUnit).setOnClickListener {
            Toast.makeText(this, "Open Unit Selector (Acres/Hectares)", Toast.LENGTH_SHORT).show()
        }
    }
}