package com.example.khetmitra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton

class SoilFallbackFragment : BottomSheetDialogFragment() {

    private var temporarySelectedSoil: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_soil_fallback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sandySoilCard = view.findViewById<MaterialCardView>(R.id.cardSandy)
        val loamySoilCard = view.findViewById<MaterialCardView>(R.id.cardLoamy)
        val blackSoilCard = view.findViewById<MaterialCardView>(R.id.cardClay)
        val btnSaveProfile = view.findViewById<MaterialButton>(R.id.btnSaveProfile)

        sandySoilCard.setOnClickListener {
            selectSoil("Sandy Soil", it as MaterialCardView)
        }
        loamySoilCard.setOnClickListener {
            selectSoil("Loamy Soil", it as MaterialCardView)
        }
        blackSoilCard.setOnClickListener {
            selectSoil("Clay/Black Soil", it as MaterialCardView)
        }

        val crops = arrayOf("Cotton", "Soybean", "Rice", "Sugarcane", "Pulses")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, crops)
        val autoCompleteCrop = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)
        autoCompleteCrop.setAdapter(adapter)

        autoCompleteCrop.setOnItemClickListener { _, _, position, _ ->
            val estimatedSoil = when (crops[position]) {
                "Rice" -> "Clay Soil"
                "Cotton" -> "Black Cotton Soil"
                else -> "Loamy Soil"
            }
            temporarySelectedSoil = estimatedSoil
        }

        btnSaveProfile.setOnClickListener {
            temporarySelectedSoil?.let {
                parentFragmentManager.setFragmentResultListener(
                    "soil_request",
                    viewLifecycleOwner
                ) { _, bundle ->
                    val detectedSoil = bundle.getString("selected_soil")
                    detectedSoil?.let {
                        saveFinalFarmData(it)
                        dismiss()
                    }
                }
            }
        }
    }

    private fun saveFinalFarmData(soilType: String) {
        Toast.makeText(requireContext(), "Farm Saved: $soilType", Toast.LENGTH_SHORT).show()
        val intent = android.content.Intent(requireContext(), MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun selectSoil(soil: String, card: MaterialCardView) {
        temporarySelectedSoil = soil
        card.strokeColor = "#4400FF00".toColorInt()
        card.strokeWidth = 4
    }
}