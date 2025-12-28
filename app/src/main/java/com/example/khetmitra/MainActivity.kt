package com.example.khetmitra

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dashboardItems = listOf(
            DashboardModel("Weather", "Sunny, 24°C", iconDrawable = R.drawable.ic_weather),
            DashboardModel("Plans", "3 tasks for today", iconDrawable = R.drawable.ic_plans),
            DashboardModel("Chat", "2 new messages", iconDrawable = R.drawable.ic_chat),
            DashboardModel("Market", "Up by 10%", iconDrawable = R.drawable.ic_market)
        )

        val languageSpinner = findViewById<Spinner>(R.id.languageSpinner)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.languages,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        languageSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedLang = parent?.getItemAtPosition(position).toString()

                // if they select "Hi" (Hindi)
                if (selectedLang == "Hindi") {
                    // TODO: Call your function to change app language
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // Do nothing
            }
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DashboardAdapter(dashboardItems)
    }
}