package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

@SuppressLint("ViewConstructor")
class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvMarket: TextView = findViewById(R.id.tvMarkerMarket)
    private val tvPrice: TextView = findViewById(R.id.tvMarkerPrice)
    private val tvDate: TextView = findViewById(R.id.tvMarkerDate)

    @SuppressLint("SetTextI18n")
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null || highlight == null) return

        val dataSetIndex = highlight.dataSetIndex
        val dataSet = chartView.data.getDataSetByIndex(dataSetIndex)
        tvMarket.text = dataSet.label ?: "Unknown Market"

        tvPrice.text = "₹ ${e.y.toInt()}"

        val dateStr = chartView.xAxis.valueFormatter?.getFormattedValue(e.x) ?: ""
        tvDate.text = dateStr

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 15f)
    }
}