package com.example.khetmitra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

class ChatbotActivity : AppCompatActivity() {

    private val chatList = ArrayList<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var etInput: EditText
    private lateinit var recyclerChat: RecyclerView
    private lateinit var btnAction: ImageView

    // Camera Launcher
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                addMessageWithAttachment("[Photo Captured]", true, bitmap = imageBitmap)
            }
        }
    }

    // Gallery Launcher
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            addMessageWithAttachment("[Photo Selected]", true, uri = uri)
        }
    }

    // File Launcher
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = getFileName(uri)
            // Mark isImage = false so the adapter knows to show the file bubble
            addMessageWithAttachment("[File Attached]", true, uri = uri, isImage = false, fileName = name)
        }
    }


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) openSystemCamera()
        else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        etInput = findViewById(R.id.etMessageInput)
        recyclerChat = findViewById(R.id.recyclerChat)
        btnAction = findViewById(R.id.btnMic)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnPlus = findViewById<View>(R.id.btnPlus)

        btnPlus.setOnClickListener { showAttachmentOptions() }

        chatAdapter = ChatAdapter(chatList)
        recyclerChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerChat.adapter = chatAdapter

        addMessage("Namaste! I am your KhetMitra AI. Ask me anything.", false)

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString().trim().isNotEmpty()) {
                    btnAction.setImageResource(android.R.drawable.ic_menu_send)
                    btnAction.tag = "SEND"
                } else {
                    btnAction.setImageResource(android.R.drawable.ic_btn_speak_now)
                    btnAction.tag = "MIC"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnAction.setOnClickListener {
            if (btnAction.tag == "SEND") sendMessage()
            else Toast.makeText(this, "Voice typing coming soon...", Toast.LENGTH_SHORT).show()
        }

        etInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                sendMessage()
                return@setOnEditorActionListener true
            }
            false
        }
        btnBack.setOnClickListener { finish() }
    }

    private fun showAttachmentOptions() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_chat_attachments, null)
        bottomSheetDialog.setContentView(view)

        // Camera
        view.findViewById<View>(R.id.optionCamera).setOnClickListener {
            bottomSheetDialog.dismiss()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openSystemCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Gallery
        view.findViewById<View>(R.id.optionGallery).setOnClickListener {
            bottomSheetDialog.dismiss()
            pickImageLauncher.launch("image/*")
        }

        // File
        view.findViewById<View>(R.id.optionFile).setOnClickListener {
            bottomSheetDialog.dismiss()
            pickFileLauncher.launch("*/*")
        }

        bottomSheetDialog.show()
    }

    private fun openSystemCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Unknown File"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }

    private fun sendMessage() {
        val query = etInput.text.toString().trim()
        if (query.isNotEmpty()) {
            addMessage(query, true)
            etInput.text.clear()
            fetchAIResponse(query)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        chatList.add(ChatMessage(text, isUser))
        updateList()
    }

    private fun addMessageWithAttachment(
        text: String,
        isUser: Boolean,
        bitmap: Bitmap? = null,
        uri: Uri? = null,
        isImage: Boolean = true,
        fileName: String = ""
    ) {
        chatList.add(ChatMessage(text, isUser, bitmap, uri, isImage, fileName))
        updateList()

        recyclerChat.postDelayed({
            addMessage("I received your ${if (isImage) "photo" else "file"}. Analyzing...", false)
        }, 1000)
    }

    private fun updateList() {
        chatAdapter.notifyItemInserted(chatList.size - 1)
        recyclerChat.scrollToPosition(chatList.size - 1)
    }

    private fun fetchAIResponse(query: String) {
        recyclerChat.postDelayed({
            val dummyResponse = "I understood: '$query'. \n\n(AI Model Placeholder)"
            addMessage(dummyResponse, false)
        }, 1000)
    }

    inner class ChatAdapter(private val messages: List<ChatMessage>) :
        RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvBot: TextView = itemView.findViewById(R.id.tvBotMessage)
            val tvUser: TextView = itemView.findViewById(R.id.tvUserMessage)
            val cardUserImage: View = itemView.findViewById(R.id.cardUserImage)
            val ivUserImage: ImageView = itemView.findViewById(R.id.ivUserImage)
            val cardUserFile: View = itemView.findViewById(R.id.cardUserFile)
            val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val msg = messages[position]

            holder.tvBot.visibility = View.GONE
            holder.tvUser.visibility = View.GONE
            holder.cardUserImage.visibility = View.GONE
            holder.cardUserFile.visibility = View.GONE

            if (msg.isUser) {
                if (msg.imageBitmap != null) {
                    holder.cardUserImage.visibility = View.VISIBLE
                    holder.ivUserImage.setImageBitmap(msg.imageBitmap)
                }
                else if (msg.fileUri != null) {
                    if (msg.isImage) {
                        holder.cardUserImage.visibility = View.VISIBLE
                        holder.ivUserImage.setImageURI(msg.fileUri)
                    } else {
                        holder.cardUserFile.visibility = View.VISIBLE
                        holder.tvFileName.text = msg.fileName
                    }
                }
                else {
                    holder.tvUser.visibility = View.VISIBLE
                    holder.tvUser.text = msg.message
                }
            } else {
                holder.tvBot.visibility = View.VISIBLE
                holder.tvBot.text = msg.message
            }
        }

        override fun getItemCount(): Int = messages.size
    }
}