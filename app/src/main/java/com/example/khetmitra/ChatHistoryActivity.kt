package com.example.khetmitra

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatHistoryActivity : AppCompatActivity() {
    private val sessionList = mutableListOf<ChatSession>()
    private lateinit var adapter: DrawerSessionAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private var currentLangCode: String = TranslateLanguage.ENGLISH

    private fun t(text: String): String {
        if (currentLangCode == TranslateLanguage.ENGLISH) return text
        return TranslationHelper.getManualTranslation(text, currentLangCode) ?: text
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_history)

        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        currentLangCode = prefs.getString("Language", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        recycler = findViewById(R.id.recyclerSessions)
        layoutEmpty = findViewById(R.id.layoutEmpty)

        adapter = DrawerSessionAdapter(
            sessions = sessionList,
            onSessionClick = { session -> openChat(session.id) },
            onDeleteClick = { session -> confirmDelete(session) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<ExtendedFloatingActionButton>(R.id.fabNewChat).setOnClickListener {
            openChat(null)
        }

        if (currentLangCode != TranslateLanguage.ENGLISH) {
            window.decorView.post {
                TranslationHelper.translateViewHierarchy(window.decorView.rootView, currentLangCode) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadSessions() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sessions = ChatHistoryManager.getSessions()
                withContext(Dispatchers.Main) {
                    sessionList.clear()
                    sessionList.addAll(sessions)
                    adapter.notifyDataSetChanged()
                    layoutEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatHistoryActivity, t("Failed to load history"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openChat(sessionId: String?) {
        val intent = Intent(this, ChatbotActivity::class.java)
        if (sessionId != null) {
            intent.putExtra("SESSION_ID", sessionId)
        }
        startActivity(intent)
    }

    private fun confirmDelete(session: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle(t("Delete Chat"))
            .setMessage("${t("Delete")} ${session.title}? ${t("This cannot be undone.")}")
            .setPositiveButton(t("Delete")) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        ChatHistoryManager.deleteSession(session.id)
                        withContext(Dispatchers.Main) { loadSessions() }
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton(t("Cancel"), null)
            .show()
    }
}