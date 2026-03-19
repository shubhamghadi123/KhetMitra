package com.example.khetmitra

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.mlkit.nl.translate.TranslateLanguage

class ViewPlanActivity : AppCompatActivity() {
    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_plan)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val tvPlanHeader = findViewById<TextView>(R.id.tvPlanHeader)
        val rvSavedPlanStages = findViewById<RecyclerView>(R.id.rvSavedPlanStages)
        rvSavedPlanStages.layoutManager = LinearLayoutManager(this)

        val planJson = intent.getStringExtra("PLAN_JSON")
        val farmName = intent.getStringExtra("FARM_NAME") ?: ""
        val cropName = intent.getStringExtra("CROP_NAME") ?: ""

        val translatedFarm = farmName.replace("Farm", t("Farm")).replace("Field", t("Field"))
        val translatedCrop = t(cropName)
        val finalHeaderText = t("[CROP] Plan for [FARM]")
            .replace("[CROP]", translatedCrop)
            .replace("[FARM]", d(translatedFarm))

        var planStagesToLoad: List<FarmPlanStage>? = null
        if (!planJson.isNullOrEmpty()) {
            try {
                val planResponse = Gson().fromJson(planJson, FarmPlanResponse::class.java)
                planResponse.stages.forEach { applyUIStyling(it) }
                planStagesToLoad = planResponse.stages
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (langCode != TranslateLanguage.ENGLISH) {
            window.decorView.post {
                TranslationHelper.translateViewHierarchy(window.decorView.rootView, langCode) {
                    tvPlanHeader.text = finalHeaderText

                    planStagesToLoad?.let { stages ->
                        rvSavedPlanStages.adapter = PlanStageAdapter(stages, langCode) { clickedStage ->
                            val intent = android.content.Intent(this@ViewPlanActivity, StageDetailActivity::class.java)
                            intent.putExtra("STAGE_JSON", Gson().toJson(clickedStage))
                            startActivity(intent)
                        }
                    }
                }
            }
        } else {
            tvPlanHeader.text = finalHeaderText
            planStagesToLoad?.let { stages ->
                rvSavedPlanStages.adapter = PlanStageAdapter(stages, langCode) { clickedStage ->
                    val intent = android.content.Intent(this@ViewPlanActivity, StageDetailActivity::class.java)
                    intent.putExtra("STAGE_JSON", Gson().toJson(clickedStage))
                    startActivity(intent)
                }
            }
        }
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