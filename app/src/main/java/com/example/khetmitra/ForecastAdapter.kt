package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ForecastAdapter(private val forecastList: List<ForecastModel>) :
    RecyclerView.Adapter<ForecastAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tvDayName)
        val tvTemp: TextView = itemView.findViewById(R.id.tvDayTemp)
        val imgIcon: ImageView = itemView.findViewById(R.id.imgWeatherIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast_day, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = forecastList[position]
        holder.tvDay.text = item.dayName
        holder.tvTemp.text = item.dayTemp
        holder.imgIcon.setImageResource(item.weatherIcon)

        holder.tvDay.tag = null
        holder.tvTemp.tag = null

        val prefs = holder.itemView.context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        val targetLang = prefs.getString("Language", com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
            ?: com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH

        // Only run translation if the target language is NOT English
        if (targetLang != com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH) {
            // Translate the Day Name
            TranslationHelper.translateViewHierarchy(holder.tvDay, targetLang) { }
            // Translate the Temp (if it contains text like 'Sunny' or 'Cloudy')
            TranslationHelper.translateViewHierarchy(holder.tvTemp, targetLang) { }
        }
    }

    override fun getItemCount(): Int {
        return forecastList.size
    }
}