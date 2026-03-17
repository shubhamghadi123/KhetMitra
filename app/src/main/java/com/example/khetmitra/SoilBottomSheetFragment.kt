package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// Data class for the API result
data class SoilData(val sand: Double, val silt: Double, val clay: Double, val isFallback: Boolean = false)

class SoilBottomSheetFragment : BottomSheetDialogFragment() {
    private var selectedSoil: String? = null
    private var fieldAreaAcres: Double = 0.0
    private var fieldLat: Double = 0.0
    private var fieldLng: Double = 0.0
    private var coordinatesJson: String = ""
    private var langCode: String = TranslateLanguage.ENGLISH
    private lateinit var soilList: List<SoilType>
    private var fetchedSand: Double? = null
    private var fetchedSilt: Double? = null
    private var fetchedClay: Double? = null

    fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    fun d(num: Any): String {
        return TranslationHelper.convertDigits(num.toString(), langCode)
    }

    companion object {
        fun newInstance(area: Double, lat: Double, lng: Double, coordinates: String): SoilBottomSheetFragment {
            val fragment = SoilBottomSheetFragment()
            val args = Bundle()
            args.putDouble("ARG_AREA", area)
            args.putDouble("ARG_LAT", lat)
            args.putDouble("ARG_LNG", lng)
            args.putString("ARG_COORDINATES", coordinates)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fieldAreaAcres = arguments?.getDouble("ARG_AREA") ?: 0.0
        fieldLat = arguments?.getDouble("ARG_LAT") ?: 0.0
        fieldLng = arguments?.getDouble("ARG_LNG") ?: 0.0
        coordinatesJson = arguments?.getString("ARG_COORDINATES") ?: "[]"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        val pbLoadingApi = view.findViewById<ProgressBar>(R.id.pbLoadingApi)
        val layoutApiContent = view.findViewById<View>(R.id.layoutApiContent)
        val layoutApiError = view.findViewById<View>(R.id.layoutApiError)
        val btnRetryApi = view.findViewById<android.widget.Button>(R.id.btnRetryApi)
        val tvLocalMatchDesc = view.findViewById<TextView>(R.id.tvLocalMatchDesc)
        val btnLocalSoilMatchCard = view.findViewById<MaterialCardView>(R.id.btnLocalSoilMatch)
        val btnLocalSoilMatchText = view.findViewById<TextView>(R.id.btnLocalSoilMatchText)
        val btnSaveProfileMain = view.findViewById<MaterialCardView>(R.id.btnSaveProfileMain)

        fun fetchSatelliteData() {
            pbLoadingApi.visibility = View.VISIBLE
            layoutApiContent.visibility = View.INVISIBLE
            layoutApiError?.visibility = View.GONE
            lifecycleScope.launch {
                val fetchedSoil = if (fieldLat != 0.0 && fieldLng != 0.0) {
                    fetchSoilDataFromApi(fieldLat, fieldLng)
                } else null
                pbLoadingApi.visibility = View.GONE
                if (fetchedSoil != null) {
                    fetchedSand = fetchedSoil.sand
                    fetchedSilt = fetchedSoil.silt
                    fetchedClay = fetchedSoil.clay
                    layoutApiContent.visibility = View.VISIBLE
                    layoutApiError?.visibility = View.GONE
                    val sandFmt = String.format(Locale.US, "%.1f", fetchedSoil.sand)
                    val siltFmt = String.format(Locale.US, "%.1f", fetchedSoil.silt)
                    val clayFmt = String.format(Locale.US, "%.1f", fetchedSoil.clay)
                    view.findViewById<TextView>(R.id.tvSandVal).text = "${d(sandFmt)}%"
                    view.findViewById<TextView>(R.id.tvSiltVal).text = "${d(siltFmt)}%"
                    view.findViewById<TextView>(R.id.tvClayVal).text = "${d(clayFmt)}%"
                    view.findViewById<TextView>(R.id.tvSandLabel).text = t("Sand")
                    view.findViewById<TextView>(R.id.tvSiltLabel).text = t("Silt")
                    view.findViewById<TextView>(R.id.tvClayLabel).text = t("Clay")
                    val tvSatelliteTitle = view.findViewById<TextView>(R.id.tvSatelliteTitle)
                    if (fetchedSoil.isFallback) {
                        tvSatelliteTitle.text = t("Regional Analysis")
                        tvSatelliteTitle.setTextColor("#FFCC00".toColorInt())
                    } else {
                        tvSatelliteTitle.text = t("Satellite Analysis")
                        tvSatelliteTitle.setTextColor(android.graphics.Color.WHITE)
                    }
                    val mappedSoilData = mapTextureToIndianSoil(fetchedSoil)
                    val soilName = mappedSoilData.first
                    val colorHint = mappedSoilData.second
                    val translatedSoil = t(soilName)
                    val descTemplate = if (fetchedSoil.isFallback) {
                        t("Based on regional data for your location, the soil is likely [SOIL]. Is it [COLOR]?")
                    } else {
                        t("Based on satellite analysis, your soil is likely [SOIL]. Is your soil [COLOR]?")
                    }
                    tvLocalMatchDesc.text = descTemplate
                        .replace("[SOIL]", translatedSoil)
                        .replace("[COLOR]", t(colorHint))
                    btnLocalSoilMatchText.text = t("Confirm: [SOIL]").replace("[SOIL]", translatedSoil)
                    btnLocalSoilMatchCard.setOnClickListener {
                        selectedSoil = soilName
                        saveFinalFarmData(soilName)
                    }
                } else {
                    layoutApiError?.visibility = View.VISIBLE
                    layoutApiContent.visibility = View.INVISIBLE
                    Toast.makeText(requireContext(), t("Satellite service busy. Please try again or choose manually."), Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnRetryApi?.setOnClickListener {
            fetchSatelliteData()
        }
        fetchSatelliteData()
        view.findViewById<TextView>(R.id.tvManualLabel).text = t("Choose manually:")
        rvSoil.layoutManager = GridLayoutManager(requireContext(), 2)
        rvSoil.adapter = SoilAdapter(soilList) { selected ->
            selectedSoil = when (selected.id) {
                1 -> "Alluvial Soil"
                2 -> "Black / Regur Soil"
                3 -> "Red & Yellow Soil"
                4 -> "Laterite Soil"
                5 -> "Arid / Desert Soil"
                6 -> "Mountain / Forest Soil"
                7 -> "Saline & Alkaline Soil"
                8 -> "Peaty & Marshy Soil"
                else -> "Unknown Soil"
            }
            btnSaveProfileMain.isEnabled = true
        }

        view.findViewById<TextView>(R.id.btnNotSure).apply {
            text = t("Not sure about soil type?")
            setOnClickListener {
                val fallbackFrag = SoilFallbackFragment()
                fallbackFrag.show(parentFragmentManager, "SoilGuidance")
            }
        }

        btnSaveProfileMain.setOnClickListener {
            if (selectedSoil != null) {
                saveFinalFarmData(selectedSoil!!)
            } else {
                Toast.makeText(requireContext(), t("Please select soil"), Toast.LENGTH_SHORT).show()
            }
        }

        parentFragmentManager.setFragmentResultListener("soil_request", viewLifecycleOwner) { _, bundle ->
            val detectedSoil = bundle.getString("selected_soil")
            val detectedCrop = bundle.getString("selected_crop") ?: "Not Selected"
            detectedSoil?.let { saveFinalFarmData(it, detectedCrop) }
        }
    }

    private suspend fun fetchSoilDataFromApi(lat: Double, lon: Double): SoilData {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SoilGrids", "Requesting soil data for Lat: $lat, Lon: $lon")

                val urlString = "https://rest.isric.org/soilgrids/v2.0/properties/query?lat=$lat&lon=$lon&property=clay&property=sand&property=silt&depth=0-5cm&value=mean"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000 // Faster timeout for better UX
                connection.readTimeout = 4000

                val responseCode = connection.responseCode
                Log.d("SoilGrids", "Response Code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().readText()
                    val jsonObject = org.json.JSONObject(responseText)
                    val properties = jsonObject.optJSONObject("properties") ?: return@withContext getRegionalSoilGuess(lat, lon)
                    val layers = properties.optJSONArray("layers") ?: return@withContext getRegionalSoilGuess(lat, lon)

                    var sand = 0.0; var silt = 0.0; var clay = 0.0

                    for (i in 0 until layers.length()) {
                        val layer = layers.getJSONObject(i)
                        val name = layer.getString("name")
                        val depthsArray = layer.optJSONArray("depths")

                        if (depthsArray != null && depthsArray.length() > 0) {
                            val valuesObj = depthsArray.getJSONObject(0).optJSONObject("values")
                            if (valuesObj != null && valuesObj.has("mean")) {
                                val meanValue = valuesObj.getDouble("mean") / 10.0
                                when (name) {
                                    "sand" -> sand = meanValue
                                    "silt" -> silt = meanValue
                                    "clay" -> clay = meanValue
                                }
                            }
                        }
                    }
                    Log.d("SoilGrids", "Success from API")
                    return@withContext SoilData(sand, silt, clay, isFallback = false)
                } else {
                    Log.e("SoilGrids", "Server Error $responseCode. Triggering Fallback.")
                    return@withContext getRegionalSoilGuess(lat, lon)
                }
            } catch (_: Exception) {
                Log.e("SoilGrids", "Network failure. Triggering Fallback.")
                return@withContext getRegionalSoilGuess(lat, lon)
            }
        }
    }

    private fun getRegionalSoilGuess(lat: Double, lon: Double): SoilData {
        return when {
            // Maharashtra / Gujarat / MP (Black Soil / High Clay)
            lat in 15.0..24.0 && lon in 70.0..80.0 -> SoilData(15.0, 25.0, 60.0, true)
            // North India / Indo-Gangetic Plain (Alluvial / High Silt)
            lat in 24.0..32.0 && lon in 74.0..90.0 -> SoilData(35.0, 45.0, 20.0, true)
            // Rajasthan / Bordering regions (Arid / High Sand)
            lat in 24.0..30.0 && lon in 69.0..75.0 -> SoilData(85.0, 10.0, 5.0, true)
            // South/Coastal (Laterite/Red Zone)
            lat in 8.0..15.0 -> SoilData(40.0, 20.0, 40.0, true)
            // Default Indian Average
            else -> SoilData(33.0, 33.0, 34.0, true)
        }
    }

    private fun mapTextureToIndianSoil(soil: SoilData): Pair<String, String> {
        return when {
            soil.clay >= 35.0 -> Pair("Black / Regur Soil", "Black / Dark Brown")
            soil.sand >= 65.0 -> Pair("Arid / Desert Soil", "Light Brown / Sandy")
            soil.silt >= 40.0 && soil.sand < 50.0 -> Pair("Alluvial Soil", "Light Gray / Ashy")
            else -> Pair("Red & Yellow Soil", "Red / Yellowish")
        }
    }

    private fun saveFinalFarmData(soilType: String, cropType: String = "Not Selected") {
        val dbAreaText = if (fieldAreaAcres < 1.0) String.format(Locale.US, "%.2f Guntas", fieldAreaAcres * 40)
        else String.format(Locale.US, "%.2f Acres", fieldAreaAcres)

        Toast.makeText(requireContext(), t("Saving farm profile..."), Toast.LENGTH_SHORT).show()
        val safeContext = requireContext()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                if (user != null) {
                    val existingFarms = SupabaseManager.client.postgrest["farms"]
                        .select { filter { eq("farmer_id", user.id) } }
                        .decodeList<FarmEntry>()

                    var nextNumber = existingFarms.size + 1
                    var autoName = "Farm $nextNumber"
                    while (existingFarms.mapNotNull { it.name }.contains(autoName)) {
                        nextNumber++
                        autoName = "Farm $nextNumber"
                    }

                    // Uses the updated FarmEntry model with percentages
                    val newFarm = FarmEntry(
                        farmer_id = user.id,
                        name = autoName,
                        land_size = dbAreaText,
                        soil_type = soilType,
                        coordinates = coordinatesJson,
                        crop = cropType,
                        sand_pct = fetchedSand,
                        silt_pct = fetchedSilt,
                        clay_pct = fetchedClay
                    )

                    SupabaseManager.client.postgrest["farms"].insert(newFarm)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(safeContext, t("Farm saved successfully!"), Toast.LENGTH_LONG).show()
                        dismiss()
                        val intent = android.content.Intent(safeContext, ManageFieldsActivity::class.java)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        safeContext.startActivity(intent)
                        activity?.finish()
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) { Toast.makeText(safeContext, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}