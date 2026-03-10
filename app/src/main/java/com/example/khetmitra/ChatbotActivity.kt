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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.mlkit.nl.translate.TranslateLanguage
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
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
    private var selectedImageBitmap: Bitmap? = null
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""
    private var selectedFileBytes: ByteArray? = null
    private var selectedFileMimeType: String = ""

    private lateinit var generativeModel: GenerativeModel
    private lateinit var markwon: Markwon
    private var currentLangCode: String = TranslateLanguage.ENGLISH

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

        markwon = Markwon.create(this)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        val requestedLanguage = getLanguageName(currentLangCode)

        generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content {
                text("You are KhetMitra AI, an expert agricultural assistant. " +
                        "You MUST always provide your final response in fluent $requestedLanguage. " +
                        "Provide concise, practical advice for farmers regarding crop diseases, pests, and soil health. " +
                        "Use Markdown formatting like bolding and bullet points to make it easy to read.")
            }
        )

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

        findViewById<TextView>(R.id.tvHeaderTitle)?.text = t("Chat")
        etInput.hint = t("Ask anything...")
        chatAdapter = ChatAdapter(chatList, markwon)
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = chatAdapter

        addMessage(t("Namaste! I am your KhetMitra AI. Ask me anything."), isUser = false)

        btnPlus.setOnClickListener { showAttachmentOptions() }
        btnRemoveImage.setOnClickListener { clearPreview() }
        btnBack.setOnClickListener { finish() }

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
    }

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
                val response = if (bitmap != null) {
                    val inputContent = content {
                        image(bitmap)
                        text(query.ifEmpty { t("Analyze this agricultural image and provide details.") })
                    }
                    generativeModel.generateContent(inputContent)
                } else if (fileBytes != null) {
                    val inputContent = content {
                        blob(mimeType, fileBytes)
                        text(query.ifEmpty { t("Analyze this document and summarize it simply for a farmer.") })
                    }
                    generativeModel.generateContent(inputContent)
                } else {
                    generativeModel.generateContent(query)
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
        chatList.add(
            ChatMessage(
                message = text,
                isUser = isUser,
                imageBitmap = bitmap,
                fileUri = fileUri,
                isImage = isImage,
                fileName = fileName
            )
        )
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