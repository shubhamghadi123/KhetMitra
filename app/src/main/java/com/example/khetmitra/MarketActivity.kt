package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

class MarketActivity : AppCompatActivity() {
    private lateinit var lineChart: LineChart
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var allStates: List<StateRow> = emptyList()
    private var allDistricts: List<DistrictRow> = emptyList()
    private var allMarkets: List<MarketRow> = emptyList()
    private var allCrops: List<CropRow> = emptyList()
    private var activeDistrictMarkets: List<MarketRow> = emptyList()
    private var districtPriceData: Map<Int, List<CropPriceRow>> = emptyMap()
    private var selectedStateId: Int = -1
    private var selectedDistrictId: Int = -1
    private var selectedMarketId: Int = -1
    private var selectedCropId: Int = -1
    private var isDistrictMode: Boolean = false
    private lateinit var dropdownState: AutoCompleteTextView
    private lateinit var dropdownDistrict: AutoCompleteTextView
    private lateinit var dropdownMarket: AutoCompleteTextView
    private lateinit var dropdownCrop: AutoCompleteTextView
    private lateinit var tvPriceValue: TextView
    private lateinit var tvMinPrice: TextView
    private lateinit var tvMaxPrice: TextView
    private lateinit var tvPriceUnit: TextView
    private lateinit var tvQuantity: TextView
    private lateinit var tvQuantityUnit: TextView
    private lateinit var tvPriceDate: TextView
    private lateinit var tvNoDataMarket: LinearLayout
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnFetchPrice: MaterialButton
    private lateinit var cardPrice: MaterialCardView
    private lateinit var cardGraph: MaterialCardView
    private lateinit var btnPrevPeriod: ImageView
    private lateinit var btnNextPeriod: ImageView
    private lateinit var tvDateRange: TextView
    private lateinit var marketTitleLayout: LinearLayout
    private lateinit var tvMarketName: TextView
    private lateinit var btnPrevMarket: ImageView
    private lateinit var btnNextMarket: ImageView
    private var chartEndDate: LocalDate = LocalDate.now()
    private var langCode: String = TranslateLanguage.ENGLISH
    private var currentMarketIndex = 0
    private var currentRequestId = 0
    private var priceHistoryJob: Job? = null
    private var translateMarketJob: Job? = null

    private fun t(text: String): String {
        if (langCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, langCode) ?: text
    }

    private fun d(num: Any): String = TranslationHelper.convertDigits(num.toString(), langCode)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchLocation()
        else Toast.makeText(this, t("Location permission denied. Please select manually."), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market)
        updateLangCode()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }

        dropdownState    = findViewById(R.id.dropdownState)
        dropdownDistrict = findViewById(R.id.dropdownDistrict)
        dropdownMarket   = findViewById(R.id.dropdownMarket)
        dropdownCrop     = findViewById(R.id.dropdownCrop)
        tvPriceValue     = findViewById(R.id.tvPriceValue)
        tvMinPrice       = findViewById(R.id.tvMinPrice)
        tvMaxPrice       = findViewById(R.id.tvMaxPrice)
        tvPriceUnit      = findViewById(R.id.tvPriceUnit)
        tvQuantity = findViewById(R.id.tvQuantity)
        tvQuantityUnit = findViewById(R.id.tvQuantityUnit)
        tvPriceDate      = findViewById(R.id.tvPriceDate)
        tvNoDataMarket = findViewById(R.id.tvNoDataMarket)
        toggleGroup      = findViewById(R.id.toggleGroup)
        lineChart        = findViewById(R.id.lineChart)
        btnFetchPrice    = findViewById(R.id.btnFetchPrice)
        cardPrice        = findViewById(R.id.cardPrice)
        cardGraph        = findViewById(R.id.cardGraph)
        btnPrevPeriod    = findViewById(R.id.btnPrevPeriod)
        btnNextPeriod    = findViewById(R.id.btnNextPeriod)
        tvDateRange      = findViewById(R.id.tvDateRange)
        marketTitleLayout = findViewById(R.id.tvMarketTitle)
        tvMarketName = findViewById(R.id.tvMarketName)
        btnPrevMarket = findViewById(R.id.btnPrevMarket)
        btnNextMarket = findViewById(R.id.btnNextMarket)

        setupChartAppearance()
        dropdownDistrict.isEnabled = false
        dropdownMarket.isEnabled   = false

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                chartEndDate = LocalDate.now()
                if (isDistrictMode && selectedDistrictId != -1) {
                    loadPriceHistory(checkedId == R.id.btnWeekly, isDistrictLevel = true)
                } else if (!isDistrictMode && selectedMarketId != -1) {
                    loadPriceHistory(checkedId == R.id.btnWeekly, isDistrictLevel = false)
                }
            }
        }

        btnFetchPrice.setOnClickListener {
            chartEndDate = LocalDate.now()
            currentMarketIndex = 0

            if (selectedMarketId == -1) {
                isDistrictMode = true
                marketTitleLayout.visibility = View.VISIBLE
                cardPrice.visibility = View.GONE
                cardGraph.visibility = View.VISIBLE
                loadPriceHistory(toggleGroup.checkedButtonId == R.id.btnWeekly, isDistrictLevel = true)
            } else {
                isDistrictMode = false
                marketTitleLayout.visibility = View.GONE
                refreshPriceData()
            }
        }

        btnPrevPeriod.setOnClickListener {
            val isWeekly = toggleGroup.checkedButtonId == R.id.btnWeekly
            chartEndDate = if (isWeekly) chartEndDate.minusDays(7) else chartEndDate.minusMonths(12)
            loadPriceHistory(isWeekly, isDistrictLevel = isDistrictMode)
        }

        btnNextPeriod.setOnClickListener {
            val isWeekly = toggleGroup.checkedButtonId == R.id.btnWeekly
            chartEndDate = if (isWeekly) chartEndDate.plusDays(7) else chartEndDate.plusMonths(12)
            if (chartEndDate.isAfter(LocalDate.now())) {
                chartEndDate = LocalDate.now()
            }
            loadPriceHistory(isWeekly, isDistrictLevel = isDistrictMode)
        }

        btnPrevMarket.setOnClickListener {
            if (isDistrictMode && activeDistrictMarkets.isNotEmpty()) {
                currentMarketIndex = if (currentMarketIndex > 0) currentMarketIndex - 1 else activeDistrictMarkets.size - 1
                drawChartForCurrentMarket(toggleGroup.checkedButtonId == R.id.btnWeekly)
            }
        }

        btnNextMarket.setOnClickListener {
            if (isDistrictMode && activeDistrictMarkets.isNotEmpty()) {
                currentMarketIndex = (currentMarketIndex + 1) % activeDistrictMarkets.size
                drawChartForCurrentMarket(toggleGroup.checkedButtonId == R.id.btnWeekly)
            }
        }

        if (langCode != TranslateLanguage.ENGLISH) {
            window.decorView.post {
                TranslationHelper.translateViewHierarchy(window.decorView.rootView, langCode) {}
                translateHints()
            }
        }

        lifecycleScope.launch {
            loadStates()
            loadCrops()
            requestLocationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        updateLangCode()
    }

    private fun updateLangCode() {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        langCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
    }

    private fun validateFetchButton() {
        val isReady = selectedCropId != -1 && selectedStateId != -1 && selectedDistrictId != -1
        btnFetchPrice.isEnabled = isReady
        btnFetchPrice.setBackgroundColor(if (isReady) "#2D6A4F".toColorInt() else "#A8D5B5".toColorInt())
    }

    private fun requestLocationPermission() {
        val fine   = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val hasFine   = ContextCompat.checkSelfPermission(this, fine)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) fetchLocation()
        else locationPermissionLauncher.launch(arrayOf(fine, coarse))
    }

    private fun AutoCompleteTextView.applyCustomDropdownStyle(items: List<String>) {
        val adapter = ArrayAdapter(this@MarketActivity, R.layout.custom_spinner_dropdown_item, items)
        this.setAdapter(adapter)
        this.setDropDownBackgroundResource(R.drawable.bg_spinner_dropdown)
    }

    private fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val cts = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                reverseGeocode(location.latitude, location.longitude)
            } else {
                getLastKnownLocation()
            }
        }.addOnFailureListener {
            getLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                reverseGeocode(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, t("Could not get location. Select manually."), Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, t("Could not get location. Select manually."), Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lng: Double) {
        try {
            val geocoder = Geocoder(this, Locale("en", "IN"))
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses.isNullOrEmpty()) {
                Toast.makeText(this, t("Could not detect location. Select manually."), Toast.LENGTH_SHORT).show()
                return
            }
            val detectedState = addresses[0].adminArea?.trim() ?: ""
            Log.d("Location", "Detected state: $detectedState")
            if (detectedState.isNotEmpty()) autoSelectState(detectedState)
            else Toast.makeText(this, t("State not detected. Select manually."), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Location", "Geocoder failed: ${e.message}")
            Toast.makeText(this, t("Geocoder error. Select manually."), Toast.LENGTH_SHORT).show()
        }
    }

    private fun translateHints() {
        if (langCode == TranslateLanguage.ENGLISH) return
        mapOf(
            R.id.dropdownCrop     to "Commodity (Crop)",
            R.id.dropdownState    to "State",
            R.id.dropdownDistrict to "District",
            R.id.dropdownMarket   to "Market (Mandi)"
        ).forEach { (id, hint) ->
            val view = findViewById<View>(id)
            val til = view?.parent?.parent as? com.google.android.material.textfield.TextInputLayout
            til?.hint = t(hint)
        }
    }

    private fun autoSelectState(detectedState: String) {
        val match = allStates.firstOrNull {
            it.stateName.equals(detectedState, ignoreCase = true)
                    || it.stateName.contains(detectedState, ignoreCase = true)
                    || detectedState.contains(it.stateName, ignoreCase = true)
        }
        if (match == null) {
            Toast.makeText(this, "State \"$detectedState\" not found. Select manually.", Toast.LENGTH_LONG).show()
            return
        }
        selectedStateId = match.stateId
        dropdownState.setText(t(match.stateName), false)
        validateFetchButton()
        lifecycleScope.launch { loadDistricts(selectedStateId) }
    }

    private suspend fun loadStates() {
        try {
            allStates = SupabaseManager.client
                .postgrest["states"]
                .select {
                    filter { eq("status", 1) }
                    order("state_name", Order.ASCENDING)
                }
                .decodeList<StateRow>()
            val names = allStates.map { t(it.stateName) }
            dropdownState.applyCustomDropdownStyle(names)
            dropdownState.setOnItemClickListener { _, _, pos, _ ->
                selectedStateId    = allStates[pos].stateId
                selectedDistrictId = -1
                selectedMarketId   = -1
                dropdownDistrict.setText("", false)
                dropdownMarket.setText("", false)
                dropdownDistrict.isEnabled = false
                dropdownMarket.isEnabled   = false
                clearPriceUI()
                validateFetchButton()
                lifecycleScope.launch { loadDistricts(selectedStateId) }
            }
        } catch (e: Exception) {
            Log.e("Market", "loadStates failed: ${e.message}")
        }
    }

    private suspend fun loadDistricts(stateId: Int) {
        try {
            allDistricts = SupabaseManager.client
                .postgrest["districts"]
                .select {
                    filter {
                        eq("state_id", stateId)
                        eq("status", 1)
                    }
                    order("district_name", Order.ASCENDING)
                }
                .decodeList<DistrictRow>()

            val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(langCode)
                .build()
            val client = com.google.mlkit.nl.translate.Translation.getClient(options)

            val translatedNames = withContext(Dispatchers.IO) {
                allDistricts.map { district ->
                    val manualTranslation = t(district.districtName)
                    if (langCode != TranslateLanguage.ENGLISH &&
                        Regex("[a-zA-Z]").containsMatchIn(manualTranslation)) {
                        try { client.translate(manualTranslation).await() }
                        catch (_: Exception) { manualTranslation }
                    } else manualTranslation
                }
            }

            withContext(Dispatchers.Main) {
                dropdownDistrict.applyCustomDropdownStyle(translatedNames)
                dropdownDistrict.isEnabled = true
                dropdownDistrict.setOnItemClickListener { _, _, pos, _ ->
                    currentMarketIndex = 0
                    selectedDistrictId = allDistricts[pos].districtId
                    selectedMarketId   = -1
                    dropdownMarket.setText("", false)
                    dropdownMarket.isEnabled = false
                    clearPriceUI()
                    validateFetchButton()
                    lifecycleScope.launch { loadMarkets(selectedDistrictId) }
                }
                client.close()
            }
        } catch (e: Exception) {
            Log.e("Market", "loadDistricts failed: ${e.message}")
        }
    }

    private suspend fun loadMarkets(districtId: Int) {
        try {
            allMarkets = SupabaseManager.client
                .postgrest["markets"]
                .select {
                    filter {
                        eq("district_id", districtId)
                        eq("status", 1)
                    }
                    order("market_name", Order.ASCENDING)
                }
                .decodeList<MarketRow>()

            val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(langCode)
                .build()
            val client = com.google.mlkit.nl.translate.Translation.getClient(options)

            val translatedNames = withContext(Dispatchers.IO) {
                allMarkets.map { market ->
                    val manualTranslation = t(market.marketName)
                    if (langCode != TranslateLanguage.ENGLISH &&
                        Regex("[a-zA-Z]").containsMatchIn(manualTranslation)) {
                        try { client.translate(manualTranslation).await() }
                        catch (_: Exception) { manualTranslation }
                    } else manualTranslation
                }
            }

            withContext(Dispatchers.Main) {
                dropdownMarket.applyCustomDropdownStyle(translatedNames)
                dropdownMarket.isEnabled = true
                dropdownMarket.setOnItemClickListener { _, _, pos, _ ->
                    selectedMarketId = allMarkets[pos].marketId
                    clearPriceUI()
                    validateFetchButton()
                }
                client.close()
            }
        } catch (e: Exception) {
            Log.e("Market", "loadMarkets failed: ${e.message}")
        }
    }

    private suspend fun loadCrops() {
        try {
            allCrops = SupabaseManager.client
                .postgrest["crops"]
                .select {
                    filter { eq("status", 1) }
                    filter { eq("crop_group_id", 1) }
                    order("crop_name", Order.ASCENDING)
                }
                .decodeList<CropRow>()

            val names = allCrops.map { t(it.cropName) }

            withContext(Dispatchers.Main) {
                dropdownCrop.applyCustomDropdownStyle(names)
                dropdownCrop.setOnItemClickListener { _, _, pos, _ ->
                    selectedCropId = allCrops[pos].cropId
                    clearPriceUI()
                    validateFetchButton()
                }
            }
        } catch (e: Exception) {
            Log.e("Market", "loadCrops failed: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshPriceData() {
        if (selectedMarketId == -1 || selectedCropId == -1) return

        lifecycleScope.launch {
            try {
                val today = LocalDate.now().toString()

                val results = SupabaseManager.client
                    .postgrest["crop_price"]
                    .select {
                        filter {
                            eq("market_id", selectedMarketId)
                            eq("crop_id", selectedCropId)
                            lte("price_date", today)
                        }
                        order("price_date", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<CropPriceRow>()

                if (results.isEmpty()) {
                    cardPrice.visibility = View.GONE
                    cardGraph.visibility = View.GONE
                    showNoData(true)
                    tvPriceValue.text = "₹ --"
                    tvMinPrice.text   = "₹ --"
                    tvMaxPrice.text   = "₹ --"
                    tvPriceUnit.text  = ""
                } else {
                    cardGraph.visibility = View.VISIBLE
                    cardPrice.visibility = View.VISIBLE
                    showNoData(false)
                    val row = results.first()
                    tvPriceValue.text = "₹ ${d(row.modalPrice.toInt())}"
                    tvMinPrice.text   = "₹ ${d(row.minPrice.toInt())}"
                    tvMaxPrice.text   = "₹ ${d(row.maxPrice.toInt())}"
                    val unit = row.priceUnit
                        .removePrefix("Rs./")
                        .removePrefix("rs./")
                        .trim()
                    tvPriceUnit.text = "/ ${t(unit)}"
                    tvPriceDate.text  = "${t("Price as of")} ${formatDateWithYear(row.priceDate)}"
                    if (row.arrivalQuantity > 0) {
                        tvQuantity.text = d(row.arrivalQuantity.toInt())
                    } else {
                        tvQuantity.text = t("N/A")
                        tvQuantityUnit.visibility = View.GONE
                    }

                    loadPriceHistory(toggleGroup.checkedButtonId == R.id.btnWeekly, isDistrictLevel = false)
                }
            } catch (e: Exception) {
                Log.e("Market", "refreshPriceData failed: ${e.message}")
                cardPrice.visibility = View.GONE
                showNoData(true)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadPriceHistory(isWeekly: Boolean, isDistrictLevel: Boolean = false) {
        if (selectedCropId == -1) return
        if (isDistrictLevel && allMarkets.isEmpty()) {
            showNoData(true)
            return
        }

        currentRequestId++
        val requestId = currentRequestId
        val endDate  = chartEndDate
        val fromDate = if (isWeekly) endDate.minusDays(6) else endDate.minusMonths(12)

        if (isWeekly) {
            tvDateRange.text = "${formatDateShort(fromDate.toString())} - ${formatDateShort(endDate.toString())}"
        } else {
            tvDateRange.text = "${d(fromDate.year)} - ${d(endDate.year)}"
        }

        btnNextPeriod.alpha     = if (endDate >= LocalDate.now()) 0.3f else 1.0f
        btnNextPeriod.isEnabled = endDate < LocalDate.now()

        priceHistoryJob?.cancel()

        lineChart.clear()
        lineChart.fitScreen()
        lineChart.invalidate()
        showNoData(false)

        priceHistoryJob = lifecycleScope.launch {
            try {
                val rows = if (isDistrictLevel) {
                    val marketIds = allMarkets.map { it.marketId }
                    if (marketIds.isEmpty()) return@launch
                    SupabaseManager.client.postgrest["crop_price"].select {
                        filter {
                            eq("crop_id", selectedCropId)
                            isIn("market_id", marketIds)
                            gte("price_date", fromDate.toString())
                            lte("price_date", endDate.toString())
                        }
                        order("price_date", Order.ASCENDING)
                    }.decodeList<CropPriceRow>()
                } else {
                    SupabaseManager.client.postgrest["crop_price"].select {
                        filter {
                            eq("crop_id", selectedCropId)
                            eq("market_id", selectedMarketId)
                            gte("price_date", fromDate.toString())
                            lte("price_date", endDate.toString())
                        }
                        order("price_date", Order.ASCENDING)
                    }.decodeList<CropPriceRow>()
                }

                if (requestId != currentRequestId) return@launch

                val filteredRows = rows.filter {
                    val date = LocalDate.parse(it.priceDate)
                    !date.isBefore(fromDate) && !date.isAfter(endDate)
                }

                if (filteredRows.isEmpty()) {
                    activeDistrictMarkets = emptyList()
                    districtPriceData = emptyMap()
                    showNoData(true)
                    return@launch
                }

                districtPriceData = filteredRows.groupBy { it.marketId }
                activeDistrictMarkets = if (isDistrictLevel) {
                    allMarkets.filter { districtPriceData.containsKey(it.marketId) }
                } else {
                    val m = allMarkets.find { it.marketId == selectedMarketId }
                    if (m != null) listOf(m) else emptyList()
                }

                if (activeDistrictMarkets.isEmpty()) {
                    showNoData(true)
                    return@launch
                }

                if (currentMarketIndex >= activeDistrictMarkets.size) {
                    currentMarketIndex = 0
                }
                drawChartForCurrentMarket(isWeekly)

            } catch (e: Exception) {
                if (requestId != currentRequestId) return@launch
                Log.e("Market", "loadPriceHistory failed: ${e.message}")
                showNoData(true)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun drawChartForCurrentMarket(isWeekly: Boolean) {
        if (activeDistrictMarkets.isEmpty()) {
            showNoData(true)
            return
        }

        val currentMarket = activeDistrictMarkets[currentMarketIndex]
        val marketRows = districtPriceData[currentMarket.marketId] ?: emptyList()

        val rawName = currentMarket.marketName
        val countText = "(${d(currentMarketIndex + 1)}/${d(activeDistrictMarkets.size)})"

        tvMarketName.text = "${t(rawName)} $countText"
        if (langCode != TranslateLanguage.ENGLISH && Regex("[a-zA-Z]").containsMatchIn(rawName)) {
            translateMarketJob?.cancel()
            translateMarketJob = lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(langCode)
                        .build()
                    val client = com.google.mlkit.nl.translate.Translation.getClient(options)
                    val translatedName = client.translate(rawName).await()
                    withContext(Dispatchers.Main) {
                        tvMarketName.text = "$translatedName $countText"
                    }
                    client.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (marketRows.isEmpty()) {
            showNoData(true)
            return
        }

        val grouped = if (isWeekly) {
            marketRows.groupBy { it.priceDate }.toSortedMap()
        } else {
            marketRows.groupBy { it.priceDate.substring(0, 7) }.toSortedMap()
        }

        val labels = ArrayList<String>()
        val entries = ArrayList<Entry>()
        var xIndex = 0f

        for ((dateStr, dayRows) in grouped) {
            if (isWeekly) {
                labels.add(formatDateShort(dateStr))
            } else {
                labels.add(formatMonthYear("$dateStr-01"))
            }
            val avg = dayRows.map { it.modalPrice }.average().toFloat()
            entries.add(Entry(xIndex, avg))
            xIndex++
        }

        val dataSet = LineDataSet(entries, "Price").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            color = "#52B788".toColorInt()
            setCircleColor("#2D6A4F".toColorInt())
            lineWidth = 3f
            circleRadius = 4f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = "#A8D5B5".toColorInt()
            fillAlpha = 60
        }

        lineChart.clear()
        lineChart.fitScreen()
        showNoData(false)
        updateChart(listOf(dataSet), labels, isWeekly)
    }

    private fun setupChartAppearance() {
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled      = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled         = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.isDoubleTapToZoomEnabled = false
        lineChart.clipToPadding = false

        lineChart.extraBottomOffset = 10f
        lineChart.extraLeftOffset = 0f
        lineChart.extraRightOffset = 5f
        lineChart.xAxis.labelRotationAngle = -45f
        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            textColor = "#555555".toColorInt()
            setAvoidFirstLastClipping(false)
        }

        lineChart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = "#E8EDE0".toColorInt()
            textColor = "#555555".toColorInt()
            spaceTop = 15f
            spaceBottom = 15f
            granularity = 1f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                    return d(value.toInt())
                }
            }
        }
        lineChart.axisRight.isEnabled = false

        val markerView = CustomMarkerView(this, R.layout.custom_marker_view)
        markerView.chartView = lineChart
        lineChart.marker = markerView
    }

    private fun updateChart(dataSets: List<ILineDataSet>, labels: ArrayList<String>, isWeekly: Boolean) {
        lineChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            granularity    = 1f
            labelCount     = labels.size
        }
        lineChart.legend.apply {
            isEnabled        = dataSets.size > 1
            isWordWrapEnabled = true
            textColor        = "#555555".toColorInt()
            textSize         = 12f
        }
        lineChart.data = LineData(dataSets)
        lineChart.setVisibleXRangeMaximum(if (isWeekly) 7f else 12f)
        lineChart.moveViewToX(labels.size.toFloat())
        lineChart.animateX(500)
        lineChart.invalidate()
    }

    private fun clearPriceUI() {
        priceHistoryJob?.cancel()
        cardPrice.visibility = View.GONE
        cardGraph.visibility = View.GONE
        tvNoDataMarket.visibility = View.GONE
        tvPriceValue.text = "₹ --"
        tvMinPrice.text   = "₹ --"
        tvMaxPrice.text   = "₹ --"
        tvPriceUnit.text  = ""
        tvQuantity.text = "--"
        tvQuantityUnit.visibility = View.VISIBLE
        tvPriceDate.text  = ""
        lineChart.clear()
        lineChart.fitScreen()
        lineChart.invalidate()
    }

    private fun showNoData(show: Boolean) {
        if (show && cardPrice.isVisible) {
            tvNoDataMarket.visibility = View.GONE
            lineChart.visibility = View.VISIBLE
            lineChart.clear()
            lineChart.invalidate()
        } else {
            tvNoDataMarket.visibility = if (show) View.VISIBLE else View.GONE
            lineChart.visibility = if (show) View.GONE else View.VISIBLE
            cardGraph.visibility = if (show) View.GONE else View.VISIBLE
        }
    }

    private fun formatDateShort(date: String): String {
        return try {
            val p = date.split("-")
            val m = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${d(p[2].toInt())} ${t(m[p[1].toInt()])}"
        } catch (_: Exception) { date }
    }

    private fun formatMonthYear(date: String): String {
        return try {
            val parts = date.split("-")
            val year = parts[0]
            val month = parts[1].toInt()
            val months = listOf("", "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${t(months[month])} ${d(year)}"
        } catch (_: Exception) {
            date
        }
    }

    private fun formatDateWithYear(date: String): String {
        return try {
            val p = date.split("-")
            val m = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${d(p[2].toInt())} ${t(m[p[1].toInt()])} ${d(p[0])}"
        } catch (_: Exception) { date }
    }
}