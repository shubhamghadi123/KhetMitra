package com.example.khetmitra

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class SoilBottomSheetFragment : BottomSheetDialogFragment() {

    private var selectedSoil: String? = null
    private var fieldAreaAcres: Double = 0.0

    private val soilList = listOf(
        SoilType(1, "Black Soil", 0),
        SoilType(2, "Red Soil",  0),
        SoilType(3, "Sandy Soil",  0),
        SoilType(4, "Clay Soil",  0)
    )

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processImage(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startSoilHealthCardScanner()
        else Toast.makeText(context, "Camera permission required to scan card", Toast.LENGTH_SHORT).show()
    }

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
        val btnSaveProfileMain = view.findViewById<MaterialButton>(R.id.btnSaveProfileMain)

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
            startSoilHealthCardScanner()
        }

        btnNotSure.setOnClickListener {
            val fallbackFrag = SoilFallbackFragment()
            fallbackFrag.show(parentFragmentManager, "SoilGuidance")
        }

        parentFragmentManager.setFragmentResultListener("soil_request", viewLifecycleOwner) { _, bundle ->
            val detectedSoil = bundle.getString("selected_soil")
            detectedSoil?.let {
                saveFinalFarmData(it)
                dismiss()
            }
        }

        btnSaveProfileMain.setOnClickListener {
            if (selectedSoil != null) {
                saveFinalFarmData(selectedSoil!!)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Please select soil first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startSoilHealthCardScanner() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val detectedSoil = parseSoilFromText(visionText.text)
                if (detectedSoil != null) {
                    saveFinalFarmData(detectedSoil)
                    dismiss()
                } else {
                    Toast.makeText(context, "Soil type not found on card. Try manual selection.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to read card", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseSoilFromText(text: String): String? {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("black") || lowerText.contains("काळी") -> "Black Soil"
            lowerText.contains("red") || lowerText.contains("तांबडी") -> "Red Soil"
            lowerText.contains("sandy") || lowerText.contains("रेताड") -> "Sandy Soil"
            lowerText.contains("clay") || lowerText.contains("चिकन") -> "Clay Soil"
            else -> null
        }
    }

    private fun saveFinalFarmData(soilType: String) {
        val finalMessage = "Field: %.2f Acres | Soil: %s".format(fieldAreaAcres, soilType)
        Toast.makeText(requireContext(), "Farm Profile Saved: $finalMessage", Toast.LENGTH_LONG).show()

        val intent = android.content.Intent(requireContext(), MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        dismiss()
    }
}