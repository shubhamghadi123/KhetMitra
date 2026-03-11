package com.example.khetmitra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatbotActivity : AppCompatActivity() {

    private val chatList = ArrayList<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var etInput: EditText
    private lateinit var recyclerChat: RecyclerView
    private lateinit var btnMicCard: View
    private lateinit var btnSendCard: View
    private lateinit var previewCard: CardView
    private lateinit var ivSelectedPreview: ImageView

    // Attachment State
    private var selectedImageBitmap: Bitmap? = null
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""
    private var selectedFileBytes: ByteArray? = null
    private var selectedFileMimeType: String = ""

    // AI & Formatting
    private lateinit var generativeModel: GenerativeModel
    private lateinit var activeChat: Chat // NEW: Maintains conversation history
    private lateinit var markwon: Markwon
    private var currentLangCode: String = TranslateLanguage.ENGLISH

    // Translation Helper
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

    // Launchers
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                prepareImagePreview(imageBitmap)
            }
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

        // Init Formatting & Lang
        markwon = Markwon.create(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        // View Binding
        etInput = findViewById(R.id.etMessageInput)
        recyclerChat = findViewById(R.id.recyclerChat)
        previewCard = findViewById(R.id.previewCard)
        ivSelectedPreview = findViewById(R.id.ivSelectedPreview)
        val btnRemoveImage = findViewById<View>(R.id.btnRemoveImage)
        val btnBack = findViewById<View>(R.id.btnBack)
        val btnPlus = findViewById<View>(R.id.btnPlus)

        btnMicCard = findViewById(R.id.btnMic)
        btnSendCard = findViewById(R.id.btnSend)
        btnSendCard.visibility = View.GONE

        // Assuming you added btnSync to your XML header
        val btnSync = findViewById<ImageView>(R.id.btnSync)

        findViewById<TextView>(R.id.tvHeaderTitle)?.text = t("Chat")

        chatAdapter = ChatAdapter(chatList, markwon)
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = chatAdapter

        // INITIALIZE SMART AI
        initializeSmartChatbot()

        // Listeners
        btnPlus.setOnClickListener { showAttachmentOptions() }
        btnRemoveImage.setOnClickListener { clearPreview() }
        btnBack.setOnClickListener { finish() }
        btnSync?.setOnClickListener { syncLatestFarmData() }

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

        // Handle auto-open camera
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
    }

    // ==========================================
    // DATA & AI INITIALIZATION (SUPABASE)
    // ==========================================

    private fun buildCombinedFarmContext(farms: List<FarmEntry>, monitoringData: List<FieldMonitoring>): String {
        if (farms.isEmpty()) return "The user has not mapped any farms yet. Instruct them to use the 'Map My Field' button on the dashboard."

        val contextBuilder = StringBuilder()
        contextBuilder.append("The user manages ${farms.size} farm(s). Here is the data:\n\n")

        for ((index, farm) in farms.withIndex()) {
            val farmName = farm.name ?: "Farm ${index + 1}"
            val crop = if (farm.crop.isNullOrBlank() || farm.crop == "Not Selected") "Unknown" else farm.crop
            val liveData = monitoringData.find { it.polygon_id == farm.poly_id }

            contextBuilder.append("Farm ${index + 1}: '$farmName'\n")
            contextBuilder.append("- Size: ${farm.land_size}\n")
            contextBuilder.append("- Soil Type: ${farm.soil_type}\n")
            contextBuilder.append("- Crop: $crop\n")

            if (liveData != null) {
                contextBuilder.append("  [LIVE SATELLITE DATA FOUND]\n")
                liveData.weather_condition?.let { contextBuilder.append("  - Weather: $it\n") }
                liveData.temperature?.let { contextBuilder.append("  - Air Temp: $it°C\n") }
                liveData.soil_moisture?.let { contextBuilder.append("  - Soil Moisture: $it\n") }
                liveData.ndvi_score?.let { contextBuilder.append("  - Health Score (NDVI): $it\n") }
            } else {
                contextBuilder.append("  [LIVE DATA IS MISSING]\n")
                contextBuilder.append("  *CRITICAL INSTRUCTION: Live soil data for this farm has not been generated yet. If the user asks about current soil health, politely instruct them to go to 'Manage Field' and click 'View Soil Report' to generate data.*\n")
            }
            contextBuilder.append("\n")
        }
        return contextBuilder.toString()
    }

    private fun initializeSmartChatbot() {
        etInput.hint = t("Loading farm data...")
        etInput.isEnabled = false
        btnMicCard.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                val currentFarmerId = user?.id ?: ""
                Log.d("KhetMitra", "Fetching data for Farmer: $currentFarmerId")

                // Fetch concurrently
                val farmsDeferred = async {
                    SupabaseManager.client.postgrest["farms"]
                        .select { filter { eq("farmer_id", currentFarmerId) } }.decodeList<FarmEntry>()
                }
                val monitoringDeferred = async {
                    SupabaseManager.client.postgrest["field_monitoring"]
                        .select { filter { eq("user_id", currentFarmerId) } }.decodeList<FieldMonitoring>()
                }

                val userFarms = farmsDeferred.await()
                val monitoringData = monitoringDeferred.await()

                val combinedContext = buildCombinedFarmContext(userFarms, monitoringData)
                val requestedLanguage = getLanguageName(currentLangCode)

                val fullInstruction = """
                    You are KhetMitra AI, an expert agricultural assistant.
                    You MUST always respond in fluent $requestedLanguage.
                    Use Markdown formatting.
                    
                    $combinedContext
                    
                    CRITICAL RULES:
                    1. Prioritize LIVE SATELLITE DATA when answering questions.
                    2. If they ask a general question, clarify WHICH farm they mean.
                    3. Do not mention raw database terms to the user.
                """.trimIndent()

                generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    systemInstruction = content { text(fullInstruction) }
                )

                // Initialize conversation history
                activeChat = generativeModel.startChat()

                withContext(Dispatchers.Main) {
                    etInput.hint = t("Ask anything...")
                    etInput.isEnabled = true
                    btnMicCard.isEnabled = true

                    val greeting = if (userFarms.isNotEmpty()) {
                        t("Namaste! I have loaded your farm data. How can I help you today?")
                    } else {
                        t("Namaste! I am KhetMitra AI. You haven't mapped any farms yet, but you can still ask me anything!")
                    }
                    addMessage(greeting, false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    etInput.hint = t("Ask anything...")
                    etInput.isEnabled = true
                    btnMicCard.isEnabled = true

                    generativeModel = GenerativeModel(
                        modelName = "gemini-2.5-flash",
                        apiKey = BuildConfig.GEMINI_API_KEY,
                        systemInstruction = content { text("You are KhetMitra AI. Always reply in ${getLanguageName(currentLangCode)}.") }
                    )
                    activeChat = generativeModel.startChat()
                    addMessage(t("Namaste! I am KhetMitra AI. How can I help?"), false)
                }
            }
        }
    }

    private fun syncLatestFarmData() {
        Toast.makeText(this, t("Syncing latest soil data..."), Toast.LENGTH_SHORT).show()
        val btnSync = findViewById<ImageView>(R.id.btnSync)
        btnSync?.animate()?.rotationBy(360f)?.setDuration(1000)?.start()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseManager.client.auth.currentUserOrNull()
                val currentFarmerId = user?.id ?: return@launch

                val farmsDeferred = async {
                    SupabaseManager.client.postgrest["farms"].select { filter { eq("farmer_id", currentFarmerId) } }.decodeList<FarmEntry>()
                }
                val monitoringDeferred = async {
                    SupabaseManager.client.postgrest["field_monitoring"].select { filter { eq("user_id", currentFarmerId) } }.decodeList<FieldMonitoring>()
                }

                val combinedContext = buildCombinedFarmContext(farmsDeferred.await(), monitoringDeferred.await())
                val requestedLanguage = getLanguageName(currentLangCode)

                val fullInstruction = """
                    You are KhetMitra AI, an expert agricultural assistant.
                    You MUST always respond in fluent $requestedLanguage.
                    Use Markdown formatting.
                    
                    $combinedContext
                    
                    CRITICAL RULES:
                    1. Prioritize LIVE SATELLITE DATA when answering questions.
                    2. If they ask a general question, clarify WHICH farm they mean.
                """.trimIndent()

                // Overwrite the model, but start a NEW chat session with the fresh context
                generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    systemInstruction = content { text(fullInstruction) }
                )

                // Note: Syncing wipes short-term memory (chat history) but updates long-term memory (database)
                activeChat = generativeModel.startChat()

                withContext(Dispatchers.Main) {
                    addMessage(t("✅ System: I have synced your latest soil reports!"), false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatbotActivity, t("Sync failed."), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================
    // CHAT & ATTACHMENT LOGIC
    // ==========================================

    private fun handleUriImage(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            prepareImagePreview(bitmap)
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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

    private fun sendMessage() {
        val query = etInput.text.toString().trim()
        val imageToSend = selectedImageBitmap
        val fileToSend = selectedFileUri
        val bytesToSend = selectedFileBytes
        val mimeTypeToSend = selectedFileMimeType

        if (query.isNotEmpty() || imageToSend != null || fileToSend != null) {
            addMessage(
                text = query,
                isUser = true,
                bitmap = imageToSend,
                fileUri = fileToSend,
                isImage = (imageToSend != null),
                fileName = selectedFileName
            )
            etInput.text.clear()
            clearPreview()
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
                // SEND TO ACTIVE CHAT (Memory!)
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

                withContext(Dispatchers.Main) {
                    val loadingIndex = chatList.indexOf(loadingMessage)
                    if (loadingIndex != -1) {
                        chatList.removeAt(loadingIndex)
                        chatAdapter.notifyItemRemoved(loadingIndex)
                    }
                    addMessage(aiText, isUser = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val loadingIndex = chatList.indexOf(loadingMessage)
                    if (loadingIndex != -1) {
                        chatList.removeAt(loadingIndex)
                        chatAdapter.notifyItemRemoved(loadingIndex)
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

    // ==========================================
    // ADAPTER
    // ==========================================
    inner class ChatAdapter(
        private val messages: List<ChatMessage>,
        private val markwon: Markwon
    ) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val layoutBotMessage: View = itemView.findViewById(R.id.layoutBotMessage)
            val pbBotLoading: View = itemView.findViewById(R.id.pbBotLoading)
            val tvBot: TextView = itemView.findViewById(R.id.tvBotMessage)
            val cardUserMessage: View = itemView.findViewById(R.id.cardUserMessage)
            val tvUser: TextView = itemView.findViewById(R.id.tvUserMessage)
            val cardUserImage: View = itemView.findViewById(R.id.cardUserImage)
            val ivUserImage: ImageView = itemView.findViewById(R.id.ivUserImage)
            val cardUserFile: View = itemView.findViewById(R.id.cardUserFile)
            val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val msg = messages[position]

            holder.layoutBotMessage.visibility = View.GONE
            holder.cardUserMessage.visibility = View.GONE
            holder.cardUserImage.visibility = View.GONE
            holder.cardUserFile.visibility = View.GONE

            if (msg.isUser) {
                if (msg.imageBitmap != null) {
                    holder.cardUserImage.visibility = View.VISIBLE
                    holder.ivUserImage.setImageBitmap(msg.imageBitmap)
                } else if (msg.fileUri != null) {
                    if (msg.isImage) {
                        holder.cardUserImage.visibility = View.VISIBLE
                        holder.ivUserImage.setImageURI(msg.fileUri)
                    } else {
                        holder.cardUserFile.visibility = View.VISIBLE
                        holder.tvFileName.text = msg.fileName
                    }
                }
                if (msg.message.isNotEmpty()) {
                    holder.cardUserMessage.visibility = View.VISIBLE
                    holder.tvUser.text = msg.message
                }
            } else {
                holder.layoutBotMessage.visibility = View.VISIBLE
                if (msg.isLoading) {
                    holder.pbBotLoading.visibility = View.VISIBLE
                    holder.tvBot.text = msg.message
                } else {
                    holder.pbBotLoading.visibility = View.GONE
                    markwon.setMarkdown(holder.tvBot, msg.message)
                }
            }
        }
        override fun getItemCount(): Int = messages.size
    }
}