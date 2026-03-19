package com.example.khetmitra

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.mlkit.nl.translate.TranslateLanguage

class StageDetailActivity : AppCompatActivity() {

    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage_detail)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }

        val layoutDetailHeader = findViewById<LinearLayout>(R.id.layoutDetailHeader)
        val ivDetailIcon = findViewById<ImageView>(R.id.ivDetailIcon)
        val tvDetailTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailSteps = findViewById<TextView>(R.id.tvDetailSteps)

        val tvDetailDuration = findViewById<TextView>(R.id.tvDetailDuration)
        val tvDetailEffort = findViewById<TextView>(R.id.tvDetailEffort)
        val tvDetailCriticality = findViewById<TextView>(R.id.tvDetailCriticality)

        val stageJson = intent.getStringExtra("STAGE_JSON")

        if (!stageJson.isNullOrEmpty()) {
            try {
                val stage = Gson().fromJson(stageJson, FarmPlanStage::class.java)
                applyUIStyling(stage)

                val baseColor = stage.cardColor
                val darkColor = ColorUtils.blendARGB(baseColor, android.graphics.Color.BLACK, 0.45f)
                val lightColor = ColorUtils.blendARGB(baseColor, android.graphics.Color.WHITE, 0.92f)

                layoutDetailHeader.setBackgroundColor(baseColor)
                ivDetailIcon.setImageResource(if (stage.iconRes != 0) stage.iconRes else android.R.drawable.ic_menu_info_details)
                ivDetailIcon.setColorFilter(darkColor)

                val formattedSteps = stage.steps.joinToString(separator = "\n\n") { "• $it" }

                val safeTitles = arrayOf(
                    stage.stageTitle,
                    t("1. Soil Preparation"),
                    t("2. Sowing & Planting"),
                    t("3. Crop Maintenance"),
                    t("4. Fertilizing & Irrigation"),
                    t("5. Harvesting")
                )
                val finalTitle = if (stage.stageNumber in 1..5) safeTitles[stage.stageNumber] else stage.stageTitle

                val durationText = stage.durationText.replace("Days", t("days")).replace("Day", t("days"))
                val pillDuration = "⏱️ ${t("Duration")}: ${d(durationText)}"
                val pillEffort   = "💪 ${t("Effort")}: ${d(stage.effortPercent)}%"
                val pillRisk     = "⚠️ ${t("Risk")}: ${if (stage.criticality > 70) t("High") else t("Normal")}"

                tvDetailTitle.text = finalTitle
                tvDetailSteps.text = formattedSteps
                setupPill(tvDetailDuration, pillDuration, lightColor, darkColor)
                setupPill(tvDetailEffort, pillEffort, lightColor, darkColor)
                setupPill(tvDetailCriticality, pillRisk, lightColor, darkColor)

                if (langCode != TranslateLanguage.ENGLISH) {
                    window.decorView.post {
                        TranslationHelper.translateViewHierarchy(window.decorView.rootView, langCode) {
                            tvDetailTitle.text = finalTitle
                            tvDetailSteps.text = formattedSteps
                            tvDetailDuration.text = pillDuration
                            tvDetailEffort.text = pillEffort
                            tvDetailCriticality.text = pillRisk
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupPill(textView: TextView, text: String, bgColor: Int, textColor: Int) {
        textView.text = text
        textView.setTextColor(textColor)
        val card = textView.parent as? MaterialCardView
        card?.setCardBackgroundColor(bgColor)
        card?.strokeWidth = 0
        card?.cardElevation = 0f
    }

    private fun applyUIStyling(stage: FarmPlanStage) {
        when (stage.stageNumber) {
            1 -> { stage.iconRes = R.drawable.ic_tractor; stage.cardColor = "#D28F6B".toColorInt() }
            2 -> { stage.iconRes = R.drawable.ic_seeds; stage.cardColor = "#74A582".toColorInt() }
            3 -> { stage.iconRes = R.drawable.ic_tools; stage.cardColor = "#6FA7C7".toColorInt() }
            4 -> { stage.iconRes = R.drawable.ic_fertilizer; stage.cardColor = "#74A582".toColorInt() }
            5 -> { stage.iconRes = R.drawable.ic_harvest; stage.cardColor = "#DDA255".toColorInt() }
            else -> { stage.iconRes = android.R.drawable.ic_menu_info_details; stage.cardColor = "#999999".toColorInt() }
        }
    }
}