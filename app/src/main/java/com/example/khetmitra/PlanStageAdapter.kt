package com.example.khetmitra

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt // 👉 Make sure this is imported!
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.nl.translate.TranslateLanguage

class PlanStageAdapter(
    private val stages: List<FarmPlanStage>,
    private var langCode: String = TranslateLanguage.ENGLISH,
    private val onStageClick: (FarmPlanStage) -> Unit
) : RecyclerView.Adapter<PlanStageAdapter.StageViewHolder>() {

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    private fun getStyleForStage(stageNumber: Int): Pair<Int, Int> {
        return when (stageNumber) {
            1 -> Pair(R.drawable.ic_tractor, "#D28F6B".toColorInt()) // Soil Prep (Brown)
            2 -> Pair(R.drawable.ic_seeds, "#74A582".toColorInt())   // Sowing (Green)
            3 -> Pair(R.drawable.round_water_drop_24, "#6FA7C7".toColorInt()) // Watering (Blue)
            4 -> Pair(R.drawable.ic_fertilizer, "#74A582".toColorInt()) // Fertilizer (Green)
            5 -> Pair(R.drawable.round_bug_report_24, "#E57373".toColorInt()) // Pests (Red)
            else -> Pair(R.drawable.ic_harvest, "#DDA255".toColorInt()) // Harvest/Storage (Gold)
        }
    }

    class StageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStageIcon: ImageView = view.findViewById(R.id.ivStageIcon)
        val cardIconBg: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.cardIconBg)
        val tvStageTitle: TextView = view.findViewById(R.id.tvStageTitle)
        val tvStageSub: TextView = view.findViewById(R.id.tvStageSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan_stage, parent, false)
        return StageViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: StageViewHolder, position: Int) {
        val stage = stages[position]
        val (iconRes, baseColor) = getStyleForStage(stage.stageNumber)
        val lightColor = androidx.core.graphics.ColorUtils.blendARGB(baseColor, android.graphics.Color.WHITE, 0.85f)

        holder.ivStageIcon.setImageResource(iconRes)
        holder.ivStageIcon.setColorFilter(baseColor)
        holder.cardIconBg.setCardBackgroundColor(lightColor)
        holder.tvStageTitle.text = stage.stageTitle

        val durationLabel = t("Duration")
        val localDaysValue = d(stage.durationInDays)
        val localDaysLabel = t("days")

        holder.tvStageSub.text = "⏱️ $durationLabel: $localDaysValue $localDaysLabel"
        holder.itemView.setOnClickListener {
            onStageClick(stage)
        }
    }

    override fun getItemCount(): Int = stages.size
}