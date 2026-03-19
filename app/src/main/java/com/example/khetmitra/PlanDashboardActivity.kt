package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlanDashboardActivity : AppCompatActivity() {
    private lateinit var rvExistingPlans: RecyclerView
    private lateinit var cardEmptyState: LinearLayout
    private lateinit var btnHeaderDelete: MaterialCardView
    private lateinit var ivDeletePlanIcon: ImageView
    private var langCode: String = TranslateLanguage.ENGLISH
    private val plansList = mutableListOf<SavedFarmPlan>()
    private var adapter: SavedPlanAdapter? = null

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_farm_plan_dashboard)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }

        rvExistingPlans = findViewById(R.id.rvExistingPlans)
        cardEmptyState = findViewById(R.id.cardEmptyState)
        rvExistingPlans.layoutManager = LinearLayoutManager(this)

        findViewById<MaterialCardView>(R.id.btnCreateNewPlan).setOnClickListener {
            startActivity(Intent(this, CreatePlanActivity::class.java))
        }

        btnHeaderDelete  = findViewById(R.id.btnHeaderDelete)
        ivDeletePlanIcon = btnHeaderDelete.findViewById(R.id.ivDeletePlanIcon)

        btnHeaderDelete.setOnClickListener {
            val isNowDeleteMode = adapter?.toggleDeleteMode() ?: false

            ivDeletePlanIcon.setImageResource(
                if (isNowDeleteMode) R.drawable.round_check_24
                else R.drawable.round_delete_24
            )
            btnHeaderDelete.setCardBackgroundColor(
                if (isNowDeleteMode) "#B71C1C".toColorInt() else "#2D6A4F".toColorInt()
            )
            btnHeaderDelete.strokeColor = if (isNowDeleteMode) "#820E0E".toColorInt() else "#52B788".toColorInt()
        }

        if (langCode != TranslateLanguage.ENGLISH) {
            window.decorView.post {
                TranslationHelper.translateViewHierarchy(window.decorView.rootView, langCode) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSavedPlans()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadSavedPlans() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull() ?: return@launch
                val fetchedPlans = SupabaseManager.client.postgrest["farm_plans"]
                    .select {
                        filter { eq("farmer_id", user.id) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<SavedFarmPlan>()

                withContext(Dispatchers.Main) {
                    plansList.clear()
                    plansList.addAll(fetchedPlans)

                    if (plansList.isEmpty()) {
                        cardEmptyState.visibility = View.VISIBLE
                        rvExistingPlans.visibility = View.GONE
                    } else {
                        cardEmptyState.visibility = View.GONE
                        rvExistingPlans.visibility = View.VISIBLE

                        if (adapter == null) {
                            adapter = SavedPlanAdapter(plansList,
                                onClick = { clickedPlan ->
                                    val intent = Intent(this@PlanDashboardActivity, ViewPlanActivity::class.java)
                                    intent.putExtra("PLAN_JSON", clickedPlan.plan_json)
                                    intent.putExtra("FARM_NAME", clickedPlan.farm_name)
                                    intent.putExtra("CROP_NAME", clickedPlan.crop_name)
                                    startActivity(intent)
                                },
                                onDeleteClick = { planToDelete, position ->
                                    showDeleteConfirmationDialog(planToDelete, position)
                                }
                            )
                            rvExistingPlans.adapter = adapter
                        } else {
                            adapter?.notifyDataSetChanged()
                        }
                        if (langCode != TranslateLanguage.ENGLISH) {
                            rvExistingPlans.post {
                                TranslationHelper.translateViewHierarchy(rvExistingPlans, langCode) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlanDashboardActivity, t("Failed to load plans"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(plan: SavedFarmPlan, position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(t("Delete Plan"))
            .setMessage(t("Are you sure you want to delete this farm plan?"))
            .setPositiveButton(t("Delete")) { dialog, _ ->
                if (plan.id != null) deletePlanFromDatabase(plan.id.toString(), position)
                dialog.dismiss()
            }
            .setNegativeButton(t("Cancel")) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deletePlanFromDatabase(planId: String, position: Int) {
        Toast.makeText(this, t("Deleting plan..."), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val deletedRows = SupabaseManager.client.postgrest["farm_plans"].delete {
                    select()
                    filter { eq("id", planId) }
                }.decodeList<SavedFarmPlan>()

                withContext(Dispatchers.Main) {
                    if (deletedRows.isNotEmpty()) {
                        plansList.removeAt(position)
                        adapter?.notifyItemRemoved(position)
                        adapter?.notifyItemRangeChanged(position, plansList.size)

                        if (plansList.isEmpty()) {
                            cardEmptyState.visibility = View.VISIBLE
                            rvExistingPlans.visibility = View.GONE
                        }
                        Toast.makeText(this@PlanDashboardActivity, t("Plan deleted"), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@PlanDashboardActivity, t("Failed: Database blocked deletion. Check RLS policies."), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlanDashboardActivity, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}