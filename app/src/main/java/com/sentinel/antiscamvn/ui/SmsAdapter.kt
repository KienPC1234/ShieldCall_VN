package com.sentinel.antiscamvn.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R
import java.util.Date

class SmsAdapter(
    private val list: List<SmsConversation>,
    private val onItemClick: (SmsConversation) -> Unit
) : RecyclerView.Adapter<SmsAdapter.SmsViewHolder>() {
    class SmsViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val txtAvatar: TextView = v.findViewById(R.id.txt_avatar)
        val imgAvatarImage: android.widget.ImageView = v.findViewById(R.id.img_avatar_image)
        val txtSender: TextView = v.findViewById(R.id.txt_sender)
        val txtPreview: TextView = v.findViewById(R.id.txt_preview)
        val txtDate: TextView = v.findViewById(R.id.txt_date)
        val txtUnreadCount: TextView = v.findViewById(R.id.txt_unread_count)
        val imgVerified: android.widget.ImageView = v.findViewById(R.id.img_verified)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_sms_list, parent, false)
        return SmsViewHolder(v)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val item = list[position]
        val context = holder.itemView.context
        
        // Sender & Preview
        if (!item.contactName.isNullOrEmpty()) {
             holder.txtSender.text = item.contactName
        } else {
             holder.txtSender.text = item.address
        }
        
        holder.txtPreview.text = item.body

        // Avatar Logic
        if (!item.avatarPath.isNullOrEmpty()) {
            try {
                holder.imgAvatarImage.setImageURI(android.net.Uri.parse(item.avatarPath))
                holder.imgAvatarImage.visibility = View.VISIBLE
                holder.txtAvatar.visibility = View.GONE
            } catch (e: Exception) {
                holder.imgAvatarImage.visibility = View.GONE
                holder.txtAvatar.visibility = View.VISIBLE
            }
        } else {
            holder.imgAvatarImage.visibility = View.GONE
            holder.txtAvatar.visibility = View.VISIBLE
        }

        // Avatar Text (First letter)
        val nameForAvatar = item.contactName ?: item.address
        val initial = nameForAvatar.firstOrNull()?.toString()?.uppercase() ?: "?"
        holder.txtAvatar.text = initial

        // Smart Date
        holder.txtDate.text = DateUtils.getRelativeTimeSpanString(
            item.date,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )

        // Unread Indicator (read == 0 is unread)
        val isUnread = item.unreadCount > 0 || (item.read == 0 && item.unreadCount == 0)
        
        if (isUnread) {
            holder.txtUnreadCount.visibility = View.VISIBLE
            holder.txtUnreadCount.text = if (item.unreadCount > 0) {
                if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
            } else "1"
            
            holder.txtSender.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
            holder.txtSender.typeface = android.graphics.Typeface.DEFAULT_BOLD
            
            holder.txtPreview.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
            holder.txtPreview.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else {
            holder.txtUnreadCount.visibility = View.GONE
            
            holder.txtSender.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
            holder.txtSender.typeface = android.graphics.Typeface.DEFAULT_BOLD // Keep sender bold but lighter
            
            holder.txtPreview.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
            holder.txtPreview.typeface = android.graphics.Typeface.DEFAULT
        }

        // Trusted sender verification
        val isTrusted = com.sentinel.antiscamvn.utils.TrustedSenderUtils.isTrusted(item.address, item.contactName)
        holder.imgVerified.visibility = if (isTrusted) View.VISIBLE else View.GONE
        
        // Long Click Actions
        holder.itemView.setOnLongClickListener { view ->
            val popup = android.widget.PopupMenu(view.context, view)
            popup.menu.add(0, 1, 0, "Xóa cuộc trò chuyện")
            popup.menu.add(0, 2, 0, "Chặn số này")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> { // Delete
                        android.widget.Toast.makeText(view.context, "Tính năng xóa đang phát triển", android.widget.Toast.LENGTH_SHORT).show()
                        true
                    }
                    2 -> { // Block
                        android.widget.Toast.makeText(view.context, "Tính năng block đang phát triển", android.widget.Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
            true
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = list.size
}