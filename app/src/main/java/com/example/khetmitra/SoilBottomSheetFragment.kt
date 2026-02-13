package com.example.khetmitra

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SoilBottomSheetFragment : BottomSheetDialogFragment() {

    private var selectedSoil: String? = null
    private var fieldAreaAcres: Double = 0.0

    private val soilList = listOf(
        SoilType(1, "Black Soil", 0),
        SoilType(2, "Red Soil",  0),
        SoilType(3, "Sandy Soil",  0),
        SoilType(4, "Clay Soil",  0)
    )

    companion object {
        fun newInstance(area: Double): SoilBottomSheetFragment {
            val fragment = SoilBottomSheetFragment()
            val args = Bundle()
            args.putDouble("ARG_AREA", area)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fieldAreaAcres = arguments?.getDouble("ARG_AREA") ?: 0.0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_soil_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvSoil = view.findViewById<RecyclerView>(R.id.rvSoilTypes)
        val btnConfirmSoil = view.findViewById<MaterialButton>(R.id.btnConfirmSoil)
        val btnNotSure = view.findViewById<MaterialButton>(R.id.btnNotSure)
        val cardScanSHC = view.findViewById<MaterialCardView>(R.id.cardScanSHC)

        // Dynamic Adapter Setup
        rvSoil.layoutManager = LinearLayoutManager(requireContext())
        rvSoil.adapter = SoilAdapter(soilList) { selected ->
            selectedSoil = selected.nameEn
        }

        btnConfirmSoil.setOnClickListener {
            selectedSoil?.let {
                saveFinalFarmData(it)
                dismiss()
            } ?: Toast.makeText(context, "Please select soil", Toast.LENGTH_SHORT).show()
        }

        cardScanSHC.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Camera for OCR...", Toast.LENGTH_SHORT).show()
        }

        // FIX: Launching the fallback guide
        btnNotSure.setOnClickListener {
            val fallbackFrag = SoilFallbackFragment()
            // This works now because fallbackFrag is a BottomSheetDialogFragment
            fallbackFrag.show(parentFragmentManager, "SoilGuidance")
        }

        // IMPORTANT: Use parentFragmentManager to match the 'show' call above
        parentFragmentManager.setFragmentResultListener("soil_request", viewLifecycleOwner) { _, bundle ->
            val detectedSoil = bundle.getString("selected_soil")
            detectedSoil?.let {
                saveFinalFarmData(it)
                dismiss() // Auto-close after help is received
            }
        }
    }

    private fun saveFinalFarmData(soilType: String) {
        val finalMessage = "Field: %.2f Acres | Soil: %s".format(fieldAreaAcres, soilType)
        Toast.makeText(requireContext(), finalMessage, Toast.LENGTH_LONG).show()
    }
}