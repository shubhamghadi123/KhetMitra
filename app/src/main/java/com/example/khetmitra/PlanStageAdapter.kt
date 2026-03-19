package com.example.khetmitra

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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

    class StageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivStageIcon: ImageView = view.findViewById(R.id.ivStageIcon)
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
        holder.ivStageIcon.setImageResource(if (stage.iconRes != 0) stage.iconRes else android.R.drawable.ic_menu_info_details)
        if (stage.cardColor != 0) {
            holder.ivStageIcon.setColorFilter(stage.cardColor)
        }

        val safeTitles = arrayOf(
            stage.stageTitle,
            t("1. Soil Preparation"),
            t("2. Sowing & Planting"),
            t("3. Crop Maintenance"),
            t("4. Fertilizing & Irrigation"),
            t("5. Harvesting")
        )
        holder.tvStageTitle.text = if (stage.stageNumber in 1..5) safeTitles[stage.stageNumber] else stage.stageTitle

        val durationLabel = t("Duration")
        val effortLabel = t("Effort")
        val translatedDuration = stage.durationText.replace("Days", t("days")).replace("Day", t("days"))

        holder.tvStageSub.text = "⏱️ $durationLabel: ${d(translatedDuration)}  •  💪 $effortLabel: ${d(stage.effortPercent)}%"

        holder.itemView.setOnClickListener {
            onStageClick(stage)
        }
    }

    override fun getItemCount(): Int = stages.size
}