package com.example.khetmitra

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.TranslateLanguage

open class BaseActivity : AppCompatActivity() {

    private lateinit var loadingOverlay: FrameLayout

    override fun setContentView(layoutResID: Int) {
        val userView = layoutInflater.inflate(layoutResID, null)

        val rootContainer = FrameLayout(this)
        rootContainer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Loading Screen
        loadingOverlay = FrameLayout(this)
        loadingOverlay.setBackgroundColor(Color.WHITE)
        loadingOverlay.isClickable = true

        val progressBar = ProgressBar(this)
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        loadingOverlay.addView(progressBar, params)

        // Add "Setting Language..." text
        val tvLoading = TextView(this)
        tvLoading.text = "Setting Language..."
        val textParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        textParams.gravity = Gravity.CENTER
        textParams.topMargin = 150
        loadingOverlay.addView(tvLoading, textParams)

        rootContainer.addView(userView)
        rootContainer.addView(loadingOverlay)

        super.setContentView(rootContainer)

        // Start Translation Logic
        checkAndTranslate(userView)
    }

    private fun checkAndTranslate(view: View) {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH)

        // If English, hide loader. If not, start translating.
        if (langCode == TranslateLanguage.ENGLISH) {
            loadingOverlay.visibility = View.GONE
        } else {
            loadingOverlay.visibility = View.VISIBLE
            // Calls TranslationHelper
            TranslationHelper.translateViewHierarchy(view, langCode!!) {
                // When done, hide the loader
                loadingOverlay.visibility = View.GONE
            }
        }
    }
}