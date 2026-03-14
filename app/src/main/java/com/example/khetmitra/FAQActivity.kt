package com.example.khetmitra

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage

class FAQActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faq)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        setupAccordion(R.id.headerFaq1, R.id.contentFaq1, R.id.arrowFaq1)
        setupAccordion(R.id.headerFaq2, R.id.contentFaq2, R.id.arrowFaq2)
        setupAccordion(R.id.headerFaq3, R.id.contentFaq3, R.id.arrowFaq3)
        setupAccordion(R.id.headerFaq4, R.id.contentFaq4, R.id.arrowFaq4)

        if (langCode != TranslateLanguage.ENGLISH) {
            val rootView = findViewById<View>(android.R.id.content)
            rootView.post {
                TranslationHelper.translateViewHierarchy(rootView, langCode) {}
            }
        }
    }

    private fun setupAccordion(headerId: Int, contentId: Int, arrowId: Int) {
        val header = findViewById<View>(headerId)
        val content = findViewById<TextView>(contentId)
        val arrow = findViewById<ImageView>(arrowId)
        header.setOnClickListener {
            if (content.isGone) {
                content.visibility = View.VISIBLE
                arrow.animate().rotation(180f).setDuration(200).start()
            } else {
                content.visibility = View.GONE
                arrow.animate().rotation(0f).setDuration(200).start()
            }
        }
    }
}