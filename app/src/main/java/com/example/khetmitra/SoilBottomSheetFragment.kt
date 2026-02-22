package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
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

    private var fieldLat: Double = 0.0
    private var fieldLng: Double = 0.0
    private var langCode: String = TranslateLanguage.ENGLISH

    private lateinit var soilList: List<SoilType>

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String {
        return TranslationHelper.convertDigits(num.toString(), langCode)
    }

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
        fun newInstance(area: Double, lat: Double, lng: Double): SoilBottomSheetFragment {
            val fragment = SoilBottomSheetFragment()
            val args = Bundle()
            args.putDouble("ARG_AREA", area)
            args.putDouble("ARG_LAT", lat)
            args.putDouble("ARG_LNG", lng)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fieldAreaAcres = arguments?.getDouble("ARG_AREA") ?: 0.0
        fieldLat = arguments?.getDouble("ARG_LAT") ?: 0.0
        fieldLng = arguments?.getDouble("ARG_LNG") ?: 0.0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_soil_bottom_sheet, container, false)
    }

    @SuppressLint("SetTextI18n")
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

        val rvSoil = view.findViewById<RecyclerView>(R.id.rvSoilTypes)
        val tvLocalMatchDesc = view.findViewById<TextView>(R.id.tvLocalMatchDesc)
        val btnLocalSoilMatch = view.findViewById<MaterialButton>(R.id.btnLocalSoilMatch)
        val btnNotSure = view.findViewById<MaterialButton>(R.id.btnNotSure)
        val cardScanSHC = view.findViewById<MaterialCardView>(R.id.cardScanSHC)
        val btnSaveProfileMain = view.findViewById<MaterialButton>(R.id.btnSaveProfileMain)

        var recommendedSoilData = Pair("Black / Regur Soil", "Black/Dark Brown")

        if (fieldLat != 0.0 && fieldLng != 0.0) {
            try {
                val geocoder = android.location.Geocoder(requireContext(), Locale.ENGLISH)
                val addresses = geocoder.getFromLocation(fieldLat, fieldLng, 1)

                if (!addresses.isNullOrEmpty()) {
                    val stateName = addresses[0].adminArea ?: ""

                    recommendedSoilData = when {
                        stateName.contains("Maharashtra", true) || stateName.contains("Gujarat", true) || stateName.contains("Madhya Pradesh", true) ->
                            Pair("Black / Regur Soil", "Black / Dark Brown")

                        stateName.contains("Punjab", true) || stateName.contains("Haryana", true) || stateName.contains("Uttar Pradesh", true) || stateName.contains("Bihar", true) ->
                            Pair("Alluvial Soil", "Light Gray / Ashy")

                        stateName.contains("Rajasthan", true) ->
                            Pair("Arid / Desert Soil", "Light Brown / Sandy")

                        stateName.contains("Karnataka", true) || stateName.contains("Kerala", true) || stateName.contains("Tamil Nadu", true) || stateName.contains("Odisha", true) ->
                            Pair("Red & Yellow Soil", "Red / Yellowish")

                        stateName.contains("Assam", true) || stateName.contains("Himachal", true) || stateName.contains("Uttarakhand", true) ->
                            Pair("Mountain / Forest Soil", "Dark Brown / Blackish")

                        else -> Pair("Alluvial Soil", "Light Gray / Ashy")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val soilName = recommendedSoilData.first
        val colorHint = recommendedSoilData.second

        tvLocalMatchDesc?.text = ""
        btnLocalSoilMatch?.text = ""

        val loadDynamicContent = {
            tvLocalMatchDesc?.text = "${t("Most farms near you have")} ${t(soilName)}. ${t("Is your soil")} ${t(colorHint)}?"
            btnLocalSoilMatch?.text = "${t("Yes, it's")} ${t(soilName)}"

            rvSoil.layoutManager = GridLayoutManager(requireContext(), 2)
            rvSoil.adapter = SoilAdapter(soilList) { selected ->
                selectedSoil = selected.nameEn
                btnSaveProfileMain.isEnabled = true
            }

            btnLocalSoilMatch?.setOnClickListener {
                selectedSoil = soilName
                saveFinalFarmData(soilName)
            }
        }

        if (langCode != TranslateLanguage.ENGLISH) {
            view.post {
                TranslationHelper.translateViewHierarchy(view, langCode) {
                    view.post { loadDynamicContent() }
                }
            }
        } else {
            loadDynamicContent()
        }

        rvSoil.layoutManager = GridLayoutManager(requireContext(), 2)
        val attachAdapter = {
            rvSoil.adapter = SoilAdapter(soilList) { selected ->
                selectedSoil = selected.nameEn
                btnSaveProfileMain.isEnabled = true
            }
        }

        btnLocalSoilMatch.setOnClickListener {
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
                TranslationHelper.translateViewHierarchy(view, langCode) {
                    rvSoil.post { attachAdapter() }
                }
            }
        } else {
            attachAdapter()
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
        val displayAreaText = if (fieldAreaAcres < 1.0) {
            val areaGuntas = fieldAreaAcres * 40
            val formattedGuntas = String.format(Locale.US, "%.2f", areaGuntas)
            "${d(formattedGuntas)} ${t("Guntas")}"
        } else {
            val formattedAcres = String.format(Locale.US, "%.2f", fieldAreaAcres)
            "${d(formattedAcres)} ${t("Acres")}"
        }
        val translatedMessage = "${t("Farm profile saved")}\n${t("Area")}: $displayAreaText\n${t("Soil")}: ${t(soilType)}"

        Toast.makeText(requireContext(), translatedMessage, Toast.LENGTH_LONG).show()

        val intent = android.content.Intent(requireContext(), MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        dismiss()
    }
}