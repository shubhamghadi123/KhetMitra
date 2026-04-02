package com.example.khetmitra

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.GravityCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : BaseActivity() {
    private lateinit var adapter: DashboardAdapter
    private val dashboardItems = ArrayList<DataModels>()
    private var currentLangCode = TranslateLanguage.ENGLISH
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val DEFAULT_CITY = "19.07,72.87"

    fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    fun d(num: String): String {
        return TranslationHelper.convertDigits(num, currentLangCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val profileCard = findViewById<CardView>(R.id.profileCard)
        val btnStartMapping = findViewById<MaterialCardView>(R.id.btnStartMapping)
        val btnScanCrop     = findViewById<MaterialCardView>(R.id.btnScanCrop)
        val navView = findViewById<NavigationView>(R.id.navView)
        val btnNotification = findViewById<FrameLayout>(R.id.btnNotification)
        val logoutItem = navView.menu.findItem(R.id.nav_logout)
        val logoutColor = ColorStateList.valueOf("#EF4444".toColorInt())
        val logoutTitle = SpannableString(logoutItem.title)
        logoutTitle.setSpan(ForegroundColorSpan("#EF4444".toColorInt()), 0, logoutTitle.length, 0)
        logoutItem.title = logoutTitle
        logoutItem.iconTintList = logoutColor

        profileCard.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        btnNotification.setOnClickListener {
            showNotificationInbox()
        }

        val shouldOpenMap = intent.getBooleanExtra("OPEN_MAP_FRAGMENT", false)
        if (shouldOpenMap) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, FieldMeasurementFragment())
                .addToBackStack(null)
                .commit()
        }

        val scanCropClickListener = View.OnClickListener {
            val intent = Intent(this, ChatbotActivity::class.java)
            intent.putExtra("AUTO_OPEN_CAMERA", true)
            startActivity(intent)
        }
        btnScanCrop.setOnClickListener(scanCropClickListener)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.nav_manage_fields -> startActivity(Intent(this, ManageFieldsActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_help -> startActivity(Intent(this, HelpSupportActivity::class.java))
                R.id.nav_logout -> {
                    CoroutineScope(Dispatchers.IO).launch {
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
                            if (e is CancellationException) throw e
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
                title == t("Plans") || title == "Plans" ->
                    startActivity(Intent(this, PlanDashboardActivity::class.java))
            }
        }
        recyclerView.adapter = adapter

        checkLocationPermissionAndFetch()
        scheduleWeatherAlerts()

        if (currentLangCode != TranslateLanguage.ENGLISH) {
            val rootView = findViewById<View>(android.R.id.content)
            TranslationHelper.translateViewHierarchy(rootView, currentLangCode) {}
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationPermissionAndFetch()
        updateNotificationDot()
    }

    private fun loadProfileImage(url: String, imageView: ImageView?) {
        if (imageView == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connection = URL(url).openConnection()
                connection.doInput = true
                connection.connect()
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load profile image: ${e.message}")
            }
        }
    }

    private val cardThemes = listOf(
        CardTheme("#FFF7ED".toColorInt(), "#F97316".toColorInt(), "🌤️", "Live",    "#F97316".toColorInt()),
        CardTheme("#F0FDF4".toColorInt(), "#22C55E".toColorInt(), "📋", "Today",   "#22C55E".toColorInt()),
        CardTheme("#F0F9FF".toColorInt(), "#0EA5E9".toColorInt(), "💬", "New",     "#0EA5E9".toColorInt()),
        CardTheme("#FAF5FF".toColorInt(), "#A855F7".toColorInt(), "📈", "Updated", "#A855F7".toColorInt()),
    )

    private fun setupInitialData() {
        dashboardItems.clear()
        val weatherSubtitle = "${t("Loading")}..."
        dashboardItems.add(
            DataModels(
                title       = t("Weather"),
                subtitle    = weatherSubtitle,
                iconRes     = R.drawable.ic_weather,
                tag         = t("Live"),
                bgColor     = cardThemes[0].bgColor,
                accentColor = cardThemes[0].accentColor,
                emoji       = cardThemes[0].emoji,
                textColor   = cardThemes[0].textColor
            )
        )
        dashboardItems.add(
            DataModels(
                title       = t("Plans"),
                subtitle    = "",
                iconRes     = R.drawable.ic_plans,
                tag         = t("Today"),
                bgColor     = cardThemes[1].bgColor,
                accentColor = cardThemes[1].accentColor,
                emoji       = cardThemes[1].emoji,
                textColor   = cardThemes[1].textColor
            )
        )
        dashboardItems.add(
            DataModels(
                title       = t("Chat"),
                subtitle    = "",
                iconRes     = R.drawable.ic_chat,
                tag         = t("New"),
                bgColor     = cardThemes[2].bgColor,
                accentColor = cardThemes[2].accentColor,
                emoji       = cardThemes[2].emoji,
                textColor   = cardThemes[2].textColor
            )
        )
        dashboardItems.add(
            DataModels(
                title       = t("Market"),
                subtitle    = "",
                iconRes     = R.drawable.ic_market,
                tag         = t("Updated"),
                bgColor     = cardThemes[3].bgColor,
                accentColor = cardThemes[3].accentColor,
                emoji       = cardThemes[3].emoji,
                textColor   = cardThemes[3].textColor
            )
        )
    }

    @SuppressLint("SetTextI18n")
    private fun fetchAndDisplayFarmerName() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return@launch
                val profile = SupabaseManager.client.postgrest["farmers"]
                    .select { filter { eq("id", userId) } }
                    .decodeSingleOrNull<FarmerProfile>()
                withContext(Dispatchers.Main) {
                    if (profile != null) {
                        val tvWelcomeMessage = findViewById<TextView>(R.id.tvWelcome)
                        val tvUsername = findViewById<TextView>(R.id.tvUsername)
                        tvWelcomeMessage?.text = t("Welcome back,")
                        translateWithMLKit("${profile.first_name}!") { translatedFirstName ->
                            tvUsername?.text = translatedFirstName
                        }
                        val navView = findViewById<NavigationView>(R.id.navView)
                        val headerView = navView?.getHeaderView(0)
                        val tvHeaderName = headerView?.findViewById<TextView>(R.id.navUserName)
                        translateWithMLKit(profile.first_name) { translatedFullName ->
                            tvHeaderName?.text = translatedFullName
                        }
                        val ivMainProfile = findViewById<ImageView>(R.id.ivMainProfilePhoto)
                        val ivNavProfile = headerView?.findViewById<ImageView>(R.id.ivNavProfilePhoto)
                        if (profile.profile_photo_url.isNullOrEmpty()) {
                            val defaultAvatar = when (profile.gender.lowercase()) {
                                "male" -> R.drawable.default_male_farmer
                                "female" -> R.drawable.default_female_farmer
                                else -> R.drawable.round_person_24
                            }
                            ivMainProfile?.setImageResource(defaultAvatar)
                            ivNavProfile?.setImageResource(defaultAvatar)
                        } else {
                            loadProfileImage(profile.profile_photo_url, ivMainProfile)
                            loadProfileImage(profile.profile_photo_url, ivNavProfile)
                        }
                    }
                }
            } catch (e: Exception) {
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

    @Suppress("DEPRECATION")
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



    private fun isFahrenheit(prefs: SharedPreferences): Boolean {
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

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.weatherService.getForecast(lat, lon)
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
                    val useFahrenheit = isFahrenheit(prefs)
                    val currentTempRaw = data.current.temperature_2m
                    val tempText = convertTemp(currentTempRaw, useFahrenheit).toString()
                    val weatherCode = data.current.weathercode
                    val isDay = data.current.is_day
                    val rawCondition = getConditionText(weatherCode)
                    val iconRes = getIconForCondition(rawCondition, isDay)

                    val todayMaxTemp = data.daily.temperature_2m_max.firstOrNull() ?: 0.0
                    val todayMinTemp = data.daily.temperature_2m_min.firstOrNull() ?: 0.0
                    val todayRain = data.daily.precipitation_probability_max.firstOrNull() ?: 0
                    val windSpeedMetric = data.current.wind_speed_10m

                    val isMph = prefs.getString("WindUnit", "km/h")?.contains("mph") == true
                    val windSymbol = if (isMph) t("m/h") else t("km/h")
                    val windSpeedDisplay = if (isMph) (windSpeedMetric * 0.621371).toInt() else windSpeedMetric.toInt()

                    var alertTitle = ""
                    var alertMessage = ""

                    if (todayMinTemp < 5.0) {
                        alertTitle = t("Frost Warning") + " ❄️"
                        alertMessage = "${t("Temperatures dropping to")} ${d(todayMinTemp.toInt().toString())}°. ${t("Apply light irrigation to protect crops from frost")}."
                    } else if (todayMaxTemp > 38.0) {
                        alertTitle = t("Heat Stress Alert") + " ☀️"
                        alertMessage = "${t("Extreme heat")} (${d(todayMaxTemp.toInt().toString())}°). ${t("Ensure adequate soil moisture and avoid afternoon spraying")}."
                    } else if (windSpeedMetric > 15) {
                        alertTitle = t("Spraying Alert") + " 💨"
                        alertMessage = "${t("Wind is too strong")} (${d(windSpeedDisplay.toString())} $windSymbol). ${t("Avoid spraying to prevent chemical drift")}."
                    } else if (todayRain > 50) {
                        alertTitle = t("Rainfall Alert") + " 🌧️"
                        alertMessage = "${t("High chance of rain")} (${d(todayRain.toString())}%). ${t("Delay spraying fertilizers and pesticides")}."
                    }

                    withContext(Dispatchers.Main) {
                        val tempSymbol = if (useFahrenheit) t("°F") else t("°C")

                        if (alertTitle.isNotEmpty()) {
                            saveNotification(alertTitle, alertMessage)
                        }

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
            } catch (e: Exception) {
                Log.e("MainActivity", "Weather fetch failed: ${e.message}", e)
            }
        }
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
            61, 63, 65, 80, 81, 82 -> "Rain"
            71, 73, 75, 77, 85, 86 -> "Snow"
            95 -> "Thunderstorm"
            96, 99 -> "Hail"
            else -> "Unknown"
        }
    }

    private fun updateWeatherCard(condition: String, temp: String, unitSymbol: String, iconRes: Int) {
        val newSubtitle = "$condition, ${d(temp)}$unitSymbol"
        if (dashboardItems.isNotEmpty()) {
            val existing = dashboardItems[0]
            dashboardItems[0] = existing.copy(subtitle = newSubtitle, iconRes = iconRes)
            adapter.notifyItemChanged(0)
        }
    }

    private fun translateWithMLKit(text: String, callback: (String) -> Unit) {
        if (currentLangCode == TranslateLanguage.ENGLISH) {
            callback(text)
            return
        }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(currentLangCode)
            .build()
        val client = Translation.getClient(options)
        client.downloadModelIfNeeded().addOnSuccessListener {
            client.translate(text).addOnSuccessListener { result -> callback(result) }
                .addOnFailureListener { callback(text) }
        }.addOnFailureListener { callback(text) }
    }

    private fun getIconForCondition(conditionRaw: String, isDay: Int = 1): Int {
        val text = conditionRaw.lowercase()
        return when {
            text.contains("clear") || text.contains("sunny") -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
            text.contains("partly") -> if (isDay == 1) R.raw.partly_cloudy_day else R.raw.partly_cloudy_night
            text.contains("cloudy") -> R.raw.cloudy
            text.contains("rain") -> R.raw.rain
            text.contains("thunder") -> R.raw.thunderstorms
            else -> if (isDay == 1) R.raw.clear_day else R.raw.clear_night
        }
    }

    private fun scheduleWeatherAlerts() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        val weatherWorkRequest = androidx.work.PeriodicWorkRequestBuilder<WeatherAlertWorker>(12, java.util.concurrent.TimeUnit.HOURS).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("WeatherAlertJob", androidx.work.ExistingPeriodicWorkPolicy.KEEP, weatherWorkRequest)
    }

    private fun saveNotification(title: String, message: String) {
        val prefs = getSharedPreferences("AppNotifications", MODE_PRIVATE)
        val historyJson = prefs.getString("history", "[]")
        val type = object : TypeToken<MutableList<NotificationItem>>() {}.type
        val history: MutableList<NotificationItem> = Gson().fromJson(historyJson, type)

        val last = history.lastOrNull()
        if (last != null && last.title == title && (System.currentTimeMillis() - last.timestamp) < 12 * 60 * 60 * 1000) {
            return
        }

        history.add(NotificationItem(title, message, System.currentTimeMillis()))
        prefs.edit {putString("history", Gson().toJson(history))}
        updateNotificationDot()
    }

    private fun updateNotificationDot() {
        val prefs = getSharedPreferences("AppNotifications", MODE_PRIVATE)
        val historyJson = prefs.getString("history", "[]")
        val notificationDot = findViewById<View>(R.id.notificationBadge)

        if (historyJson != "[]") {
            notificationDot?.visibility = View.VISIBLE
        } else {
            notificationDot?.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showNotificationInbox() {
        val prefs = getSharedPreferences("AppNotifications", MODE_PRIVATE)
        val historyJson = prefs.getString("history", "[]")
        val type = object : TypeToken<MutableList<NotificationItem>>() {}.type
        val history: MutableList<NotificationItem> = Gson().fromJson(historyJson, type)

        if (history.isEmpty()) {
            Toast.makeText(this, t("All caught up! No new notifications."), Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)

        val mainContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor("#FFFFFF".toColorInt()) // Pure white background
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        val headerRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                (20 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (20 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt()
            )
        }

        val titleView = TextView(this).apply {
            text = t("Notifications")
            textSize = 22f
            setTextColor("#1E293B".toColorInt()) // Very Dark Slate
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clearAllBtn = TextView(this).apply {
            text = t("Clear All")
            textSize = 14f
            setTextColor("#EF4444".toColorInt()) // Vibrant Red
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((10 * resources.displayMetrics.density).toInt(), (5 * resources.displayMetrics.density).toInt(), 0, (5 * resources.displayMetrics.density).toInt())
            setOnClickListener {
                history.clear()
                prefs.edit { putString("history", "[]") }
                updateNotificationDot()
                bottomSheetDialog.dismiss()
                Toast.makeText(this@MainActivity, t("All notifications cleared"), Toast.LENGTH_SHORT).show()
            }
        }

        headerRow.addView(titleView)
        headerRow.addView(clearAllBtn)
        mainContainer.addView(headerRow)

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val listLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                (20 * resources.displayMetrics.density).toInt(),
                0,
                (20 * resources.displayMetrics.density).toInt(),
                (40 * resources.displayMetrics.density).toInt()
            )
        }

        scrollView.addView(listLayout)
        mainContainer.addView(scrollView)

        fun renderList() {
            listLayout.removeAllViews()

            if (history.isEmpty()) {
                bottomSheetDialog.dismiss()
                updateNotificationDot()
                return
            }

            history.reversed().forEach { item ->
                val card = MaterialCardView(this).apply {
                    val params = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 0, 0, (14 * resources.displayMetrics.density).toInt())
                    layoutParams = params
                    setCardBackgroundColor("#FFFFFF".toColorInt()) // Pure white card
                    strokeColor = "#E2E8F0".toColorInt()
                    strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                    radius = 24f * resources.displayMetrics.density
                    cardElevation = 0f
                }

                val innerLayout = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(
                        (20 * resources.displayMetrics.density).toInt(),
                        (16 * resources.displayMetrics.density).toInt(),
                        (20 * resources.displayMetrics.density).toInt(),
                        (20 * resources.displayMetrics.density).toInt()
                    )
                }

                val cardHeaderRow = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val itemDate = java.util.Date(item.timestamp)
                var finalTranslatedDate: String

                if (currentLangCode == TranslateLanguage.ENGLISH) {
                    val sdfEnglishFull = java.text.SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.ENGLISH)
                    finalTranslatedDate = sdfEnglishFull.format(itemDate)
                } else {
                    val sdfBase = java.text.SimpleDateFormat("dd MMM, h:mm", java.util.Locale.ENGLISH)
                    var baseDateStr = sdfBase.format(itemDate)

                    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    for (month in months) {
                        if (baseDateStr.contains(month)) {
                            baseDateStr = baseDateStr.replace(month, t(month))
                            break
                        }
                    }

                    val calendar = java.util.Calendar.getInstance()
                    calendar.timeInMillis = item.timestamp
                    val hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val periodKey = when (hourOfDay) {
                        in 5..11 -> "Morning"
                        in 12..16 -> "Afternoon"
                        in 17..19 -> "Evening"
                        else -> "Night"
                    }
                    finalTranslatedDate = "$baseDateStr ${t(periodKey)}"
                }

                val dateText = TextView(this).apply {
                    text = d(finalTranslatedDate)
                    setTextColor("#64748B".toColorInt()) // Soft Grey
                    textSize = 12f
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val deleteBtn = TextView(this).apply {
                    text = "✕"
                    setTextColor("#94A3B8".toColorInt())
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding((10 * resources.displayMetrics.density).toInt(), 0, 0, 0)
                    setOnClickListener {
                        history.remove(item)
                        prefs.edit { putString("history", Gson().toJson(history)) }
                        renderList()
                    }
                }

                cardHeaderRow.addView(dateText)
                cardHeaderRow.addView(deleteBtn)

                val itemTitle = TextView(this).apply {
                    text = item.title
                    setTextColor("#0F172A".toColorInt()) // Very Dark
                    textSize = 17f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, 0)
                }

                val itemMessage = TextView(this).apply {
                    text = item.message
                    setTextColor("#475569".toColorInt()) // Medium Grey
                    textSize = 14f
                    setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, 0)
                    setLineSpacing(0f, 1.3f)
                }

                innerLayout.addView(cardHeaderRow)
                innerLayout.addView(itemTitle)
                innerLayout.addView(itemMessage)
                card.addView(innerLayout)
                listLayout.addView(card)
            }
        }

        renderList()
        bottomSheetDialog.setContentView(mainContainer)
        (mainContainer.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        bottomSheetDialog.show()
    }
}