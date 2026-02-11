package com.sentinel.antiscamvn

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val images: List<Bitmap> = emptyList()
)

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val layoutAi: View = view.findViewById(R.id.layout_ai)
        val layoutUser: View = view.findViewById(R.id.layout_user)
        
        val txtAi: TextView = view.findViewById(R.id.txt_chat_ai)
        val txtUser: TextView = view.findViewById(R.id.txt_chat_user)
        
        val imagesAi: LinearLayout = view.findViewById(R.id.layout_images_ai)
        val imagesUser: LinearLayout = view.findViewById(R.id.layout_images_user)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        if (message.isUser) {
            holder.layoutUser.visibility = View.VISIBLE
            holder.layoutAi.visibility = View.GONE
            holder.txtUser.text = message.content
            renderImages(holder.imagesUser, message.images)
        } else {
            holder.layoutAi.visibility = View.VISIBLE
            holder.layoutUser.visibility = View.GONE
            holder.txtAi.text = message.content
            renderImages(holder.imagesAi, message.images)
        }
    }

    private fun renderImages(container: LinearLayout, images: List<Bitmap>) {
        container.removeAllViews()
        if (images.isNotEmpty()) {
            container.visibility = View.VISIBLE
            val previewCount = minOf(images.size, 3)
            val density = container.resources.displayMetrics.density
            val size = (100 * density).toInt()

            for (i in 0 until previewCount) {
                val imageView = ImageView(container.context).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(images[i])
                }
                container.addView(imageView)
            }

            if (images.size > 3) {
                val moreText = TextView(container.context).apply {
                    text = "+${images.size - 3}"
                    textSize = 14f
                    
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                    setTextColor(typedValue.data)
                    
                    setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                }
                container.addView(moreText)
            }
        } else {
            container.visibility = View.GONE
        }
    }

    override fun getItemCount() = messages.size
}