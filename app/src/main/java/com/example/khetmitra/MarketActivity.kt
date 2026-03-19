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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
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
import kotlinx.coroutines.launch
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
    private lateinit var tvPriceDate: TextView
    private lateinit var tvNoData: TextView
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnFetchPrice: MaterialButton
    private lateinit var cardPrice: MaterialCardView
    private lateinit var cardGraph: MaterialCardView
    private lateinit var btnPrevPeriod: ImageView
    private lateinit var btnNextPeriod: ImageView
    private lateinit var tvDateRange: TextView
    private var chartEndDate: LocalDate = LocalDate.now()
    private var langCode: String = TranslateLanguage.ENGLISH

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
        tvPriceDate      = findViewById(R.id.tvPriceDate)
        tvNoData         = findViewById(R.id.tvNoData)
        toggleGroup      = findViewById(R.id.toggleGroup)
        lineChart        = findViewById(R.id.lineChart)
        btnFetchPrice    = findViewById(R.id.btnFetchPrice)
        cardPrice        = findViewById(R.id.cardPrice)
        cardGraph        = findViewById(R.id.cardGraph)
        btnPrevPeriod = findViewById(R.id.btnPrevPeriod)
        btnNextPeriod = findViewById(R.id.btnNextPeriod)
        tvDateRange   = findViewById(R.id.tvDateRange)

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
            if (selectedMarketId == -1) {
                isDistrictMode = true
                cardPrice.visibility = View.GONE
                cardGraph.visibility = View.VISIBLE
                loadPriceHistory(toggleGroup.checkedButtonId == R.id.btnWeekly, isDistrictLevel = true)
            } else {
                isDistrictMode = false
                cardPrice.visibility = View.VISIBLE
                cardGraph.visibility = View.VISIBLE
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
        if (isReady) {
            btnFetchPrice.setBackgroundColor("#2D6A4F".toColorInt())
        } else {
            btnFetchPrice.setBackgroundColor("#A8D5B5".toColorInt())
        }
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val cts = CancellationTokenSource()
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) reverseGeocode(location.latitude, location.longitude)
                else Toast.makeText(this, t("Could not get location. Select manually."), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Location error: ${it.message}", Toast.LENGTH_SHORT).show()
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
            val names = allDistricts.map { t(it.districtName) }
            dropdownDistrict.applyCustomDropdownStyle(names)
            dropdownDistrict.isEnabled = true
            dropdownDistrict.setOnItemClickListener { _, _, pos, _ ->
                selectedDistrictId = allDistricts[pos].districtId
                selectedMarketId   = -1
                dropdownMarket.setText("", false)
                dropdownMarket.isEnabled = false
                clearPriceUI()
                validateFetchButton()
                lifecycleScope.launch { loadMarkets(selectedDistrictId) }
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
            val names = allMarkets.map { t(it.marketName) }
            dropdownMarket.applyCustomDropdownStyle(names)
            dropdownMarket.isEnabled = true
            dropdownMarket.setOnItemClickListener { _, _, pos, _ ->
                selectedMarketId = allMarkets[pos].marketId
                clearPriceUI()
                validateFetchButton()
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
                    filter { eq("status", 1)}
                    filter { eq("crop_group_id", 1)}
                    order("crop_name", Order.ASCENDING)
                }
                .decodeList<CropRow>()

            val names = allCrops.map { crop ->
                val rawName = crop.cropName
                if (rawName.contains("(") && rawName.contains(")")) {
                    val base = rawName.substringBefore("(")
                    val variety = rawName.substringAfter("(").substringBefore(")")
                    "${t(base.trim())} (${t(variety.trim())})"
                } else {
                    t(rawName)
                }
            }
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
                val yesterday = LocalDate.now().minusDays(1).toString()

                val results = SupabaseManager.client
                    .postgrest["crop_price"]
                    .select {
                        filter {
                            eq("market_id", selectedMarketId)
                            eq("crop_id", selectedCropId)
                            lte("price_date", yesterday)
                        }
                        order("price_date", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<CropPriceRow>()

                if (results.isEmpty()) {
                    showNoData(true)
                    tvPriceValue.text = "₹ --"
                    tvMinPrice.text   = "₹ --"
                    tvMaxPrice.text   = "₹ --"
                    tvPriceUnit.text  = ""
                } else {
                    showNoData(false)
                    val targetDayData = results.first()
                    tvPriceValue.text = "₹ ${d(targetDayData.modalPrice.toInt())}"
                    tvMinPrice.text   = "₹ ${d(targetDayData.minPrice.toInt())}"
                    tvMaxPrice.text   = "₹ ${d(targetDayData.maxPrice.toInt())}"
                    tvPriceUnit.text  = "/ ${t(targetDayData.priceUnit)}"
                    val formattedDate = formatDateWithYear(targetDayData.priceDate)
                    tvPriceDate.text = "${t("Price as of")} $formattedDate"

                    loadPriceHistory(toggleGroup.checkedButtonId == R.id.btnWeekly, isDistrictLevel = false)
                }
            } catch (e: Exception) {
                Log.e("Market", "refreshPriceData failed: ${e.message}")
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

        val endDate = chartEndDate
        val fromDate = if (isWeekly) endDate.minusDays(6) else endDate.minusMonths(12)

        tvDateRange.text = "${formatDateShort(fromDate.toString())} - ${formatDateShort(endDate.toString())}"

        if (endDate.isEqual(LocalDate.now()) || endDate.isAfter(LocalDate.now())) {
            btnNextPeriod.alpha = 0.3f
            btnNextPeriod.isEnabled = false
        } else {
            btnNextPeriod.alpha = 1.0f
            btnNextPeriod.isEnabled = true
        }

        lifecycleScope.launch {
            try {
                var rows = SupabaseManager.client
                    .postgrest["crop_price"]
                    .select {
                        filter {
                            eq("crop_id", selectedCropId)
                            if (isDistrictLevel) {
                                isIn("market_id", allMarkets.map { it.marketId })
                            } else {
                                eq("market_id", selectedMarketId)
                            }
                            gte("price_date", fromDate.toString())
                            lte("price_date", endDate.toString())
                        }
                        order("price_date", Order.ASCENDING)
                    }
                    .decodeList<CropPriceRow>()

                if (rows.isEmpty() && !isDistrictLevel) {
                    rows = SupabaseManager.client
                        .postgrest["crop_price"]
                        .select {
                            filter {
                                eq("market_id", selectedMarketId)
                                eq("crop_id", selectedCropId)
                                lte("price_date", endDate.toString())
                            }
                            order("price_date", Order.DESCENDING)
                            limit(if (isWeekly) 7 else 365)
                        }
                        .decodeList<CropPriceRow>()
                        .reversed()
                }

                if (rows.isEmpty()) {
                    showNoData(true)
                    return@launch
                }

                val groupedByDateStr = if (isWeekly) {
                    rows.groupBy { it.priceDate }.toSortedMap()
                } else {
                    rows.groupBy { it.priceDate.substring(0, 7) }.toSortedMap()
                }

                val labels = ArrayList<String>()
                val dateToIndexMap = HashMap<String, Float>()
                var xIndex = 0f

                for ((dateStr, _) in groupedByDateStr) {
                    labels.add(if (isWeekly) formatDateShort(dateStr) else formatMonthShort("$dateStr-01"))
                    dateToIndexMap[dateStr] = xIndex
                    xIndex++
                }

                val dataSets = ArrayList<ILineDataSet>()

                if (isDistrictLevel) {
                    val rowsByMarket = rows.groupBy { it.marketId }
                    val colors = listOf("#52B788", "#1E6091", "#D9ED92", "#184E77", "#34A0A4", "#76C893")
                    var colorIdx = 0

                    for ((marketId, marketRows) in rowsByMarket) {
                        val entries = ArrayList<Entry>()
                        val marketGroupedByDate = if (isWeekly) marketRows.groupBy { it.priceDate } else marketRows.groupBy { it.priceDate.substring(0, 7) }

                        for ((dateStr, dayRows) in marketGroupedByDate) {
                            val avgPrice = dayRows.map { it.modalPrice }.average().toFloat()
                            val mappedX = dateToIndexMap[dateStr] ?: 0f
                            entries.add(Entry(mappedX, avgPrice))
                        }

                        val marketName = allMarkets.find { it.marketId == marketId }?.marketName ?: "Unknown"

                        val dataSet = LineDataSet(entries, t(marketName)).apply {
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                            val lineColor = colors[colorIdx % colors.size].toColorInt()
                            color = lineColor
                            setCircleColor(lineColor)
                            lineWidth = 2.5f
                            circleRadius = 4f
                            setDrawValues(false)
                            setDrawFilled(false)
                        }
                        dataSets.add(dataSet)
                        colorIdx++
                    }
                } else {
                    val entries = ArrayList<Entry>()
                    for ((dateStr, dayRows) in groupedByDateStr) {
                        val avgPrice = dayRows.map { it.modalPrice }.average().toFloat()
                        val mappedX = dateToIndexMap[dateStr] ?: 0f
                        entries.add(Entry(mappedX, avgPrice))
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
                    dataSets.add(dataSet)
                }
                showNoData(false)
                lineChart.fitScreen()
                updateChart(dataSets, labels, isWeekly)
            } catch (e: Exception) {
                Log.e("Market", "loadPriceHistory failed: ${e.message}")
                showNoData(true)
            }
        }
    }

    private fun setupChartAppearance() {
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false

        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.isDoubleTapToZoomEnabled = false

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            textColor = "#555555".toColorInt()
        }
        lineChart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = "#E8EDE0".toColorInt()
            textColor = "#555555".toColorInt()
        }
        lineChart.axisRight.isEnabled = false

        val markerView = CustomMarkerView(this, R.layout.custom_marker_view)
        markerView.chartView = lineChart
        lineChart.marker = markerView
    }

    private fun updateChart(dataSets: List<ILineDataSet>, labels: ArrayList<String>, isWeekly: Boolean) {
        lineChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            granularity = 1f
            labelCount  = labels.size
        }

        lineChart.legend.apply {
            isEnabled = dataSets.size > 1
            isWordWrapEnabled = true
            textColor = "#555555".toColorInt()
            textSize = 12f
        }

        lineChart.data = LineData(dataSets)

        if (isWeekly) {
            lineChart.setVisibleXRangeMaximum(7f)
        } else {
            lineChart.setVisibleXRangeMaximum(12f)
        }

        lineChart.moveViewToX(labels.size.toFloat())
        lineChart.animateX(500)
        lineChart.invalidate()
    }

    private fun clearPriceUI() {
        cardPrice.visibility = View.GONE
        cardGraph.visibility = View.GONE
        tvPriceValue.text = "₹ --"
        tvMinPrice.text   = "₹ --"
        tvMaxPrice.text   = "₹ --"
        tvPriceUnit.text  = ""
        tvPriceDate.text  = ""
        lineChart.clear()
        lineChart.fitScreen()
        lineChart.invalidate()
        showNoData(false)
    }

    private fun showNoData(show: Boolean) {
        tvNoData.visibility  = if (show) View.VISIBLE else View.GONE
        lineChart.visibility = if (show) View.GONE    else View.VISIBLE
    }

    private fun formatDateShort(date: String): String {
        return try {
            val p = date.split("-")
            val m = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${d(p[2].toInt())} ${t(m[p[1].toInt()])}"
        } catch (_: Exception) { date }
    }

    private fun formatMonthShort(date: String): String {
        return try {
            val m = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            t(m[date.split("-")[1].toInt()])
        } catch (_: Exception) { date }
    }

    private fun formatDateWithYear(date: String): String {
        return try {
            val p = date.split("-")
            val m = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            "${d(p[2].toInt())} ${t(m[p[1].toInt()])} ${d(p[0])}"
        } catch (_: Exception) { date }
    }
}