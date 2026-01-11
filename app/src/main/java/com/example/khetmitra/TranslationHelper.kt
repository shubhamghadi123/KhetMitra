package com.example.khetmitra

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import okio.IOException
import java.util.Locale

object TranslationHelper {

    // 1. Variable to hold the loaded data (Empty by default)
    private var loadedCorrections: Map<String, Map<String, String>> = emptyMap()

    // 2. INITIALIZE: Call this once in your MainActivity onCreate()
    fun initTranslations(context: Context) {
        try {
            // Reads "manual_corrections.json" from the assets folder
            val jsonString = context.assets.open("manual_corrections.json").bufferedReader().use { it.readText() }

            // Defines the type: Map<String, Map<String, String>>
            val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type

            // Parses JSON into the map
            loadedCorrections = Gson().fromJson(jsonString, type)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // 3. LOOKUP: Checks the loaded JSON map for translations
    fun getManualTranslation(text: String, langCode: String): String? {
        val cleanText = text.trim()
        // Try exact match or lowercase match
        return loadedCorrections[cleanText]?.get(langCode)
            ?: loadedCorrections[cleanText.lowercase(Locale.getDefault())]?.get(langCode)
    }

    // DIGIT CONVERTER (The missing piece for 123 -> १२३)
    fun convertDigits(text: String, langCode: String): String {
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
                text.map { if (it in '0'..'9') ('௦' + (it - '0')) else it }.joinToString("")
            else -> text
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

                    // Check Dictionary First
                    val manualFix = getManualTranslation(cleanText, targetLang)

                    if (manualFix != null) {
                        // Apply Manual Fix + Convert Digits
                        tv.text = convertDigits(manualFix, targetLang)
                        completedCount++
                        if (completedCount == allTextViews.size) onFinished()
                    } else {
                        // Use ML Kit Fallback
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