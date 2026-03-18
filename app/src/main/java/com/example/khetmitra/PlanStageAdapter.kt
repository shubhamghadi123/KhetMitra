package com.example.khetmitra

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlanStageAdapter(
    private val stages: List<FarmPlanStage>,
    private val onStageClick: (FarmPlanStage) -> Unit
) : RecyclerView.Adapter<PlanStageAdapter.StageViewHolder>() {

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

        holder.tvStageTitle.text = stage.stageTitle
        holder.tvStageSub.text = "⏱️ Duration: ${stage.durationText}  •  💪 Effort: ${stage.effortPercent}%"
        holder.itemView.setOnClickListener {
            onStageClick(stage)
        }
    }

    override fun getItemCount(): Int = stages.size
}