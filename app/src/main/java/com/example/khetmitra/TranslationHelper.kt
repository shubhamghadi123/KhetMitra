package com.example.khetmitra

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

object TranslationHelper {

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
            .addOnFailureListener {
                onFinished() // If download fails, just finish
            }
    }
}