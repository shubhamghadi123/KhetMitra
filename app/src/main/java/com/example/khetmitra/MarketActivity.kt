package com.example.khetmitra

import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView

class MarketActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private var currentBasePrice = 2500f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market)

        // Find Back Button (It's a MaterialCardView now, not an ImageView!)
        findViewById<MaterialCardView>(R.id.btnBack).setOnClickListener { finish() }

        // Find UI Elements
        val dropdownState = findViewById<AutoCompleteTextView>(R.id.dropdownState)
        val dropdownDistrict = findViewById<AutoCompleteTextView>(R.id.dropdownDistrict)
        val dropdownMarket = findViewById<AutoCompleteTextView>(R.id.dropdownMarket)
        val dropdownCrop = findViewById<AutoCompleteTextView>(R.id.dropdownCrop)

        val tvPriceValue = findViewById<TextView>(R.id.tvPriceValue)
        val tvMinPrice = findViewById<TextView>(R.id.tvMinPrice)
        val tvMaxPrice = findViewById<TextView>(R.id.tvMaxPrice)

        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleGroup)
        lineChart = findViewById(R.id.lineChart)

        setupChartAppearance()

        // 1. Setup Dummy Data
        val states = arrayOf("Maharashtra", "Gujarat", "Madhya Pradesh")
        val districtsMap = mapOf(
            "Maharashtra" to arrayOf("Pune", "Nashik", "Latur", "Nagpur"),
            "Gujarat" to arrayOf("Surat", "Rajkot", "Ahmedabad"),
            "Madhya Pradesh" to arrayOf("Indore", "Bhopal", "Ujjain")
        )
        val marketsMap = mapOf(
            "Pune" to arrayOf("Pune APMC", "Baramati", "Manchar"),
            "Nashik" to arrayOf("Lasalgaon APMC", "Pimpalgaon"),
            "Nagpur" to arrayOf("Nagpur APMC", "Kalmeshwar")
        )
        val crops = arrayOf("Wheat", "Cotton", "Onion", "Soybean", "Maize", "Tomato")

        dropdownState.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, states))
        dropdownCrop.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, crops))

        // 2. The Main Update Function
        fun refreshData() {
            // Check if user has selected a crop to make the prediction text dynamic
            val selectedCrop = dropdownCrop.text.toString().ifEmpty { "the crop" }

            // Generate Random Price
            currentBasePrice = (1500..4500).random().toFloat()
            val minP = (currentBasePrice - (100..300).random()).toInt()
            val maxP = (currentBasePrice + (100..400).random()).toInt()

            // Update Text
            tvPriceValue.text = "₹ ${currentBasePrice.toInt()}"
            tvMinPrice.text = "₹ $minP"
            tvMaxPrice.text = "₹ $maxP"

            // Reload Graph
            val isWeekly = toggleGroup.checkedButtonId == R.id.btnWeekly
            loadChartData(isWeekly)
        }

        // 3. Setup Listeners
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) loadChartData(checkedId == R.id.btnWeekly)
        }

        dropdownState.setOnItemClickListener { _, _, position, _ ->
            val districts = districtsMap[states[position]] ?: emptyArray()
            dropdownDistrict.setText("", false)
            dropdownMarket.setText("", false)
            dropdownDistrict.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, districts))
            refreshData()
        }

        dropdownDistrict.setOnItemClickListener { _, _, _, _ ->
            val markets = marketsMap[dropdownDistrict.text.toString()] ?: arrayOf("Main APMC", "Rural Mandi")
            dropdownMarket.setText("", false)
            dropdownMarket.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, markets))
            refreshData()
        }

        dropdownMarket.setOnItemClickListener { _, _, _, _ -> refreshData() }
        dropdownCrop.setOnItemClickListener { _, _, _, _ -> refreshData() }

        // Initial Load
        refreshData()
    }

    // --- GRAPH SETUP FUNCTIONS ---

    private fun setupChartAppearance() {
        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(false)

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.parseColor("#555555")

        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#E8EDE0")
        leftAxis.textColor = Color.parseColor("#555555")

        lineChart.axisRight.isEnabled = false
    }

    private fun loadChartData(isWeekly: Boolean) {
        val entries = ArrayList<com.github.mikephil.charting.data.Entry>()
        val labels = ArrayList<String>()

        if (isWeekly) {
            val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            for (i in 0..6) {
                val variance = (-200..200).random()
                entries.add(com.github.mikephil.charting.data.Entry(i.toFloat(), currentBasePrice + variance))
                labels.add(days[i])
            }
            lineChart.xAxis.labelCount = 7
        } else {
            val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            for (i in 0..11) {
                val variance = (-600..800).random()
                entries.add(com.github.mikephil.charting.data.Entry(i.toFloat(), currentBasePrice + variance))
                labels.add(months[i])
            }
            lineChart.xAxis.setLabelCount(12, false)
        }

        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        lineChart.xAxis.granularity = 1f

        val dataSet = LineDataSet(entries, "Price")
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.color = Color.parseColor("#52B788") // Updated to match your new green theme
        dataSet.lineWidth = 3f
        dataSet.setDrawCircles(true)
        dataSet.setCircleColor(Color.parseColor("#2D6A4F")) // Darker green for dots
        dataSet.circleRadius = 4f
        dataSet.setDrawValues(false)

        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#A8D5B5")
        dataSet.fillAlpha = 60

        lineChart.data = LineData(dataSet)
        lineChart.animateX(500)
        lineChart.invalidate()
    }
}