package com.example.khetmitra

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.nl.translate.TranslateLanguage

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dashboardItems = listOf(
            DashboardModel("Weather", "Sunny, 24°C", R.drawable.ic_weather),
            DashboardModel("Plans", "3 tasks for today", R.drawable.ic_plans),
            DashboardModel("Chat", "2 new messages", R.drawable.ic_chat),
            DashboardModel("Market", "Up by 10%", R.drawable.ic_market)
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DashboardAdapter(dashboardItems)

        setupLanguageSpinner()
        recyclerView.post {
            val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

            if (langCode != TranslateLanguage.ENGLISH) {
                TranslationHelper.translateViewHierarchy(recyclerView, langCode!!) {}
            }
        }
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<Spinner>(R.id.languageSpinner)

        val languages = listOf("English", "हिंदी", "मराठी", "ગુજરાતી", "ಕನ್ನಡ", "தமிழ்", "తెలుగు", "বাংলা")
        val codes = listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.HINDI,
            TranslateLanguage.MARATHI,
            TranslateLanguage.GUJARATI,
            TranslateLanguage.KANNADA,
            TranslateLanguage.TAMIL,
            TranslateLanguage.TELUGU,
            TranslateLanguage.BENGALI
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

        val index = codes.indexOf(currentLangCode)
        if (index >= 0) {
            spinner.setSelection(index, false)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCode = codes[position]
                val savedCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

                if (selectedCode != savedCode) {
                    prefs.edit().putString("Language", selectedCode).apply()
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
}