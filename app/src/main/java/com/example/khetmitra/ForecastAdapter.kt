package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ForecastAdapter(private val forecastList: List<ForecastModel>) :
    RecyclerView.Adapter<ForecastAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tvDayName)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val lottieIcon: com.airbnb.lottie.LottieAnimationView = itemView.findViewById(R.id.ivWeatherIcon)
        val tvHigh: TextView = itemView.findViewById(R.id.tvHighTemp)
        val tvLow: TextView = itemView.findViewById(R.id.tvLowTemp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast_day, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = forecastList[position]

        val prefs = holder.itemView.context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        val targetLang = prefs.getString("Language", com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
            ?: com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH

        holder.lottieIcon.cancelAnimation()
        holder.lottieIcon.progress = 0f

        val cacheKey = "weather_${item.icon}"

        com.airbnb.lottie.LottieCompositionFactory.fromRawRes(holder.itemView.context, item.icon, cacheKey)
            .addListener { composition ->
                holder.lottieIcon.setComposition(composition)
            }
        holder.lottieIcon.setOnClickListener {
            holder.lottieIcon.playAnimation()
        }

        if (targetLang == com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH) {
            holder.tvDay.text = item.day
            holder.tvDate.text = item.date
            holder.tvHigh.text = item.highTemp
            holder.tvLow.text = item.lowTemp
        } else {
            holder.tvHigh.text = TranslationHelper.convertDigits(item.highTemp, targetLang)
            holder.tvLow.text = TranslationHelper.convertDigits(item.lowTemp, targetLang)

            val manualDay = TranslationHelper.getManualTranslation(item.day, targetLang)
            if (manualDay != null) {
                holder.tvDay.text = manualDay
            } else {
                holder.tvDay.text = item.day
                TranslationHelper.translateViewHierarchy(holder.tvDay, targetLang) {}
            }
            holder.tvDate.text = TranslationHelper.convertDigits(item.date, targetLang)
            TranslationHelper.translateViewHierarchy(holder.tvDate, targetLang) {}
        }
    }

    override fun getItemCount() = forecastList.size
}