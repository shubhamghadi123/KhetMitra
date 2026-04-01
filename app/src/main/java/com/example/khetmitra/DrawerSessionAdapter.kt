package com.example.khetmitra

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView

class DrawerSessionAdapter(
    private val sessions: List<ChatSession>,
    private var activeSessionId: String? = null,
    private val onSessionClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<DrawerSessionAdapter.ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun setActiveSession(id: String?) {
        activeSessionId = id
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvSessionTitle)
        val btnDelete: ImageView = view.findViewById(R.id.btnDeleteSession)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drawer_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvTitle.text = session.title

        val isActive = session.id == activeSessionId
        holder.itemView.setBackgroundColor(
            if (isActive) "#F0FDF4".toColorInt() else Color.TRANSPARENT
        )
        holder.tvTitle.setTextColor(
            Color.parseColor(if (isActive) "#166534" else "#1A3C2E")
        )

        holder.itemView.setOnClickListener { onSessionClick(session) }
        holder.btnDelete.setOnClickListener { onDeleteClick(session) }
    }

    override fun getItemCount() = sessions.size
}