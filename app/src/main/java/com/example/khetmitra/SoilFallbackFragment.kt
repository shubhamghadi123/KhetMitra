package com.example.khetmitra

import android.content.Context
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
import com.google.mlkit.nl.translate.TranslateLanguage

class SoilFallbackFragment : BottomSheetDialogFragment() {

    private var temporarySelectedSoil: String? = null
    private lateinit var manualSoilAdapter: SoilAdapter
    private lateinit var ribbonAdapter: RibbonAdapter
    private var langCode: String = TranslateLanguage.ENGLISH

    private lateinit var soilList: List<SoilType>

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_soil_fallback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        soilList = listOf(
            SoilType(1, t("Alluvial Soil"), R.drawable.soil_alluvial, "#C2A278"),
            SoilType(2, t("Black / Regur Soil"), R.drawable.soil_black, "#1A1A1A"),
            SoilType(3, t("Red & Yellow Soil"), R.drawable.soil_red, "#9E231C"),
            SoilType(4, t("Laterite Soil"), R.drawable.soil_laterite, "#8B5E3C"),
            SoilType(5, t("Arid / Desert Soil"), R.drawable.soil_arid, "#E3B484"),
            SoilType(6, t("Mountain / Forest Soil"), R.drawable.soil_mountain, "#3E2B1A"),
            SoilType(7, t("Saline & Alkaline Soil"), R.drawable.soil_saline, "#A8A8A8"),
            SoilType(8, t("Peaty & Marshy Soil"), R.drawable.soil_peaty, "#2B1E18")
        )

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
                Toast.makeText(requireContext(), t("Please select an option"), Toast.LENGTH_SHORT).show()
            }
        }

        if (langCode != TranslateLanguage.ENGLISH) {
            view.post {
                TranslationHelper.translateViewHierarchy(view, langCode) {}
            }
        }
    }

    private fun showRibbonTestInstructions() {
        val customView = layoutInflater.inflate(R.layout.ribbon_instructions, null)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(customView)
            .setCancelable(true)
            .create()

        if (langCode != TranslateLanguage.ENGLISH) {
            customView.post {
                TranslationHelper.translateViewHierarchy(customView, langCode) {}
            }
        }

        val btnGotIt = customView.findViewById<MaterialButton>(R.id.btnGotIt)
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
            RibbonData(t("Breaks Easily"), t("Arid / Desert Soil")),
            RibbonData(t("Short Ribbon"), t("Alluvial / Red / Laterite / Mountain Soil")),
            RibbonData(t("Long Ribbon"), t("Black / Peaty / Saline Soil"))
        )

        ribbonAdapter = RibbonAdapter(ribbonOptions) { selected ->
            view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)?.apply {
                setText(t(""), false)
                clearFocus()
            }
            temporarySelectedSoil = null

            tvHeader2?.visibility = View.VISIBLE
            rvSoil?.visibility = View.VISIBLE

            val filteredSoils = when(selected.result) {
                t("Breaks Easily") -> soilList.filter { it.nameEn in listOf(t("Arid / Desert Soil")) }
                t("Short Ribbon") -> soilList.filter { it.nameEn in listOf(t("Alluvial Soil"),t("Red & Yellow Soil"),t("Laterite Soil"),t("Mountain / Forest Soil")) }
                t("Long Ribbon") -> soilList.filter { it.nameEn in listOf(t("Black / Regur Soil"),t("Peaty & Marshy Soil"),t("Saline & Alkaline Soil")) }
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

            manualSoilAdapter = SoilAdapter(emptyList()) { selectedSoil ->
                temporarySelectedSoil = selectedSoil.nameEn
                view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)?.apply {
                    setText(t(""), false)
                    clearFocus()
                }
            }
            rvSoil.adapter = manualSoilAdapter
        }
    }

    private fun setupCropEstimation(view: View) {
        val crops = arrayOf(
            t("Rice"), t("Wheat"),        // Alluvial
            t("Cotton"), t("Soybean"),     // Black
            t("Pulses"), t("Groundnut"),   // Red & Yellow
            t("Cashew"), t("Rubber"),      // Laterite
            t("Millets"), t("Bajra"),      // Arid
            t("Tea"), t("Coffee"),         // Mountain
            t("Barley"), t("Tobacco"),     // Saline
            t("Jute")                   // Peaty
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line,
            crops
        )
        val autoCompleteCrop = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)
        autoCompleteCrop.setAdapter(adapter)

        autoCompleteCrop.setOnItemClickListener { _, _, position, _ ->
            temporarySelectedSoil = when (crops[position]) {
                t("Rice"), t("Wheat") -> t("Alluvial Soil")
                t("Cotton"), t("Soybean") -> t("Black / Regur Soil")
                t("Pulses"), t("Groundnut") -> t("Red & Yellow Soil")
                t("Cashew"), t("Rubber") -> t("Laterite Soil")
                t("Millets"), t("Bajra") -> t("Arid / Desert Soil")
                t("Tea"), t("Coffee") -> t("Mountain / Forest Soil")
                t("Barley"), t("Tobacco") -> t("Saline & Alkaline Soil")
                t("Jute") -> t("Peaty & Marshy Soil")
                else -> t("Alluvial Soil")
            }

            if (::manualSoilAdapter.isInitialized) manualSoilAdapter.clearSelection()
            if (::ribbonAdapter.isInitialized) ribbonAdapter.clearSelection()

            view.findViewById<TextView>(R.id.tvStep2Header)?.visibility = View.GONE
            view.findViewById<RecyclerView>(R.id.rvFilteredSoils)?.visibility = View.GONE

            Toast.makeText(requireContext(), "${t("Soil Estimated")}: ${t(temporarySelectedSoil!!)}", Toast.LENGTH_SHORT).show()
        }
    }
}