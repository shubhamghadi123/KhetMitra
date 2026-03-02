package com.example.khetmitra

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage

class LanguageSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val isSetupComplete = prefs.getBoolean("IsLanguageSet", false)

        if (isSetupComplete) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_language_selection)

        val recyclerLanguages = findViewById<RecyclerView>(R.id.recyclerLanguages)

        val languages = listOf("English", "हिंदी", "मराठी", "ગુજરાતી", "ಕನ್ನಡ", "தமிழ்", "తెలుగు", "বাংলা")
        val codes = listOf(
            TranslateLanguage.ENGLISH, TranslateLanguage.HINDI, TranslateLanguage.MARATHI,
            TranslateLanguage.GUJARATI, TranslateLanguage.KANNADA, TranslateLanguage.TAMIL,
            TranslateLanguage.TELUGU, TranslateLanguage.BENGALI
        )

        val adapter = LanguageAdapter(languages) { position ->
            val selectedLanguageCode = codes[position]

            prefs.edit {
                putString("Language", selectedLanguageCode)
                putBoolean("IsLanguageSet", true)
            }

            startActivity(Intent(this@LanguageSelectionActivity, LoginActivity::class.java))
            finish()
        }
        recyclerLanguages.adapter = adapter
    }

    inner class LanguageAdapter(
        private val languageList: List<String>,
        private val onLanguageSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

        inner class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardLanguage: MaterialCardView = itemView.findViewById(R.id.cardLanguage)
            val tvLanguageName: TextView = itemView.findViewById(R.id.tvLanguageName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language_card, parent, false)
            return LanguageViewHolder(view)
        }

        override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
            holder.tvLanguageName.text = languageList[position]

            holder.cardLanguage.strokeColor = "#E0E0E0".toColorInt()
            holder.cardLanguage.strokeWidth = 2
            holder.cardLanguage.setCardBackgroundColor(Color.WHITE)

            holder.cardLanguage.setOnClickListener {
                holder.cardLanguage.setCardBackgroundColor("#E8F5E9".toColorInt())
                holder.cardLanguage.strokeColor = "#4CAF50".toColorInt()

                onLanguageSelected(holder.adapterPosition)
            }
        }

        override fun getItemCount(): Int = languageList.size
    }
}