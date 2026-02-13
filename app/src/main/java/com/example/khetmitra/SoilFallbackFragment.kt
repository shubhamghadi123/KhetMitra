package com.example.khetmitra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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

        // 1. Handle Ribbon Test Card Clicks
        view.findViewById<MaterialCardView>(R.id.cardSandy).setOnClickListener {
            selectSoil("Sandy Soil", it as MaterialCardView)
        }
        view.findViewById<MaterialCardView>(R.id.cardLoamy).setOnClickListener {
            selectSoil("Loamy Soil", it as MaterialCardView)
        }
        view.findViewById<MaterialCardView>(R.id.cardClay).setOnClickListener {
            selectSoil("Clay/Black Soil", it as MaterialCardView)
        }

        // 2. Setup Crop Dropdown (The "OR" Path)
        val crops = arrayOf("Cotton", "Soybean", "Rice", "Sugarcane", "Pulses")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, crops)
        val autoCompleteCrop = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCrop)
        autoCompleteCrop.setAdapter(adapter)

        autoCompleteCrop.setOnItemClickListener { _, _, position, _ ->
            // Logic: Estimate soil based on crop
            val estimatedSoil = when(crops[position]) {
                "Rice" -> "Clay Soil"
                "Cotton" -> "Black Cotton Soil"
                else -> "Loamy Soil"
            }
            temporarySelectedSoil = estimatedSoil
        }

        // 3. Save Button
        view.findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            temporarySelectedSoil?.let {
                parentFragmentManager.setFragmentResult("soil_request", bundleOf("selected_soil" to it))
                dismiss()
            }
        }
    }

    private fun selectSoil(soil: String, card: MaterialCardView) {
        temporarySelectedSoil = soil
        // Optional: Visual feedback (stroke color change)
        card.strokeColor = "#4400FF00".toColorInt()
        card.strokeWidth = 4
    }
}