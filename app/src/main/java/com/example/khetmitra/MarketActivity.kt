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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
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

    private lateinit var dropdownState: AutoCompleteTextView
    private lateinit var dropdownDistrict: AutoCompleteTextView
    private lateinit var dropdownMarket: AutoCompleteTextView
    private lateinit var dropdownCrop: AutoCompleteTextView
    private lateinit var tvPriceValue: TextView
    private lateinit var tvMinPrice: TextView
    private lateinit var tvMaxPrice: TextView
    private lateinit var tvPriceUnit: TextView
    private lateinit var tvNoData: TextView
    private lateinit var toggleGroup: MaterialButtonToggleGroup

    // ─── Permission launcher ──────────────────────────────────────────────────
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchLocation()
        else Toast.makeText(this, "Location permission denied. Please select manually.", Toast.LENGTH_LONG).show()
    }

    // ─── onCreate ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market)

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
        tvNoData         = findViewById(R.id.tvNoData)
        toggleGroup      = findViewById(R.id.toggleGroup)
        lineChart        = findViewById(R.id.lineChart)

        setupChartAppearance()

        dropdownDistrict.isEnabled = false
        dropdownMarket.isEnabled   = false

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && selectedMarketId != -1 && selectedCropId != -1)
                loadPriceHistory(checkedId == R.id.btnWeekly)
        }

        lifecycleScope.launch {
            loadStates()
            loadCrops()
            requestLocationPermission()
        }
    }

    // ─── Location ────────────────────────────────────────────────────────────

    private fun requestLocationPermission() {
        val fine   = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val hasFine   = ContextCompat.checkSelfPermission(this, fine)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) fetchLocation()
        else locationPermissionLauncher.launch(arrayOf(fine, coarse))
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
                else Toast.makeText(this, "Could not get location. Select manually.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Location error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        try {
            val geocoder = Geocoder(this, Locale("en", "IN"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses.isNullOrEmpty()) {
                Toast.makeText(this, "Could not detect location. Select manually.", Toast.LENGTH_SHORT).show()
                return
            }
            val detectedState = addresses[0].adminArea?.trim() ?: ""
            Log.d("Location", "Detected state: $detectedState")
            if (detectedState.isNotEmpty()) autoSelectState(detectedState)
            else Toast.makeText(this, "State not detected. Select manually.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Location", "Geocoder failed: ${e.message}")
            Toast.makeText(this, "Geocoder error. Select manually.", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Auto-select ─────────────────────────────────────────────────────────

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
        dropdownState.setText(match.stateName, false)
        lifecycleScope.launch { loadDistricts(selectedStateId) }
    }

    // ─── Loaders ─────────────────────────────────────────────────────────────

    private suspend fun loadStates() {
        try {
            allStates = SupabaseManager.client
                .postgrest["states"]
                .select {
                    filter { eq("status", 1) }
                    order("state_name", Order.ASCENDING)
                }
                .decodeList<StateRow>()

            val names = allStates.map { it.stateName }
            dropdownState.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            )
            dropdownState.setOnItemClickListener { _, _, pos, _ ->
                selectedStateId    = allStates[pos].stateId
                selectedDistrictId = -1
                selectedMarketId   = -1
                dropdownDistrict.setText("", false)
                dropdownMarket.setText("", false)
                dropdownDistrict.isEnabled = false
                dropdownMarket.isEnabled   = false
                clearPriceUI()
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

            val names = allDistricts.map { it.districtName }
            dropdownDistrict.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            )
            dropdownDistrict.isEnabled = true

            dropdownDistrict.setOnItemClickListener { _, _, pos, _ ->
                selectedDistrictId = allDistricts[pos].districtId
                selectedMarketId   = -1
                dropdownMarket.setText("", false)
                dropdownMarket.isEnabled = false
                clearPriceUI()
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

            val names = allMarkets.map { it.marketName }
            dropdownMarket.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            )
            dropdownMarket.isEnabled = true

            dropdownMarket.setOnItemClickListener { _, _, pos, _ ->
                selectedMarketId = allMarkets[pos].marketId
                refreshPriceData()
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

            val names = allCrops.map { it.cropName }
            dropdownCrop.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            )
            dropdownCrop.setOnItemClickListener { _, _, pos, _ ->
                selectedCropId = allCrops[pos].cropId
                refreshPriceData()
            }
        } catch (e: Exception) {
            Log.e("Market", "loadCrops failed: ${e.message}")
        }
    }

    // ─── Price ───────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun refreshPriceData() {
        if (selectedMarketId == -1 || selectedCropId == -1) return
        lifecycleScope.launch {
            try {
                val results = SupabaseManager.client
                    .postgrest["crop_price"]
                    .select {
                        filter {
                            eq("market_id", selectedMarketId)
                            eq("crop_id", selectedCropId)
                        }
                        order("price_date", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<CropPriceRow>()

                if (results.isEmpty()) {
                    showNoData(true)
                } else {
                    showNoData(false)
                    val latest = results.first()
                    tvPriceValue.text = "₹ ${latest.modalPrice.toInt()}"
                    tvMinPrice.text   = "₹ ${latest.minPrice.toInt()}"
                    tvMaxPrice.text   = "₹ ${latest.maxPrice.toInt()}"
                    tvPriceUnit.text  = "/ ${latest.priceUnit}"
                    loadPriceHistory(toggleGroup.checkedButtonId == R.id.btnWeekly)
                }
            } catch (e: Exception) {
                Log.e("Market", "refreshPriceData failed: ${e.message}")
                showNoData(true)
            }
        }
    }

    private fun loadPriceHistory(isWeekly: Boolean) {
        if (selectedMarketId == -1 || selectedCropId == -1) return
        lifecycleScope.launch {
            try {
                val today    = LocalDate.now()
                val fromDate = if (isWeekly) today.minusDays(6) else today.minusDays(364)

                Log.d("Chart", "Fetching: market=$selectedMarketId crop=$selectedCropId from=$fromDate to=$today")

                var rows = SupabaseManager.client
                    .postgrest["crop_price"]
                    .select {
                        filter {
                            eq("market_id", selectedMarketId)
                            eq("crop_id", selectedCropId)
                            gte("price_date", fromDate.toString())
                            lte("price_date", today.toString())
                        }
                        order("price_date", Order.ASCENDING)
                        limit(if (isWeekly) 7 else 365)
                    }
                    .decodeList<CropPriceRow>()

                Log.d("Chart", "Rows in range: ${rows.size}")

                // Fallback: if no data in date range, load latest available records
                if (rows.isEmpty()) {
                    Log.w("Chart", "No data in range, falling back to latest records")
                    rows = SupabaseManager.client
                        .postgrest["crop_price"]
                        .select {
                            filter {
                                eq("market_id", selectedMarketId)
                                eq("crop_id", selectedCropId)
                            }
                            order("price_date", Order.DESCENDING)
                            limit(if (isWeekly) 7 else 365)
                        }
                        .decodeList<CropPriceRow>()
                        .reversed()
                    Log.d("Chart", "Fallback rows: ${rows.size}")
                }

                if (rows.isEmpty()) return@launch

                val entries = ArrayList<Entry>()
                val labels  = ArrayList<String>()

                if (isWeekly) {
                    rows.forEachIndexed { i, row ->
                        entries.add(Entry(i.toFloat(), row.modalPrice))
                        labels.add(formatDateShort(row.priceDate))
                    }
                } else {
                    rows.groupBy { it.priceDate.substring(0, 7) }
                        .entries.sortedBy { it.key }.takeLast(12)
                        .forEachIndexed { i, (_, rowList) ->
                            val avg = rowList.map { it.modalPrice }.average().toFloat()
                            entries.add(Entry(i.toFloat(), avg))
                            labels.add(formatMonthShort(rowList.first().priceDate))
                        }
                }

                Log.d("Chart", "Rendering ${entries.size} points")
                showNoData(false)  // ensure chart is visible before drawing
                updateChart(entries, labels)

            } catch (e: Exception) {
                Log.e("Market", "loadPriceHistory failed: ${e.message}")
            }
        }
    }

    // ─── Chart ───────────────────────────────────────────────────────────────

    private fun setupChartAppearance() {
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(false)
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
    }

    private fun updateChart(entries: ArrayList<Entry>, labels: ArrayList<String>) {
        lineChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            granularity = 1f
            labelCount  = labels.size
        }
        val dataSet = LineDataSet(entries, "Price").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            color = "#52B788".toColorInt()
            lineWidth = 3f
            setDrawCircles(true)
            setCircleColor("#2D6A4F".toColorInt())
            circleRadius = 4f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = "#A8D5B5".toColorInt()
            fillAlpha = 60
        }
        lineChart.data = LineData(dataSet)
        lineChart.animateX(500)
        lineChart.invalidate()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun clearPriceUI() {
        tvPriceValue.text = "₹ --"
        tvMinPrice.text   = "₹ --"
        tvMaxPrice.text   = "₹ --"
        tvPriceUnit.text  = ""
        lineChart.clear()
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
            "${p[2].toInt()} ${m[p[1].toInt()]}"
        } catch (_: Exception) { date }
    }

    private fun formatMonthShort(date: String): String {
        return try {
            val m = listOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            m[date.split("-")[1].toInt()]
        } catch (_: Exception) { date }
    }
}