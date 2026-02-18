package com.example.khetmitra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class SoilBottomSheetFragment : BottomSheetDialogFragment() {

    private var selectedSoil: String? = null
    private var fieldAreaAcres: Double = 0.0
    private var langCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text.lowercase(), langCode) ?: text
    }

    private fun d(num: Any): String {
        return TranslationHelper.convertDigits(num.toString(), langCode)
    }

    private val soilList = listOf(
        SoilType(1, "Alluvial Soil", R.drawable.soil_alluvial, "#C2A278"),
        SoilType(2, "Black / Regur Soil", R.drawable.soil_black, "#342D21"),
        SoilType(3, "Red & Yellow Soil", R.drawable.soil_red, "#B83227"),
        SoilType(4, "Laterite Soil", R.drawable.soil_laterite, "#8B5E3C"),
        SoilType(5, "Arid / Desert Soil", R.drawable.soil_arid, "#E3B484"),
        SoilType(6, "Mountain / Forest Soil", R.drawable.soil_mountain, "#4B3621"),
        SoilType(7, "Saline & Alkaline Soil", R.drawable.soil_saline, "#A8A8A8"),
        SoilType(8, "Peaty & Marshy Soil", R.drawable.soil_peaty, "#322722")
    )

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processImage(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startSoilHealthCardScanner()
        else Toast.makeText(context, t("Camera permission required to scan card"), Toast.LENGTH_SHORT).show()
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

        val prefs = requireActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        val rvSoil = view.findViewById<RecyclerView>(R.id.rvSoilTypes)
        val btnConfirmSoil = view.findViewById<MaterialButton>(R.id.btnConfirmSoil)
        val btnNotSure = view.findViewById<MaterialButton>(R.id.btnNotSure)
        val cardScanSHC = view.findViewById<MaterialCardView>(R.id.cardScanSHC)
        val btnSaveProfileMain = view.findViewById<MaterialButton>(R.id.btnSaveProfileMain)

        rvSoil.layoutManager = LinearLayoutManager(requireContext())
        rvSoil.layoutManager = GridLayoutManager(requireContext(), 2)
        rvSoil.adapter = SoilAdapter(soilList) { selected ->
            selectedSoil = selected.nameEn
            btnSaveProfileMain.isEnabled = true
        }

        btnConfirmSoil.setOnClickListener {
            selectedSoil?.let {
                saveFinalFarmData(it)
                dismiss()
            } ?: Toast.makeText(context, t("Please select soil"), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), t("Please select soil"), Toast.LENGTH_SHORT).show()
            }
        }

        if (langCode != TranslateLanguage.ENGLISH) {
            view.post {
                TranslationHelper.translateViewHierarchy(view, langCode) {}
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
                    Toast.makeText(context, t("Soil type not found on card. Try manual selection."), Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, t("Failed to read card"), Toast.LENGTH_SHORT).show()
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
        val formattedAcres = String.format(Locale.US, "%.2f", fieldAreaAcres)
        val translatedMessage = "${t("Farm profile saved")}: ${d(formattedAcres)} ${t("Acres")} | ${t("Soil")}: ${t(soilType)}"
        Toast.makeText(requireContext(), translatedMessage, Toast.LENGTH_LONG).show()
        val intent = android.content.Intent(requireContext(), MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        dismiss()
    }
}