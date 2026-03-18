package com.example.khetmitra

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView

class SavedPlanAdapter(
    private val plans: List<SavedFarmPlan>,
    private val onClick: (SavedFarmPlan) -> Unit,
    private val onDeleteClick: (SavedFarmPlan, Int) -> Unit
) : RecyclerView.Adapter<SavedPlanAdapter.PlanViewHolder>() {
    private var isDeleteMode = false

    @SuppressLint("NotifyDataSetChanged")
    fun toggleDeleteMode(): Boolean {
        isDeleteMode = !isDeleteMode
        notifyDataSetChanged()
        return isDeleteMode
    }

    class PlanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFarmName: TextView = view.findViewById(R.id.tvFarmName)
        val tvCropName: TextView = view.findViewById(R.id.tvCropName)
        val ivActionIcon: ImageView = view.findViewById(R.id.ivActionIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_plan, parent, false)
        return PlanViewHolder(view)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val plan = plans[position]
        holder.tvFarmName.text = plan.farm_name
        holder.tvCropName.text = "Crop: ${plan.crop_name}"

        if (isDeleteMode) {
            holder.ivActionIcon.setImageResource(R.drawable.round_delete_24)
            holder.ivActionIcon.setColorFilter("#B71C1C".toColorInt())

            holder.itemView.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onDeleteClick(plan, currentPos)
                }
            }
        } else {
            holder.ivActionIcon.setImageResource(R.drawable.round_arrow_forward_ios_24)
            holder.ivActionIcon.clearColorFilter()

            holder.itemView.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onClick(plan)
                }
            }
        }
    }

    override fun getItemCount(): Int = plans.size
}