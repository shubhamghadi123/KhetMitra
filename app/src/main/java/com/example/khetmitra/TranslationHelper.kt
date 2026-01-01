package com.example.khetmitra

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

object TranslationHelper {

    private val manualCorrections = mapOf(
        // Days
        "mon" to mapOf(TranslateLanguage.HINDI to "सोम", TranslateLanguage.MARATHI to "सोम", TranslateLanguage.GUJARATI to "સોમ"),
        "tue" to mapOf(TranslateLanguage.HINDI to "मंगल", TranslateLanguage.MARATHI to "मंगळ", TranslateLanguage.GUJARATI to "મંગળ"),
        "wed" to mapOf(TranslateLanguage.HINDI to "बुध", TranslateLanguage.MARATHI to "बुध", TranslateLanguage.GUJARATI to "બુધ"),
        "thu" to mapOf(TranslateLanguage.HINDI to "गुरु", TranslateLanguage.MARATHI to "गुरु", TranslateLanguage.GUJARATI to "ગુરુ"),
        "fri" to mapOf(TranslateLanguage.HINDI to "शुक्र", TranslateLanguage.MARATHI to "शुक्र", TranslateLanguage.GUJARATI to "શુક્ર"),
        "sat" to mapOf(TranslateLanguage.HINDI to "शनि", TranslateLanguage.MARATHI to "शनि", TranslateLanguage.GUJARATI to "શનિ"),
        "sun" to mapOf(TranslateLanguage.HINDI to "रवि", TranslateLanguage.MARATHI to "रवि", TranslateLanguage.GUJARATI to "રવિ"),

        // Weather
        "sunny" to mapOf(TranslateLanguage.HINDI to "धूप", TranslateLanguage.MARATHI to "स्वच्छ आकाश"),
        "clear" to mapOf(TranslateLanguage.HINDI to "साफ", TranslateLanguage.MARATHI to "स्वच्छ"),
        "rain" to mapOf(TranslateLanguage.HINDI to "बारिश", TranslateLanguage.MARATHI to "पाऊस")
    )

    private fun getAllTextViews(view: View, list: ArrayList<TextView>) {
        if (view is TextView) list.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                getAllTextViews(view.getChildAt(i), list)
            }
        }
    }

    // Main Function
    fun translateViewHierarchy(rootView: View, targetLang: String, onFinished: () -> Unit) {
        val allTextViews = ArrayList<TextView>()
        getAllTextViews(rootView, allTextViews)

        if (allTextViews.isEmpty()) {
            onFinished()
            return
        }

        // Translator Setup
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)

        // Download Model & Translate
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                var completedCount = 0

                for (tv in allTextViews) {
                    if (tv.tag == null) tv.tag = tv.text.toString()
                    val originalText = tv.tag.toString()

                    if (originalText.trim().isEmpty()) {
                        completedCount++
                        if (completedCount == allTextViews.size) onFinished()
                        continue
                    }

                    val lowerCaseText = originalText.lowercase(Locale.getDefault())
                    val manualFix = manualCorrections[lowerCaseText]?.get(targetLang)

                    if (manualFix != null) {
                        tv.text = manualFix
                        completedCount++
                        if (completedCount == allTextViews.size) onFinished()
                    } else {
                        // 3. Fallback to ML Kit
                        translator.translate(originalText)
                            .addOnSuccessListener { translated ->
                                tv.text = translated
                                completedCount++
                                if (completedCount == allTextViews.size) onFinished()
                            }
                            .addOnFailureListener {
                                completedCount++
                                if (completedCount == allTextViews.size) onFinished()
                            }
                    }
                }
            }
            .addOnFailureListener {
                onFinished() // If download fails, just finish
            }
    }
}