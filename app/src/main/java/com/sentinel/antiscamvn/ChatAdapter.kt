package com.sentinel.antiscamvn

import android.graphics.Bitmap
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val image: Bitmap? = null
)

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view.findViewById(R.id.layout_message_container)
        val txtContent: TextView = view.findViewById(R.id.txt_message_content)
        val imgAttachment: ImageView = view.findViewById(R.id.img_attachment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        if (message.isUser) {
            holder.container.gravity = Gravity.END
            holder.txtContent.setBackgroundResource(R.drawable.bg_message_user)
            holder.txtContent.setTextColor(0xFFFFFFFF.toInt())
        } else {
            holder.container.gravity = Gravity.START
            holder.txtContent.setBackgroundResource(R.drawable.bg_message_bot)
            holder.txtContent.setTextColor(0xFF333333.toInt())
        }

        holder.txtContent.text = message.content

        if (message.image != null) {
            holder.imgAttachment.visibility = View.VISIBLE
            holder.imgAttachment.setImageBitmap(message.image)
        } else {
            holder.imgAttachment.visibility = View.GONE
        }
    }

    override fun getItemCount() = messages.size
}
