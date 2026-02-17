package com.example.khetmitra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.os.bundleOf
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SoilFallbackFragment : BottomSheetDialogFragment() {

    private var temporarySelectedSoil: String? = null
    private lateinit var manualSoilAdapter: SoilAdapter
    private lateinit var ribbonAdapter: RibbonAdapter

    private val soilList = listOf(
        SoilType(1, "Alluvial Soil", R.drawable.soil_alluvial, "#C2A278"),
        SoilType(2, "Black / Regur Soil", R.drawable.soil_black, "#1A1A1A"),
        SoilType(3, "Red & Yellow Soil", R.drawable.soil_red, "#9E231C"),
        SoilType(4, "Laterite Soil", R.drawable.soil_laterite, "#8B5E3C"),
        SoilType(5, "Arid / Desert Soil", R.drawable.soil_arid, "#E3B484"),
        SoilType(6, "Mountain / Forest Soil", R.drawable.soil_mountain, "#3E2B1A"),
        SoilType(7, "Saline & Alkaline Soil", R.drawable.soil_saline, "#A8A8A8"),
        SoilType(8, "Peaty & Marshy Soil", R.drawable.soil_peaty, "#2B1E18")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_soil_fallback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupManualGrid(view)
        setupRibbonRecyclerView(view)
        setupCropEstimation(view)

        view.findViewById<TextView>(R.id.tvHowToTest).setOnClickListener {
            showRibbonTestInstructions()
        }

        view.findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            if (temporarySelectedSoil != null) {
                parentFragmentManager.setFragmentResult("soil_request", bundleOf("selected_soil" to temporarySelectedSoil))
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Please select an option", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRibbonTestInstructions() {
        val customView = layoutInflater.inflate(R.layout.ribbon_instructions, null)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(customView)
            .setCancelable(true)
            .create()

        val btnGotIt = customView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGotIt)
        btnGotIt.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun setupRibbonRecyclerView(view: View) {
        val rvRibbon = view.findViewById<RecyclerView>(R.id.rvRibbonTest)
        val rvSoil = view.findViewById<RecyclerView>(R.id.rvFilteredSoils)
        val tvHeader2 = view.findViewById<TextView>(R.id.tvStep2Header)

        rvRibbon.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val ribbonOptions = listOf(
            RibbonData("Breaks Easily", "Sandy / Arid Desert Soil"),
            RibbonData("Short Ribbon", "Alluvial / Red / Laterite / Mountain Soil"),
            RibbonData("Long Ribbon", "Black / Peaty / Saline Soil")
        )

        ribbonAdapter = RibbonAdapter(ribbonOptions) { selected ->
            view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)?.apply {
                setText("", false)
                clearFocus()
            }
            temporarySelectedSoil = null

            tvHeader2?.visibility = View.VISIBLE
            rvSoil?.visibility = View.VISIBLE

            val filteredSoils = when(selected.result) {
                "Breaks Easily" -> soilList.filter { it.id == 5 }
                "Short Ribbon" -> soilList.filter { it.id in listOf(1, 3, 4, 6) }
                "Long Ribbon" -> soilList.filter { it.id in listOf(2, 7, 8) }
                else -> soilList
            }

            if (::manualSoilAdapter.isInitialized) {
                manualSoilAdapter.updateData(filteredSoils)
                view.findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)?.post {
                    view.findViewById<androidx.core.widget.NestedScrollView>(R.id.nestedScrollView)
                        .smoothScrollTo(0, tvHeader2?.top ?: 0)
                }
            }
        }
        rvRibbon.adapter = ribbonAdapter
    }

    private fun setupManualGrid(view: View) {
        val rvSoil = view.findViewById<RecyclerView>(R.id.rvFilteredSoils)
        if (rvSoil != null) {
            rvSoil.layoutManager = GridLayoutManager(requireContext(), 2)

            manualSoilAdapter = SoilAdapter(emptyList<SoilType>()) { selectedSoil ->
                temporarySelectedSoil = selectedSoil.nameEn
                view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)?.apply {
                    setText("", false)
                    clearFocus()
                }
            }
            rvSoil.adapter = manualSoilAdapter
        }
    }

    private fun setupCropEstimation(view: View) {
        val crops = arrayOf(
            "Rice", "Wheat",        // Alluvial
            "Cotton", "Soybean",     // Black
            "Pulses", "Groundnut",   // Red & Yellow
            "Cashew", "Rubber",      // Laterite
            "Millets", "Bajra",      // Arid
            "Tea", "Coffee",         // Mountain
            "Barley", "Tobacco",     // Saline
            "Jute"                   // Peaty
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, crops)
        val autoCompleteCrop = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)
        autoCompleteCrop.setAdapter(adapter)

        autoCompleteCrop.setOnItemClickListener { _, _, position, _ ->
            temporarySelectedSoil = when (crops[position]) {
                "Rice", "Wheat" -> "Alluvial Soil"
                "Cotton", "Soybean" -> "Black / Regur Soil"
                "Pulses", "Groundnut" -> "Red & Yellow Soil"
                "Cashew", "Rubber" -> "Laterite Soil"
                "Millets", "Bajra" -> "Arid / Desert Soil"
                "Tea", "Coffee" -> "Mountain / Forest Soil"
                "Barley", "Tobacco" -> "Saline & Alkaline Soil"
                "Jute" -> "Peaty & Marshy Soil"
                else -> "Alluvial Soil"
            }

            if (::manualSoilAdapter.isInitialized) manualSoilAdapter.clearSelection()
            if (::ribbonAdapter.isInitialized) ribbonAdapter.clearSelection()

            view.findViewById<TextView>(R.id.tvStep2Header)?.visibility = View.GONE
            view.findViewById<RecyclerView>(R.id.rvFilteredSoils)?.visibility = View.GONE

            Toast.makeText(requireContext(), "Soil estimated: $temporarySelectedSoil", Toast.LENGTH_SHORT).show()
        }
    }
}