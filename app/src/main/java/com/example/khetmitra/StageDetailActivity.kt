package com.example.khetmitra

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.example.khetmitra.TranslationHelper.convertDigits
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.mlkit.nl.translate.TranslateLanguage

class StageDetailActivity : AppCompatActivity() {

    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = convertDigits(num.toString(), langCode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage_detail)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }

        val cardHeaderContainer = findViewById<MaterialCardView>(R.id.cardHeaderContainer)
        val cardDetailIcon = findViewById<MaterialCardView>(R.id.cardDetailIcon)
        val ivDetailIcon = findViewById<ImageView>(R.id.ivDetailIcon)
        val tvDetailTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailSteps = findViewById<TextView>(R.id.tvDetailSteps)
        val tvDetailDuration = findViewById<TextView>(R.id.tvDetailDuration)
        val tvDetailEffort = findViewById<TextView>(R.id.tvDetailEffort)
        val tvDetailCriticality = findViewById<TextView>(R.id.tvDetailCriticality)

        (tvDetailEffort.parent as? View)?.visibility = View.GONE
        (tvDetailCriticality.parent as? View)?.visibility = View.GONE

        val viewDetailGlow = findViewById<View>(R.id.viewDetailGlow)

        val stageJson = intent.getStringExtra("STAGE_JSON")

        if (!stageJson.isNullOrEmpty()) {
            try {
                val stage = Gson().fromJson(stageJson, FarmPlanStage::class.java)

                applyUIStyling(stage)

                val baseColor = stage.cardColor
                val darkColor = ColorUtils.blendARGB(baseColor, android.graphics.Color.BLACK, 0.45f)
                val lightColor = ColorUtils.blendARGB(baseColor, android.graphics.Color.WHITE, 0.85f)

                cardHeaderContainer.setCardBackgroundColor(baseColor)
                viewDetailGlow.background?.mutate()?.setTint(baseColor)

                ivDetailIcon.setImageResource(if (stage.iconRes != 0) stage.iconRes else android.R.drawable.ic_menu_info_details)
                ivDetailIcon.setColorFilter(darkColor)
                cardDetailIcon.setCardBackgroundColor(lightColor)
                cardDetailIcon.strokeWidth = 0

                val formattedSteps = stage.steps.joinToString(separator = "\n\n") {
                    "• ${convertDigits(it, langCode)}"
                }
                val finalTitle = convertDigits(stage.stageTitle, langCode)
                val localDaysValue = d(stage.durationInDays)
                val localDaysLabel = t("days")
                val pillDuration = "⏱️ ${t("Duration")}: $localDaysValue $localDaysLabel"

                tvDetailTitle.text = finalTitle
                tvDetailSteps.text = formattedSteps

                setupPill(tvDetailDuration, pillDuration, lightColor, darkColor)

                tvDetailTitle.tag = "skip_translation"
                tvDetailDuration.tag = "skip_translation"

                if (langCode != TranslateLanguage.ENGLISH) {
                    window.decorView.post {
                        TranslationHelper.translateViewHierarchy(window.decorView.rootView, langCode) {
                            tvDetailTitle.text = finalTitle
                            tvDetailSteps.text = formattedSteps
                            tvDetailDuration.text = pillDuration
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
            1 -> { // Soil Prep
                stage.iconRes = R.drawable.ic_tractor
                stage.cardColor = "#D28F6B".toColorInt()
            }
            2 -> { // Sowing/Planting
                stage.iconRes = R.drawable.ic_seeds
                stage.cardColor = "#74A582".toColorInt()
            }
            3 -> { // Watering/Maintenance
                stage.iconRes = R.drawable.round_water_drop_24
                stage.cardColor = "#6FA7C7".toColorInt()
            }
            4 -> { // Fertilizing
                stage.iconRes = R.drawable.ic_fertilizer
                stage.cardColor = "#74A582".toColorInt()
            }
            5 -> { // Pest Control
                stage.iconRes = R.drawable.round_bug_report_24
                stage.cardColor = "#E57373".toColorInt()
            }
            else -> { // Harvesting/Storage
                stage.iconRes = R.drawable.ic_harvest
                stage.cardColor = "#DDA255".toColorInt()
            }
        }
    }
}