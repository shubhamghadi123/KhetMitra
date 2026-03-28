package com.example.khetmitra

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ViewPlanActivity : AppCompatActivity() {
    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    private suspend fun translateDynamicText(text: String): String =
        suspendCancellableCoroutine { continuation ->
            if (langCode == TranslateLanguage.ENGLISH || text.isBlank()) {
                continuation.resumeWith(Result.success(text))
                return@suspendCancellableCoroutine
            }
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(langCode)
                .build()
            val client = Translation.getClient(options)

            client.downloadModelIfNeeded().addOnSuccessListener {
                client.translate(text)
                    .addOnSuccessListener { result -> continuation.resumeWith(Result.success(result)) }
                    .addOnFailureListener { continuation.resumeWith(Result.success(text)) }
            }.addOnFailureListener {
                continuation.resumeWith(Result.success(text))
            }
        }

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

        lifecycleScope.launch(Dispatchers.IO) {

            val translatedFarm = if (farmName.matches(Regex("Farm \\d+"))) {
                "${t("Farm")} ${d(farmName.substringAfter("Farm "))}"
            } else {
                translateDynamicText(farmName)
            }

            val translatedCrop = t(cropName)
            val finalHeaderText = t("[CROP] Plan for [FARM]")
                .replace("[CROP]", translatedCrop)
                .replace("[FARM]", d(translatedFarm))

            withContext(Dispatchers.Main) {
                if (langCode != TranslateLanguage.ENGLISH) {
                    window.decorView.post {
                        TranslationHelper.translateViewHierarchy(window.decorView.rootView, langCode) {
                            tvPlanHeader.text = finalHeaderText

                            planStagesToLoad?.let { stages ->
                                rvSavedPlanStages.adapter = PlanStageAdapter(stages, langCode) { clickedStage ->
                                    val intent = android.content.Intent(this@ViewPlanActivity, StageDetailActivity::class.java)
                                    intent.putExtra("STAGE_NUMBER", clickedStage.stageNumber)
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
                            intent.putExtra("STAGE_NUMBER", clickedStage.stageNumber)
                            intent.putExtra("STAGE_JSON", Gson().toJson(clickedStage))
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

    private fun applyUIStyling(stage: FarmPlanStage) {
        when (stage.stageNumber) {
            1 -> { stage.iconRes = R.drawable.ic_tractor; stage.cardColor = "#D28F6B".toColorInt() }
            2 -> { stage.iconRes = R.drawable.ic_seeds; stage.cardColor = "#74A582".toColorInt() }
            3 -> { stage.iconRes = R.drawable.round_water_drop_24; stage.cardColor = "#6FA7C7".toColorInt() }
            4 -> { stage.iconRes = R.drawable.ic_fertilizer; stage.cardColor = "#74A582".toColorInt() }
            5 -> { stage.iconRes = R.drawable.round_bug_report_24; stage.cardColor = "#E57373".toColorInt() }
            else -> { stage.iconRes = R.drawable.ic_harvest; stage.cardColor = "#DDA255".toColorInt() }
        }
    }
}