package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage

object InsightTagHelper {
    private val tagStyles = mapOf(
        // Weather conditions
        "rain"        to TagStyle("🌧️", "Rain",
            "#0EA5E9".toColorInt(), "#F0F9FF".toColorInt(), "#330EA5E9".toColorInt()),
        "sunny"       to TagStyle("☀️", "Sunny",
            "#F97316".toColorInt(), "#FFF7ED".toColorInt(), "#33F97316".toColorInt()),
        "clear"       to TagStyle("🌤️", "Clear",
            "#F97316".toColorInt(), "#FFF7ED".toColorInt(), "#33F97316".toColorInt()),
        "cloudy"      to TagStyle("☁️", "Cloudy",
            "#6B7280".toColorInt(), "#F3F4F6".toColorInt(), "#336B7280".toColorInt()),
        "storm"       to TagStyle("⛈️", "Storm",
            "#7C3AED".toColorInt(), "#F5F3FF".toColorInt(), "#337C3AED".toColorInt()),
        "thunderstorm" to TagStyle("⛈️", "Storm",
            "#7C3AED".toColorInt(), "#F5F3FF".toColorInt(), "#337C3AED".toColorInt()),
        "fog"         to TagStyle("🌫️", "Fog",
            "#9CA3AF".toColorInt(), "#F9FAFB".toColorInt(), "#339CA3AF".toColorInt()),
        "snow"        to TagStyle("❄️", "Snow",
            "#3B82F6".toColorInt(), "#EFF6FF".toColorInt(), "#333B82F6".toColorInt()),
        "wind"        to TagStyle("💨", "Windy",
            "#64748B".toColorInt(), "#F8FAFC".toColorInt(), "#3364748B".toColorInt()),
        "hail"        to TagStyle("🌨️", "Hail",
            "#3B82F6".toColorInt(), "#EFF6FF".toColorInt(), "#333B82F6".toColorInt()),
        "drizzle"     to TagStyle("🌦️", "Drizzle",
            "#0EA5E9".toColorInt(), "#F0F9FF".toColorInt(), "#330EA5E9".toColorInt()),

        // Farming actions
        "irrigation"  to TagStyle("💧", "Irrigation",
            "#0EA5E9".toColorInt(), "#F0F9FF".toColorInt(), "#330EA5E9".toColorInt()),
        "harvest"     to TagStyle("🌾", "Harvest",
            "#EAB308".toColorInt(), "#FEFCE8".toColorInt(), "#33EAB308".toColorInt()),
        "fertilizer"  to TagStyle("🧪", "Fertilizer",
            "#22C55E".toColorInt(), "#F0FDF4".toColorInt(), "#3322C55E".toColorInt()),
        "pest"        to TagStyle("🐛", "Pest Alert",
            "#EF4444".toColorInt(), "#FEF2F2".toColorInt(), "#33EF4444".toColorInt()),
        "disease"     to TagStyle("🦠", "Disease",
            "#EF4444".toColorInt(), "#FEF2F2".toColorInt(), "#33EF4444".toColorInt()),
        "planting"    to TagStyle("🌱", "Planting",
            "#22C55E".toColorInt(), "#F0FDF4".toColorInt(), "#3322C55E".toColorInt()),
        "soil"        to TagStyle("🌍", "Soil",
            "#92400E".toColorInt(), "#FFFBEB".toColorInt(), "#3392400E".toColorInt()),
        "market"      to TagStyle("📈", "Market",
            "#A855F7".toColorInt(), "#FAF5FF".toColorInt(), "#33A855F7".toColorInt()),
        "advisory"    to TagStyle("📋", "Advisory",
            "#1A3C2E".toColorInt(), "#F0FDF4".toColorInt(), "#331A3C2E".toColorInt()),
        "alert"       to TagStyle("⚠️", "Alert",
            "#EF4444".toColorInt(), "#FEF2F2".toColorInt(), "#33EF4444".toColorInt()),
        "tip"         to TagStyle("💡", "Tip",
            "#EAB308".toColorInt(), "#FEFCE8".toColorInt(), "#33EAB308".toColorInt()),
    )

    private val defaultStyle = TagStyle("🌿", "Info",
        "#22C55E".toColorInt(), "#F0FDF4".toColorInt(), "#3322C55E".toColorInt())

    fun resolve(tag: String?): TagStyle {
        if (tag.isNullOrBlank()) return defaultStyle
        val key = tag.lowercase().trim()
        tagStyles[key]?.let { return it }
        tagStyles.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.let { return it.value }
        return defaultStyle
    }

    @SuppressLint("SetTextI18n")
    fun apply(tvTag: TextView, cardTag: MaterialCardView, rawTag: String?) {
        val style = resolve(rawTag)

        val prefs = tvTag.context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        val translatedLabel = if (langCode == TranslateLanguage.ENGLISH) {
            style.label
        } else {
            TranslationHelper.getManualTranslation(style.label, langCode) ?: style.label
        }

        tvTag.text = "${style.emoji} $translatedLabel"
        tvTag.setTextColor(style.textColor)
        cardTag.setCardBackgroundColor(style.bgColor)
        cardTag.strokeColor = style.strokeColor
    }
}