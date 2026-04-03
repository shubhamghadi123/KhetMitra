package com.example.khetmitra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.nl.translate.TranslateLanguage
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class ChatbotActivity : AppCompatActivity() {
    private val chatList = ArrayList<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var etInput: EditText
    private lateinit var recyclerChat: RecyclerView
    private lateinit var btnMicCard: View
    private lateinit var btnSendCard: View
    private lateinit var previewCard: CardView
    private lateinit var ivSelectedPreview: ImageView
    private var selectedImageBitmap: Bitmap? = null
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""
    private var selectedFileBytes: ByteArray? = null
    private var selectedFileMimeType: String = ""
    private lateinit var generativeModel: GenerativeModel
    private lateinit var activeChat: Chat
    private lateinit var markwon: Markwon
    private var currentLangCode: String = TranslateLanguage.ENGLISH
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerSessions: RecyclerView
    private lateinit var layoutDrawerEmpty: LinearLayout
    private val sessionList = mutableListOf<ChatSession>()
    private lateinit var drawerAdapter: DrawerSessionAdapter
    private var currentSessionId: String? = null
    private var isFirstUserMessage = true
    private var cachedFarmContext: String? = null
    private var cachedFarms: List<FarmEntry> = emptyList()

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    private fun getLanguageName(code: String): String {
        return when (code) {
            TranslateLanguage.MARATHI -> "Marathi"
            TranslateLanguage.HINDI -> "Hindi"
            TranslateLanguage.GUJARATI -> "Gujarati"
            TranslateLanguage.TAMIL -> "Tamil"
            TranslateLanguage.TELUGU -> "Telugu"
            TranslateLanguage.BENGALI -> "Bengali"
            TranslateLanguage.KANNADA -> "Kannada"
            TranslateLanguage.ENGLISH -> "English"
            else -> "English"
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) prepareImagePreview(imageBitmap)
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleUriImage(it) }
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleUriFile(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) openSystemCamera()
        else Toast.makeText(this, t("Camera permission required"), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        markwon = Markwon.create(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        etInput = findViewById(R.id.etMessageInput)
        recyclerChat = findViewById(R.id.recyclerChat)
        previewCard = findViewById(R.id.previewCard)
        ivSelectedPreview = findViewById(R.id.ivSelectedPreview)
        val btnRemoveImage = findViewById<View>(R.id.btnRemoveImage)
        val btnPlus = findViewById<View>(R.id.btnPlus)
        val btnMenu = findViewById<View>(R.id.btnMenu)

        btnMicCard = findViewById(R.id.btnMic)
        btnSendCard = findViewById(R.id.btnSend)
        btnSendCard.visibility = View.GONE

        findViewById<TextView>(R.id.tvHeaderTitle)?.text = t("Chat")

        chatAdapter = ChatAdapter(chatList, markwon)
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = chatAdapter

        drawerLayout = findViewById(R.id.drawerLayout)
        recyclerSessions = findViewById(R.id.recyclerSessions)
        layoutDrawerEmpty = findViewById(R.id.layoutDrawerEmpty)

        drawerAdapter = DrawerSessionAdapter(
            sessions = sessionList,
            activeSessionId = currentSessionId,
            onSessionClick = { session -> switchToSession(session.id) },
            onDeleteClick = { session -> confirmDeleteSession(session) }
        )
        recyclerSessions.layoutManager = LinearLayoutManager(this)
        recyclerSessions.adapter = drawerAdapter

        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        findViewById<View>(R.id.btnNewChat).setOnClickListener { startNewChat() }
        findViewById<View>(R.id.btnBackToDashboard).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            finish()
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                loadSessionList()
            }
        })

        currentSessionId = intent.getStringExtra("SESSION_ID")

        initializeSmartChatbot()

        btnPlus.setOnClickListener { showAttachmentOptions() }
        btnRemoveImage.setOnClickListener { clearPreview() }

        etInput.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString().trim().isNotEmpty() || selectedImageBitmap != null || selectedFileUri != null) {
                    btnMicCard.visibility = View.GONE
                    btnSendCard.visibility = View.VISIBLE
                } else {
                    btnSendCard.visibility = View.GONE
                    btnMicCard.visibility = View.VISIBLE
                }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {}
        })

        btnSendCard.setOnClickListener { sendMessage() }
        btnMicCard.setOnClickListener {
            Toast.makeText(this, t("Voice typing coming soon..."), Toast.LENGTH_SHORT).show()
        }

        if (currentLangCode != TranslateLanguage.ENGLISH) {
            window.decorView.post {
                TranslationHelper.translateViewHierarchy(window.decorView.rootView, currentLangCode) {
                }
            }
        }

        val autoOpenCamera = intent.getBooleanExtra("AUTO_OPEN_CAMERA", false)
        if (autoOpenCamera) {
            window.decorView.post {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    openSystemCamera()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private suspend fun fetchLiveWeatherForFarms(farms: List<FarmEntry>): Map<String, String> {
        val weatherMap = mutableMapOf<String, String>()
        withContext(Dispatchers.IO) {
            val deferreds = farms.map { farm ->
                async {
                    var summary = "Assume typical seasonal conditions."
                    try {
                        val cords = farm.coordinates
                        if (cords.contains(",")) {
                            val parts = cords.split(",")
                            val lat = parts[0].trim().toDouble()
                            val lon = parts[1].trim().toDouble()
                            val response = RetrofitClient.weatherService.getForecast(lat, lon)
                            if (response.isSuccessful && response.body() != null) {
                                val dailyData = response.body()?.daily
                                summary = "Live 16-day forecast JSON data: " + com.google.gson.Gson().toJson(dailyData)
                            }
                        }
                    } catch (_: Exception) {
                        Log.e("ChatbotWeather", "Failed to fetch weather for ${farm.name}")
                    }
                    (farm.name ?: "Unknown") to summary
                }
            }
            deferreds.awaitAll().forEach { (farmName, weatherSummary) ->
                weatherMap[farmName] = weatherSummary
            }
        }
        return weatherMap
    }

    private fun loadSessionList() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessions = ChatHistoryManager.getSessions()
                withContext(Dispatchers.Main) {
                    sessionList.clear()
                    sessionList.addAll(sessions)
                    drawerAdapter.setActiveSession(currentSessionId)
                    drawerAdapter.notifyDataSetChanged()
                    layoutDrawerEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                    recyclerSessions.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
                }
            } catch (_: Exception) {}
        }
    }

    private fun switchToSession(sessionId: String) {
        drawerLayout.closeDrawer(GravityCompat.START)
        if (sessionId == currentSessionId) return

        currentSessionId = sessionId
        isFirstUserMessage = false
        chatList.clear()
        chatAdapter.notifyDataSetChanged()

        etInput.hint = t("Loading chat...")
        etInput.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val savedMessages = ChatHistoryManager.getMessages(sessionId)

                val systemInstruction = buildSystemInstruction()
                generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    systemInstruction = content { text(systemInstruction) }
                )

                val historyContent = savedMessages.map { msg ->
                    content(role = if (msg.role == "user") "user" else "model") {
                        text(msg.text)
                    }
                }
                activeChat = generativeModel.startChat(history = historyContent)

                val messagesWithBitmaps = savedMessages.map { msg ->
                    async {
                        val bitmap = msg.imageUrl?.let { downloadBitmap(it) }
                        msg to bitmap
                    }
                }.awaitAll()

                withContext(Dispatchers.Main) {
                    for ((msg, bitmap) in messagesWithBitmaps) {
                        addMessage(
                            text = msg.text,
                            isUser = msg.role == "user",
                            bitmap = bitmap,
                            isImage = bitmap != null,
                            fileName = msg.fileName ?: ""
                        )
                    }
                    etInput.hint = t("Ask anything...")
                    etInput.isEnabled = true
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    etInput.hint = t("Ask anything...")
                    etInput.isEnabled = true
                    Toast.makeText(this@ChatbotActivity, t("Failed to load chat"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startNewChat() {
        drawerLayout.closeDrawer(GravityCompat.START)
        currentSessionId = null
        isFirstUserMessage = true
        chatList.clear()
        chatAdapter.notifyDataSetChanged()

        val systemInstruction = buildSystemInstruction()
        generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(systemInstruction) }
        )
        activeChat = generativeModel.startChat()

        val greeting = t("Namaste! I am KhetMitra AI. How can I help you today?")
        addMessage(greeting, false)
    }

    private fun confirmDeleteSession(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle(t("Delete Chat"))
            .setMessage("\"${session.title}\"")
            .setPositiveButton(t("Delete")) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        ChatHistoryManager.deleteSession(session.id)
                        withContext(Dispatchers.Main) {
                            if (session.id == currentSessionId) startNewChat()
                            loadSessionList()
                        }
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton(t("Cancel"), null)
            .show()
    }

    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val inputStream = connection.getInputStream()
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("KhetMitra", "Failed to download image: $url", e)
            null
        }
    }

    private fun buildSystemInstruction(): String {
        val ctx = cachedFarmContext ?: "No farm data available."
        val lang = getLanguageName(currentLangCode)
        return """
            You are KhetMitra AI, an expert agricultural assistant.
            You MUST always respond in fluent $lang.
            Use Markdown formatting.
            
            $ctx
            
            CRITICAL RULES:
            1. Prioritize LIVE SATELLITE DATA when answering questions.
            2. If they ask a general question, clarify WHICH farm they mean.
            3. Do not mention raw database terms to the user.
        """.trimIndent()
    }

    private fun buildCombinedFarmContext(
        farms: List<FarmEntry>,
        monitoringData: List<FieldMonitoring>,
        weatherData: Map<String, String>,
        farmPlans: List<SavedFarmPlan>,
        cropPrices: List<CropPriceRow>
    ): String {
        val sb = StringBuilder()

        if (farms.isEmpty()) {
            sb.append("The user has not mapped any farms yet. Instruct them to use the 'Map My Field' button on the dashboard.\n\n")
        } else {
            sb.append("The user manages ${farms.size} farm(s). Here is the comprehensive data:\n\n")

            for ((index, farm) in farms.withIndex()) {
                val farmName = farm.name ?: "Farm ${index + 1}"
                val crop = if (farm.crop.isNullOrBlank() || farm.crop == "Not Selected") "Unknown" else farm.crop
                val liveData = monitoringData.find { it.polygon_id == farm.polygon_id }
                val liveWeather = weatherData[farmName] ?: "No live weather data available."

                sb.append("Farm ${index + 1}: '$farmName'\n")
                sb.append("- Size: ${farm.land_size}\n")
                sb.append("- Soil Type: ${farm.soil_type}\n")
                sb.append("- Crop: $crop\n")

                if (liveData != null) {
                    sb.append("  [LIVE SATELLITE SOIL DATA]\n")
                    liveData.weather_condition?.let { sb.append("  - Weather: $it\n") }
                    liveData.temperature?.let { sb.append("  - Air Temp: $it°C\n") }
                    liveData.soil_moisture?.let { sb.append("  - Soil Moisture: $it\n") }
                    liveData.ndvi_score?.let { sb.append("  - Health Score (NDVI): $it\n") }
                } else {
                    sb.append("  [LIVE SOIL DATA IS MISSING]\n")
                    sb.append("  *CRITICAL INSTRUCTION: Live soil data for this farm has not been generated yet. Advise the user to go to 'Manage Field' and click 'View Soil Report'.*\n")
                }

                sb.append("  [LIVE WEATHER FORECAST]\n")
                sb.append("  - $liveWeather\n\n")
            }
        }

        if (farmPlans.isNotEmpty()) {
            sb.append("### [ACTIVE FARM PLANS (JSON FORMAT)]\n")
            sb.append("The user has generated the following AI crop plans. Use this data if they ask about their schedule, tasks, or budgets:\n")
            for (plan in farmPlans) {
                sb.append("- Farm: ${plan.farm_name}, Crop: ${plan.crop_name}\n")
                sb.append("  Plan JSON: ${plan.plan_json}\n\n")
            }
        }

        if (cropPrices.isNotEmpty()) {
            sb.append("### [LATEST LOCAL MARKET PRICES]\n")
            sb.append("Here are the latest crop prices in the user's local district markets. Advise them on selling based on these modal prices:\n")
            for (price in cropPrices) {
                sb.append("- Crop: ${price.cropName} | Market: ${price.marketName}\n")
                sb.append("  Min: ₹${price.minPrice}, Max: ₹${price.maxPrice}, Modal (Average): ₹${price.modalPrice} (Date: ${price.priceDate})\n")
            }
            sb.append("\n")
        } else {
            sb.append("### [LATEST LOCAL MARKET PRICES]\n")
            sb.append("No local market prices are currently available in the database for the user's district.\n\n")
        }

        return sb.toString()
    }

    private fun initializeSmartChatbot() {
        etInput.isEnabled = false
        btnMicCard.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                val currentFarmerId = user?.id ?: ""
                Log.d("KhetMitra", "Fetching data for Farmer: $currentFarmerId")

                val profileDeferred = async {
                    SupabaseManager.client.postgrest["farmers"]
                        .select { filter { eq("id", currentFarmerId) } }
                        .decodeSingleOrNull<FarmerProfile>()
                }

                val farmsDeferred = async {
                    SupabaseManager.client.postgrest["farms"]
                        .select { filter { eq("farmer_id", currentFarmerId) } }.decodeList<FarmEntry>()
                }

                val monitoringDeferred = async {
                    SupabaseManager.client.postgrest["field_monitoring"]
                        .select { filter { eq("user_id", currentFarmerId) } }.decodeList<FieldMonitoring>()
                }

                val plansDeferred = async {
                    SupabaseManager.client.postgrest["farm_plans"]
                        .select { filter { eq("farmer_id", currentFarmerId) } }
                        .decodeList<SavedFarmPlan>()
                }

                val userProfile = profileDeferred.await()
                val userFarms = farmsDeferred.await()
                val monitoringData = monitoringDeferred.await()
                val farmPlans = plansDeferred.await()

                val userDistrict = userProfile?.district
                val marketPrices = if (!userDistrict.isNullOrEmpty() && userDistrict != "Unknown") {
                    try {
                        SupabaseManager.client.postgrest["crop_price"]
                            .select { filter { ilike("district_name", "%$userDistrict%") } }
                            .decodeList<CropPriceRow>()
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val weatherMap = fetchLiveWeatherForFarms(userFarms)
                cachedFarms = userFarms

                cachedFarmContext = buildCombinedFarmContext(cachedFarms, monitoringData, weatherMap, farmPlans, marketPrices)

                generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    systemInstruction = content { text(buildSystemInstruction()) }
                )

                val sessionId = currentSessionId
                if (sessionId != null) {
                    val savedMessages = ChatHistoryManager.getMessages(sessionId)
                    if (savedMessages.isNotEmpty()) {
                        isFirstUserMessage = false

                        val historyContent = savedMessages.map { msg ->
                            content(role = if (msg.role == "user") "user" else "model") {
                                text(msg.text)
                            }
                        }
                        activeChat = generativeModel.startChat(history = historyContent)

                        val messagesWithBitmaps = savedMessages.map { msg ->
                            async {
                                val bitmap = msg.imageUrl?.let { downloadBitmap(it) }
                                msg to bitmap
                            }
                        }.awaitAll()

                        withContext(Dispatchers.Main) {
                            for ((msg, bitmap) in messagesWithBitmaps) {
                                addMessage(
                                    text = msg.text,
                                    isUser = msg.role == "user",
                                    bitmap = bitmap,
                                    isImage = bitmap != null,
                                    fileName = msg.fileName ?: ""
                                )
                            }
                        }
                    } else {
                        activeChat = generativeModel.startChat()
                    }
                } else {
                    activeChat = generativeModel.startChat()
                }

                withContext(Dispatchers.Main) {
                    etInput.hint = t("Ask anything...")
                    etInput.isEnabled = true
                    btnMicCard.isEnabled = true

                    if (currentSessionId == null || chatList.isEmpty()) {
                        val greeting = if (userFarms.isNotEmpty()) {
                            t("Namaste! I am KhetMitra AI. How can I help you today?")
                        } else {
                            t("Namaste! I am KhetMitra AI. You haven't mapped any farms yet, but you can still ask me anything!")
                        }
                        addMessage(greeting, false)
                    }
                }
            } catch (e: Exception) {
                Log.e("KhetMitra", "Failed to initialize AI", e)
                withContext(Dispatchers.Main) {
                    etInput.hint = t("Ask anything...")
                    etInput.isEnabled = true
                    btnMicCard.isEnabled = true

                    cachedFarmContext = "No farm data available."
                    cachedFarms = emptyList()

                    generativeModel = GenerativeModel(
                        modelName = "gemini-2.5-flash",
                        apiKey = BuildConfig.GEMINI_API_KEY,
                        systemInstruction = content { text(buildSystemInstruction()) }
                    )
                    activeChat = generativeModel.startChat()
                    addMessage(t("Namaste! I am KhetMitra AI. How can I help?"), false)
                }
            }
        }
    }

    //  Image / File
    private fun handleUriImage(uri: Uri) {
        try {
            val bitmap =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            prepareImagePreview(bitmap)
        } catch (_: Exception) {
            Toast.makeText(this, t("Failed to load image"), Toast.LENGTH_SHORT).show()
        }
    }

    @android.annotation.SuppressLint("Range")
    private fun handleUriFile(uri: Uri) {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    selectedFileName = cursor.getString(cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME))
                }
            }
            selectedFileMimeType = contentResolver.getType(uri) ?: "application/pdf"
            val inputStream = contentResolver.openInputStream(uri)
            selectedFileBytes = inputStream?.readBytes()
            inputStream?.close()
            selectedFileUri = uri
            prepareFilePreview()
        } catch (_: Exception) {
            Toast.makeText(this, t("Failed to load file"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareImagePreview(bitmap: Bitmap) {
        selectedImageBitmap = bitmap
        ivSelectedPreview.setImageBitmap(bitmap)
        ivSelectedPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        previewCard.visibility = View.VISIBLE
        btnMicCard.visibility = View.GONE
        btnSendCard.visibility = View.VISIBLE
    }

    private fun prepareFilePreview() {
        ivSelectedPreview.setImageResource(R.drawable.round_file_present_24)
        ivSelectedPreview.scaleType = ImageView.ScaleType.CENTER_INSIDE
        previewCard.visibility = View.VISIBLE
        btnMicCard.visibility = View.GONE
        btnSendCard.visibility = View.VISIBLE
    }

    private fun clearPreview() {
        selectedImageBitmap = null
        selectedFileUri = null
        selectedFileBytes = null
        selectedFileName = ""
        selectedFileMimeType = ""
        previewCard.visibility = View.GONE
        ivSelectedPreview.scaleType = ImageView.ScaleType.CENTER_CROP
        if (etInput.text.isEmpty()) {
            btnSendCard.visibility = View.GONE
            btnMicCard.visibility = View.VISIBLE
        }
    }

    //  Send / Receive
    private fun sendMessage() {
        val query = etInput.text.toString().trim()
        val imageToSend = selectedImageBitmap
        val fileToSend = selectedFileUri
        val bytesToSend = selectedFileBytes
        val mimeTypeToSend = selectedFileMimeType
        val fileNameSnapshot = selectedFileName

        if (query.isNotEmpty() || imageToSend != null || fileToSend != null) {
            addMessage(
                text = query,
                isUser = true,
                bitmap = imageToSend,
                fileUri = fileToSend,
                isImage = (imageToSend != null),
                fileName = fileNameSnapshot
            )
            etInput.text.clear()
            clearPreview()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (currentSessionId == null) {
                        currentSessionId = ChatHistoryManager.createSession()
                    }
                    val sid = currentSessionId ?: return@launch

                    var imageUrl: String? = null
                    if (imageToSend != null) {
                        imageUrl = ChatHistoryManager.uploadImage(imageToSend)
                    }

                    val textToSave = query.ifEmpty {
                        if (imageToSend != null) "[${t("Image sent")}]" else "[${t("File")}: $fileNameSnapshot]"
                    }

                    ChatHistoryManager.saveMessage(
                        sessionId = sid,
                        role = "user",
                        text = textToSave,
                        imageUrl = imageUrl,
                        fileName = fileNameSnapshot.ifEmpty { null }
                    )

                    if (isFirstUserMessage && query.isNotEmpty()) {
                        ChatHistoryManager.updateTitle(sid, query)
                        isFirstUserMessage = false
                    }
                } catch (_: Exception) {}
            }

            fetchGeminiResponse(query, imageToSend, bytesToSend, mimeTypeToSend)
        }
    }

    private fun fetchGeminiResponse(query: String, bitmap: Bitmap?, fileBytes: ByteArray?, mimeType: String) {
        val loadingText = t("Thinking...")
        val loadingMessage = ChatMessage(loadingText, false, isLoading = true)
        chatList.add(loadingMessage)
        chatAdapter.notifyItemInserted(chatList.size - 1)
        recyclerChat.scrollToPosition(chatList.size - 1)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = if (bitmap != null) {
                    val inputContent = content {
                        image(bitmap)
                        text(query.ifEmpty { t("Analyze this agricultural image and provide details.") })
                    }
                    activeChat.sendMessage(inputContent)
                } else if (fileBytes != null) {
                    val inputContent = content {
                        blob(mimeType, fileBytes)
                        text(query.ifEmpty { t("Analyze this document and summarize it simply for a farmer.") })
                    }
                    activeChat.sendMessage(inputContent)
                } else {
                    activeChat.sendMessage(query)
                }

                val aiText = response.text ?: t("I'm sorry, I couldn't process that.")

                try {
                    currentSessionId?.let { sid ->
                        ChatHistoryManager.saveMessage(sid, "model", aiText)
                    }
                } catch (e: Exception) {
                    Log.e("KhetMitra", "Failed to save AI message", e)
                }

                withContext(Dispatchers.Main) {
                    val idx = chatList.indexOf(loadingMessage)
                    if (idx != -1) {
                        chatList.removeAt(idx)
                        chatAdapter.notifyItemRemoved(idx)
                    }
                    addMessage(aiText, isUser = false)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    val idx = chatList.indexOf(loadingMessage)
                    if (idx != -1) {
                        chatList.removeAt(idx)
                        chatAdapter.notifyItemRemoved(idx)
                    }
                    addMessage(t("The AI server is very busy right now. Please try again in a moment."), isUser = false)
                }
            }
        }
    }

    private fun addMessage(
        text: String,
        isUser: Boolean,
        bitmap: Bitmap? = null,
        fileUri: Uri? = null,
        isImage: Boolean = true,
        fileName: String = ""
    ) {
        chatList.add(ChatMessage(text, isUser, bitmap, fileUri, isImage, fileName))
        chatAdapter.notifyItemInserted(chatList.size - 1)
        recyclerChat.scrollToPosition(chatList.size - 1)
    }

    //  Attachments
    private fun showAttachmentOptions() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_chat_attachments, null)
        if (currentLangCode != TranslateLanguage.ENGLISH) {
            TranslationHelper.translateViewHierarchy(view, currentLangCode) {}
        }
        bottomSheetDialog.setContentView(view)

        view.findViewById<View>(R.id.optionCamera).setOnClickListener {
            bottomSheetDialog.dismiss()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openSystemCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        view.findViewById<View>(R.id.optionGallery).setOnClickListener {
            bottomSheetDialog.dismiss()
            pickImageLauncher.launch("image/*")
        }
        view.findViewById<View>(R.id.optionFile).setOnClickListener {
            bottomSheetDialog.dismiss()
            pickFileLauncher.launch("application/pdf")
        }
        bottomSheetDialog.show()
    }

    private fun openSystemCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(intent)
    }
}