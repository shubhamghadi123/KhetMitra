package com.example.khetmitra

import android.graphics.Color
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

/**
 * Call this in your insights adapter's onBindViewHolder to style
 * the tag chip dynamically based on the insight type/tag string.
 *
 * Usage:
 *   InsightTagHelper.apply(holder.chipTag, holder.chipCard, item.tag)
 */
object InsightTagHelper {

    data class TagStyle(
        val emoji: String,
        val label: String,
        val textColor: Int,
        val bgColor: Int,
        val strokeColor: Int
    )

    private val tagStyles = mapOf(
        // Weather conditions
        "rain"        to TagStyle("🌧️", "Rain",        Color.parseColor("#0EA5E9"), Color.parseColor("#F0F9FF"), Color.parseColor("#330EA5E9")),
        "sunny"       to TagStyle("☀️", "Sunny",       Color.parseColor("#F97316"), Color.parseColor("#FFF7ED"), Color.parseColor("#33F97316")),
        "clear"       to TagStyle("🌤️", "Clear",       Color.parseColor("#F97316"), Color.parseColor("#FFF7ED"), Color.parseColor("#33F97316")),
        "cloudy"      to TagStyle("☁️", "Cloudy",      Color.parseColor("#6B7280"), Color.parseColor("#F3F4F6"), Color.parseColor("#336B7280")),
        "storm"       to TagStyle("⛈️", "Storm",       Color.parseColor("#7C3AED"), Color.parseColor("#F5F3FF"), Color.parseColor("#337C3AED")),
        "thunderstorm" to TagStyle("⛈️", "Storm",       Color.parseColor("#7C3AED"), Color.parseColor("#F5F3FF"), Color.parseColor("#337C3AED")),
        "fog"         to TagStyle("🌫️", "Fog",         Color.parseColor("#9CA3AF"), Color.parseColor("#F9FAFB"), Color.parseColor("#339CA3AF")),
        "snow"        to TagStyle("❄️", "Snow",        Color.parseColor("#3B82F6"), Color.parseColor("#EFF6FF"), Color.parseColor("#333B82F6")),
        "wind"        to TagStyle("💨", "Windy",       Color.parseColor("#64748B"), Color.parseColor("#F8FAFC"), Color.parseColor("#3364748B")),
        "hail"        to TagStyle("🌨️", "Hail",        Color.parseColor("#3B82F6"), Color.parseColor("#EFF6FF"), Color.parseColor("#333B82F6")),
        "drizzle"     to TagStyle("🌦️", "Drizzle",    Color.parseColor("#0EA5E9"), Color.parseColor("#F0F9FF"), Color.parseColor("#330EA5E9")),

        // Farming actions
        "irrigation"  to TagStyle("💧", "Irrigation",  Color.parseColor("#0EA5E9"), Color.parseColor("#F0F9FF"), Color.parseColor("#330EA5E9")),
        "harvest"     to TagStyle("🌾", "Harvest",     Color.parseColor("#EAB308"), Color.parseColor("#FEFCE8"), Color.parseColor("#33EAB308")),
        "fertilizer"  to TagStyle("🧪", "Fertilizer",  Color.parseColor("#22C55E"), Color.parseColor("#F0FDF4"), Color.parseColor("#3322C55E")),
        "pest"        to TagStyle("🐛", "Pest Alert",  Color.parseColor("#EF4444"), Color.parseColor("#FEF2F2"), Color.parseColor("#33EF4444")),
        "disease"     to TagStyle("🦠", "Disease",     Color.parseColor("#EF4444"), Color.parseColor("#FEF2F2"), Color.parseColor("#33EF4444")),
        "planting"    to TagStyle("🌱", "Planting",    Color.parseColor("#22C55E"), Color.parseColor("#F0FDF4"), Color.parseColor("#3322C55E")),
        "soil"        to TagStyle("🌍", "Soil",        Color.parseColor("#92400E"), Color.parseColor("#FFFBEB"), Color.parseColor("#3392400E")),
        "market"      to TagStyle("📈", "Market",      Color.parseColor("#A855F7"), Color.parseColor("#FAF5FF"), Color.parseColor("#33A855F7")),
        "advisory"    to TagStyle("📋", "Advisory",    Color.parseColor("#1A3C2E"), Color.parseColor("#F0FDF4"), Color.parseColor("#331A3C2E")),
        "alert"       to TagStyle("⚠️", "Alert",       Color.parseColor("#EF4444"), Color.parseColor("#FEF2F2"), Color.parseColor("#33EF4444")),
        "tip"         to TagStyle("💡", "Tip",         Color.parseColor("#EAB308"), Color.parseColor("#FEFCE8"), Color.parseColor("#33EAB308")),
    )

    // Fallback style
    private val defaultStyle = TagStyle("🌿", "Info", Color.parseColor("#22C55E"), Color.parseColor("#F0FDF4"), Color.parseColor("#3322C55E"))

    /**
     * Resolves the best-matching style for the given tag string.
     * Tries exact match first, then substring match.
     */
    fun resolve(tag: String?): TagStyle {
        if (tag.isNullOrBlank()) return defaultStyle
        val key = tag.lowercase().trim()
        // exact match
        tagStyles[key]?.let { return it }
        // substring match — pick first that contains the key
        tagStyles.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.let { return it.value }
        return defaultStyle
    }

    /**
     * Applies the resolved style directly to the chip TextView and its card container.
     *
     * @param tvTag       The TextView showing the emoji + label (tvInsightTag)
     * @param cardTag     The MaterialCardView wrapping tvInsightTag
     * @param rawTag      The raw tag string from your data model (e.g. "rain", "Harvest", "pest alert")
     */
    fun apply(tvTag: TextView, cardTag: MaterialCardView, rawTag: String?) {
        val style = resolve(rawTag)
        tvTag.text = "${style.emoji} ${style.label}"
        tvTag.setTextColor(style.textColor)
        cardTag.setCardBackgroundColor(style.bgColor)
        cardTag.strokeColor = style.strokeColor
    }
}