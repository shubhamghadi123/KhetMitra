package com.example.khetmitra

import android.graphics.Bitmap
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.util.UUID

object ChatHistoryManager {

    private fun currentUserId(): String =
        SupabaseManager.client.auth.currentUserOrNull()?.id ?: ""

    suspend fun uploadImage(bitmap: Bitmap): String? {
        return try {
            val userId = currentUserId()
            val fileName = "chat-images/$userId/${UUID.randomUUID()}.jpg"

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val bytes = baos.toByteArray()

            val bucket = SupabaseManager.client.storage.from("chat-images")
            bucket.upload(fileName, bytes, upsert = false)

            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createSession(title: String = "New Chat"): String {
        val userId = currentUserId()
        val result = SupabaseManager.client.postgrest["chat_sessions"]
            .insert(mapOf("farmer_id" to userId, "title" to title)) {
                select()
            }.decodeSingle<ChatSession>()
        return result.id
    }

    suspend fun getSessions(): List<ChatSession> {
        val userId = currentUserId()
        return SupabaseManager.client.postgrest["chat_sessions"]
            .select {
                filter { eq("farmer_id", userId) }
                order("updated_at", Order.DESCENDING)
            }.decodeList()
    }

    suspend fun getMessages(sessionId: String): List<ChatMessageEntity> {
        return SupabaseManager.client.postgrest["chat_messages"]
            .select {
                filter { eq("session_id", sessionId) }
                order("created_at", Order.ASCENDING)
            }.decodeList()
    }

    suspend fun saveMessage(
        sessionId: String,
        role: String,
        text: String,
        imageUrl: String? = null,
        fileName: String? = null
    ) {
        SupabaseManager.client.postgrest["chat_messages"]
            .insert(buildMap {
                put("session_id", sessionId)
                put("role", role)
                put("text", text)
                if (!imageUrl.isNullOrBlank()) put("image_url", imageUrl)
                if (!fileName.isNullOrBlank()) put("file_name", fileName)
            })

        SupabaseManager.client.postgrest["chat_sessions"]
            .update(mapOf("updated_at" to java.time.Instant.now().toString())) {
                filter { eq("id", sessionId) }
            }
    }

    suspend fun updateTitle(sessionId: String, firstMessage: String) {
        val title = if (firstMessage.length > 40) {
            firstMessage.take(40).trimEnd() + "…"
        } else {
            firstMessage
        }
        SupabaseManager.client.postgrest["chat_sessions"]
            .update(mapOf("title" to title)) {
                filter { eq("id", sessionId) }
            }
    }

    suspend fun deleteSession(sessionId: String) {
        SupabaseManager.client.postgrest["chat_sessions"]
            .delete { filter { eq("id", sessionId) } }
    }
}