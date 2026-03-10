package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.GravityCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : BaseActivity() {

    private lateinit var adapter: DashboardAdapter
    private val dashboardItems = ArrayList<DataModels>()
    private var currentLangCode = TranslateLanguage.ENGLISH
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Default to Mumbai coordinates if GPS fails
    private val DEFAULT_CITY = "19.07,72.87"

    private data class CardTheme(
        val bgColor: Int,
        val accentColor: Int,
        val emoji: String,
        val tag: String
    )

    private val cardThemes = listOf(
        CardTheme("#FFF7ED".toColorInt(), "#F97316".toColorInt(), "🌤️", "Live"),       // Weather
        CardTheme("#F0FDF4".toColorInt(), "#22C55E".toColorInt(), "📋", "Today"),      // Plans
        CardTheme("#F0F9FF".toColorInt(), "#0EA5E9".toColorInt(), "💬", "New"),        // Chat
        CardTheme("#FAF5FF".toColorInt(), "#A855F7".toColorInt(), "📈", "Updated"),    // Market
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val profileCard = findViewById<androidx.cardview.widget.CardView>(R.id.profileCard)
        val btnStartMapping = findViewById<MaterialButton>(R.id.btnStartMapping)

        val navView = findViewById<NavigationView>(R.id.navView)
        val logoutItem = navView.menu.findItem(R.id.nav_logout)
        val logoutColor = ColorStateList.valueOf("#EF4444".toColorInt())
        val logoutTitle = SpannableString(logoutItem.title)
        logoutTitle.setSpan(ForegroundColorSpan("#EF4444".toColorInt()), 0, logoutTitle.length, 0)
        logoutItem.title = logoutTitle
        logoutItem.iconTintList = logoutColor

        profileCard.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val shouldOpenMap = intent.getBooleanExtra("OPEN_MAP_FRAGMENT", false)
        if (shouldOpenMap) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, FieldMeasurementFragment())
                .addToBackStack(null)
                .commit()
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_manage_fields -> startActivity(Intent(this, ManageFieldsActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_help -> { /* TODO */ }
                R.id.nav_logout -> {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            SupabaseManager.client.auth.signOut()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, t("Logged out successfully"), Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, t("Error logging out: ") + e.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        btnStartMapping.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, FieldMeasurementFragment())
                .addToBackStack(null)
                .commit()
        }

        TranslationHelper.initTranslations(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        translateNavigationDrawer()
        setupInitialData()
        fetchAndDisplayFarmerName()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        val spacingInPixels = (5 * resources.displayMetrics.density).toInt()
        recyclerView.addItemDecoration(VerticalSpacingItemDecoration(spacingInPixels))

        adapter = DashboardAdapter(dashboardItems) { selectedItem ->
            val title = selectedItem.title
            when {
                title == t("Weather") || title == "Weather" ->
                    startActivity(Intent(this, WeatherActivity::class.java))
                title == t("Market") || title == "Market" ->
                    startActivity(Intent(this, MarketActivity::class.java))
                title == t("Chat") || title == "Chat" ->
                    startActivity(Intent(this, ChatbotActivity::class.java))
            }
        }
        recyclerView.adapter = adapter
        checkLocationPermissionAndFetch()
    }

    private fun setupInitialData() {
        dashboardItems.clear()

        val weatherSubtitle = "${t("Loading")}..."
        val plansSubtitle = "${d("3")} ${t("tasks for today")}"
        val chatSubtitle = "${d("2")} ${t("new messages")}"
        val marketSubtitle = "${t("Up by")} ${d("10")}%"

        dashboardItems.add(
            DataModels(
                title = t("Weather"),
                subtitle = weatherSubtitle,
                iconRes = R.drawable.ic_weather,
                tag = t("Live"),
                bgColor = cardThemes[0].bgColor,
                accentColor = cardThemes[0].accentColor,
                emoji = cardThemes[0].emoji
            )
        )
        dashboardItems.add(
            DataModels(
                title = t("Plans"),
                subtitle = plansSubtitle,
                iconRes = R.drawable.ic_plans,
                tag = t("Today"),
                bgColor = cardThemes[1].bgColor,
                accentColor = cardThemes[1].accentColor,
                emoji = cardThemes[1].emoji
            )
        )
        dashboardItems.add(
            DataModels(
                title = t("Chat"),
                subtitle = chatSubtitle,
                iconRes = R.drawable.ic_chat,
                tag = t("New"),
                bgColor = cardThemes[2].bgColor,
                accentColor = cardThemes[2].accentColor,
                emoji = cardThemes[2].emoji
            )
        )
        dashboardItems.add(
            DataModels(
                title = t("Market"),
                subtitle = marketSubtitle,
                iconRes = R.drawable.ic_market,
                tag = t("Updated"),
                bgColor = cardThemes[3].bgColor,
                accentColor = cardThemes[3].accentColor,
                emoji = cardThemes[3].emoji
            )
        )
    }

    @SuppressLint("SetTextI18n")
    private fun fetchAndDisplayFarmerName() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = SupabaseManager.client.auth.currentUserOrNull()?.id

                if (userId == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Debug: User ID is null! Session lost.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val profile = SupabaseManager.client.postgrest["farmers"]
                    .select { filter { eq("id", userId) } }
                    .decodeSingleOrNull<FarmerProfile>()

                withContext(Dispatchers.Main) {
                    if (profile != null) {
                        val tvWelcomeMessage = findViewById<TextView>(R.id.tvWelcome)
                        val tvUsername = findViewById<TextView>(R.id.tvUsername)

                        if (tvUsername == null) {
                            Toast.makeText(this@MainActivity, "Debug: tvUsername ID not found in XML!", Toast.LENGTH_LONG).show()
                        }

                        val welcomeText = t("Welcome Back")
                        tvWelcomeMessage?.text = "$welcomeText,"

                        translateWithMLKit("${profile.first_name}!") { translatedFirstName ->
                            tvUsername?.text = translatedFirstName
                        }

                        val navView = findViewById<NavigationView>(R.id.navView)
                        val headerView = navView?.getHeaderView(0)
                        val tvHeaderName = headerView?.findViewById<TextView>(R.id.navUserName)
                        translateWithMLKit(profile.first_name) { translatedFullName ->
                            tvHeaderName?.text = translatedFullName
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Debug: Profile is NULL. Database blocked the read!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Debug DB Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("MainActivity", "Failed to fetch farmer name: ${e.message}")
            }
        }
    }

    private fun translateNavigationDrawer() {
        if (currentLangCode == TranslateLanguage.ENGLISH) return

        val navView = findViewById<NavigationView>(R.id.navView) ?: return

        if (navView.headerCount > 0) {
            val headerView = navView.getHeaderView(0)
            TranslationHelper.translateViewHierarchy(headerView, currentLangCode) {}
        }

        val menu = navView.menu
        for (i in 0 until menu.size) {
            val item = menu[i]
            if (item.title != null) item.title = t(item.title.toString())
            if (item.hasSubMenu()) {
                val subMenu = item.subMenu
                if (subMenu != null) {
                    for (j in 0 until subMenu.size) {
                        val subItem = subMenu[j]
                        if (subItem.title != null) subItem.title = t(subItem.title.toString())
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationPermissionAndFetch()
    }

    @Deprecated("Use OnBackPressedDispatcher instead")
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getUserLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) getUserLocation() else fetchWeather(DEFAULT_CITY)
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                fetchWeather("${location.latitude},${location.longitude}")
            } else {
                fetchWeather(DEFAULT_CITY)
            }
        }.addOnFailureListener {
            fetchWeather(DEFAULT_CITY)
        }
    }

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    private fun d(num: String): String {
        return TranslationHelper.convertDigits(num, currentLangCode)
    }

    private fun isFahrenheit(prefs: android.content.SharedPreferences): Boolean {
        val tempUnitPref = prefs.getString("TempUnit", "Celsius (°C)") ?: "Celsius (°C)"
        return tempUnitPref.contains("Fahrenheit")
    }

    private fun convertTemp(celsius: Double, isFahrenheit: Boolean): Int {
        return if (isFahrenheit) ((celsius * 9 / 5) + 32).toInt() else celsius.toInt()
    }

    private fun fetchWeather(query: String) {
        var lat: Double
        var lon: Double

        try {
            val parts = query.split(",")
            lat = parts[0].trim().toDouble()
            lon = parts[1].trim().toDouble()
        } catch (_: Exception) {
            lat = 19.07
            lon = 72.87
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)
        service.getForecast(lat, lon).enqueue(object : Callback<OpenMeteoResponse> {
            override fun onResponse(call: Call<OpenMeteoResponse>, response: Response<OpenMeteoResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
                    val useFahrenheit = isFahrenheit(prefs)
                    val tempSymbol = if (useFahrenheit) t("°F") else t("°C")
                    val currentTempRaw = data.current.temperature_2m
                    val tempText = convertTemp(currentTempRaw, useFahrenheit).toString()
                    val weatherCode = data.current.weathercode
                    val isDay = data.current.is_day
                    val rawCondition = getConditionText(weatherCode)
                    val iconRes = getIconForCondition(rawCondition, isDay)
                    val manualTranslation = TranslationHelper.getManualTranslation(rawCondition, currentLangCode)

                    if (manualTranslation != null) {
                        updateWeatherCard(manualTranslation, tempText, tempSymbol, iconRes)
                    } else {
                        translateWithMLKit(rawCondition) { translatedText ->
                            updateWeatherCard(translatedText, tempText, tempSymbol, iconRes)
                        }
                    }
                }
            }

            override fun onFailure(call: Call<OpenMeteoResponse>, t: Throwable) {
                Log.e("MainActivity", "Weather fetch failed: ${t.message}", t)
            }
        })
    }

    private fun getConditionText(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Cloudy"
            45 -> "Mist"
            48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Sleet"
            61, 63, 65 -> "Rain"
            66, 67 -> "Sleet"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain"
            85, 86 -> "Snow"
            95 -> "Thunderstorm"
            96, 99 -> "Hail"
            else -> "Unknown"
        }
    }

    private fun updateWeatherCard(condition: String, temp: String, unitSymbol: String, iconRes: Int) {
        val newSubtitle = "$condition, ${d(temp)}$unitSymbol"
        if (dashboardItems.isNotEmpty()) {
            val existing = dashboardItems[0]
            dashboardItems[0] = existing.copy(
                subtitle = newSubtitle,
                iconRes = iconRes
            )
            adapter.notifyItemChanged(0)
        }
    }

    private fun translateWithMLKit(text: String, callback: (String) -> Unit) {
        if (currentLangCode == TranslateLanguage.ENGLISH) {
            callback(text)
            return
        }
        val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(currentLangCode)
            .build()
        val client = com.google.mlkit.nl.translate.Translation.getClient(options)
        client.downloadModelIfNeeded().addOnSuccessListener {
            client.translate(text).addOnSuccessListener { result ->
                callback(result)
            }.addOnFailureListener { callback(text) }
        }.addOnFailureListener { callback(text) }
    }

    private fun getIconForCondition(conditionRaw: String, isDay: Int = 1): Int {
        val text = conditionRaw.lowercase()
        return when {
            text.contains("clear") || text.contains("sunny") -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
            text.contains("partly") -> if (isDay == 1) R.raw.partly_cloudy_day else R.raw.partly_cloudy_night
            text.contains("cloudy") -> R.raw.cloudy
            text.contains("overcast") -> R.raw.overcast
            text.contains("mist") -> R.raw.mist
            text.contains("fog") -> R.raw.fog
            text.contains("haze") -> R.raw.haze
            text.contains("dust") -> R.raw.dust
            text.contains("drizzle") -> R.raw.drizzle
            text.contains("sleet") -> R.raw.sleet
            text.contains("thunder") && text.contains("rain") -> R.raw.thunderstorms_rain
            text.contains("hail") -> R.raw.hail
            text.contains("thunder") -> R.raw.thunderstorms
            text.contains("rain") -> R.raw.rain
            text.contains("snow") -> R.raw.snow
            text.contains("tornado") -> R.raw.tornado
            text.contains("hurricane") -> R.raw.hurricane
            text.contains("wind") -> R.raw.wind
            else -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
        }
    }
}