package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class ChatAdapter(
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