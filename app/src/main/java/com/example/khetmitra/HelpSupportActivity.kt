package com.example.khetmitra

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage

class HelpSupportActivity : AppCompatActivity() {
    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_support)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // WhatsApp Support Click
        findViewById<LinearLayout>(R.id.rowWhatsApp).setOnClickListener {
            val supportNumber = "+919876543210" // Replace with your actual WhatsApp support number
            val message = "Hello KhetMitra Team, I need some help with the app."

            try {
                val uri =
                    "https://api.whatsapp.com/send?phone=$supportNumber&text=${Uri.encode(message)}".toUri()
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, t("WhatsApp is not installed on your device."), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<LinearLayout>(R.id.rowCall).setOnClickListener {
            val helplineNumber = "+911234567890"
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = "tel:$helplineNumber".toUri()
            startActivity(intent)
        }

        // Video Tutorials Click
        findViewById<LinearLayout>(R.id.rowTutorials).setOnClickListener {
            // val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/your_playlist_link"))
            // startActivity(intent)
            Toast.makeText(this, t("Video tutorials coming soon!"), Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.rowFAQ).setOnClickListener {
            val intent = Intent(this, FAQActivity::class.java)
            startActivity(intent)
        }

        if (langCode != TranslateLanguage.ENGLISH) {
            val rootView = findViewById<View>(android.R.id.content)
            rootView.post {
                TranslationHelper.translateViewHierarchy(rootView, langCode) {}
            }
        }
    }
}