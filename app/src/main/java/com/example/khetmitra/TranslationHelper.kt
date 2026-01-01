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

    // 1. MANUAL DICTIONARY (Updated with ALL Languages)
    // We use lowercase keys ("mon", "sun") to catch everything
    private val manualCorrections = mapOf(
        // MONDAY
        "mon" to mapOf(
            TranslateLanguage.HINDI to "सोम", TranslateLanguage.MARATHI to "सोम", TranslateLanguage.GUJARATI to "સોમ",
            TranslateLanguage.KANNADA to "ಸೋಮ", TranslateLanguage.TAMIL to "தி", TranslateLanguage.TELUGU to "సోమ", TranslateLanguage.BENGALI to "সোম"
        ),
        // TUESDAY
        "tue" to mapOf(
            TranslateLanguage.HINDI to "मंगल", TranslateLanguage.MARATHI to "मंगळ", TranslateLanguage.GUJARATI to "મંગળ",
            TranslateLanguage.KANNADA to "ಮಂಗಳ", TranslateLanguage.TAMIL to "செ", TranslateLanguage.TELUGU to "మంగళ", TranslateLanguage.BENGALI to "মঙ্গল"
        ),
        // WEDNESDAY
        "wed" to mapOf(
            TranslateLanguage.HINDI to "बुध", TranslateLanguage.MARATHI to "बुध", TranslateLanguage.GUJARATI to "બુધ",
            TranslateLanguage.KANNADA to "ಬುಧ", TranslateLanguage.TAMIL to "பு", TranslateLanguage.TELUGU to "బుధ", TranslateLanguage.BENGALI to "বুধ"
        ),
        // THURSDAY
        "thu" to mapOf(
            TranslateLanguage.HINDI to "गुरु", TranslateLanguage.MARATHI to "गुरु", TranslateLanguage.GUJARATI to "ગુરુ",
            TranslateLanguage.KANNADA to "ಗುರು", TranslateLanguage.TAMIL to "வி", TranslateLanguage.TELUGU to "గురు", TranslateLanguage.BENGALI to "বৃহ"
        ),
        // FRIDAY
        "fri" to mapOf(
            TranslateLanguage.HINDI to "शुक्र", TranslateLanguage.MARATHI to "शुक्र", TranslateLanguage.GUJARATI to "શુક્ર",
            TranslateLanguage.KANNADA to "ಶುಕ್ರ", TranslateLanguage.TAMIL to "வெ", TranslateLanguage.TELUGU to "శుక్ర", TranslateLanguage.BENGALI to "শুক্র"
        ),
        // SATURDAY
        "sat" to mapOf(
            TranslateLanguage.HINDI to "शनि", TranslateLanguage.MARATHI to "शनि", TranslateLanguage.GUJARATI to "શનિ",
            TranslateLanguage.KANNADA to "ಶನಿ", TranslateLanguage.TAMIL to "ச", TranslateLanguage.TELUGU to "శని", TranslateLanguage.BENGALI to "শনি"
        ),
        // SUNDAY (Fixes the "Sun" issue)
        "sun" to mapOf(
            TranslateLanguage.HINDI to "रवि", TranslateLanguage.MARATHI to "रवि", TranslateLanguage.GUJARATI to "રવિ",
            TranslateLanguage.KANNADA to "ಭಾನು", TranslateLanguage.TAMIL to "ஞா", TranslateLanguage.TELUGU to "ఆది", TranslateLanguage.BENGALI to "রবি"
        ),

        // WEATHER TERMS
        "sunny" to mapOf(TranslateLanguage.HINDI to "धूप", TranslateLanguage.MARATHI to "स्वच्छ आकाश", TranslateLanguage.GUJARATI to "તડકો"),
        "clear" to mapOf(TranslateLanguage.HINDI to "साफ", TranslateLanguage.MARATHI to "स्वच्छ", TranslateLanguage.GUJARATI to "ચોખ્ખું"),
        "rain" to mapOf(TranslateLanguage.HINDI to "बारिश", TranslateLanguage.MARATHI to "पाऊस", TranslateLanguage.GUJARATI to "વરસાદ")
    )

    // 2. DIGIT CONVERTER (The missing piece for 123 -> १२३)
    private fun convertDigits(text: String, langCode: String): String {
        return when (langCode) {
            TranslateLanguage.HINDI, TranslateLanguage.MARATHI ->
                text.map { if (it in '0'..'9') ('०' + (it - '0')) else it }.joinToString("")

            TranslateLanguage.GUJARATI ->
                text.map { if (it in '0'..'9') ('૦' + (it - '0')) else it }.joinToString("")

            TranslateLanguage.KANNADA ->
                text.map { if (it in '0'..'9') ('೦' + (it - '0')) else it }.joinToString("")

            TranslateLanguage.TELUGU ->
                text.map { if (it in '0'..'9') ('౦' + (it - '0')) else it }.joinToString("")

            TranslateLanguage.BENGALI ->
                text.map { if (it in '0'..'9') ('০' + (it - '0')) else it }.joinToString("")

            TranslateLanguage.TAMIL ->
                // Tamil digits exist (௦-௯), but standard numerals are often preferred in apps.
                // If you want strict Tamil digits, use this:
                text.map { if (it in '0'..'9') ('௦' + (it - '0')) else it }.joinToString("")

            else -> text // Keep English numbers for others
        }
    }

    private fun getAllTextViews(view: View, list: ArrayList<TextView>) {
        if (view is TextView) list.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                getAllTextViews(view.getChildAt(i), list)
            }
        }
    }

    // MAIN FUNCTION
    fun translateViewHierarchy(rootView: View, targetLang: String, onFinished: () -> Unit) {
        val allTextViews = ArrayList<TextView>()
        getAllTextViews(rootView, allTextViews)

        if (allTextViews.isEmpty()) {
            onFinished()
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                var completedCount = 0

                for (tv in allTextViews) {
                    if (tv.tag == null) tv.tag = tv.text.toString()
                    val rawText = tv.tag.toString()
                    val cleanText = rawText.trim().lowercase(Locale.getDefault())

                    if (cleanText.isEmpty()) {
                        completedCount++
                        if (completedCount == allTextViews.size) onFinished()
                        continue
                    }

                    // A. Check Dictionary First
                    val manualFix = manualCorrections[cleanText]?.get(targetLang)

                    if (manualFix != null) {
                        // Apply Manual Fix + Convert Digits
                        tv.text = convertDigits(manualFix, targetLang)
                        completedCount++
                        if (completedCount == allTextViews.size) onFinished()
                    } else {
                        // B. Use ML Kit Fallback
                        translator.translate(rawText)
                            .addOnSuccessListener { translated ->
                                // Apply Digit Conversion to the translated text
                                tv.text = convertDigits(translated, targetLang)
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
                onFinished()
            }
    }
}