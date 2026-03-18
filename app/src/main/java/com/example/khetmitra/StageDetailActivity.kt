package com.example.khetmitra

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson

class StageDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage_detail)

        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val layoutDetailHeader = findViewById<LinearLayout>(R.id.layoutDetailHeader)
        val ivDetailIcon = findViewById<ImageView>(R.id.ivDetailIcon)
        val tvDetailTitle = findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailSteps = findViewById<TextView>(R.id.tvDetailSteps)

        val stageJson = intent.getStringExtra("STAGE_JSON")

        if (!stageJson.isNullOrEmpty()) {
            try {
                val stage = Gson().fromJson(stageJson, FarmPlanStage::class.java)
                applyUIStyling(stage)
                layoutDetailHeader.setBackgroundColor(stage.cardColor)
                ivDetailIcon.setImageResource(if (stage.iconRes != 0) stage.iconRes else android.R.drawable.ic_menu_info_details)
                tvDetailTitle.text = stage.stageTitle
                tvDetailSteps.text = stage.steps.joinToString(separator = "\n\n") { "• $it" }

            } catch (e: Exception) {
                e.printStackTrace()
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