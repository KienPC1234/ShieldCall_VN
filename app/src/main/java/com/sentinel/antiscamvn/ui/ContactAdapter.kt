package com.sentinel.antiscamvn.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R

data class ContactItem(
    val id: String,
    val lookupKey: String?,
    val displayName: String,
    val phoneNumber: String,
    val avatarPath: String? = null,
    val isBlocked: Boolean = false,
    val aiTag: String? = null
)

class ContactAdapter(
    private val contacts: List<ContactItem>,
    private val onItemClick: (ContactItem) -> Unit,
    private val onCallClick: (ContactItem) -> Unit,
    private val onLongClick: (ContactItem) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txt_name)
        val txtPhone: TextView = view.findViewById(R.id.txt_phone)
        val txtAvatar: TextView = view.findViewById(R.id.txt_avatar)
        val imgAvatar: ImageView = view.findViewById(R.id.img_avatar_image)
        val btnCall: ImageButton = view.findViewById(R.id.btn_call)
        val txtTag: TextView = view.findViewById(R.id.txt_tag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.txtName.text = contact.displayName
        holder.txtPhone.text = contact.phoneNumber
        
        // Tag Logic
        val context = holder.itemView.context
        if (contact.isBlocked) {
            holder.txtTag.visibility = View.VISIBLE
            holder.txtTag.text = "BLOCKED"
            holder.txtTag.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.danger_tag_background))
            holder.txtTag.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.danger_tag_text))
        } else if (!contact.aiTag.isNullOrEmpty()) {
            holder.txtTag.visibility = View.VISIBLE
            holder.txtTag.text = contact.aiTag
            holder.txtTag.background.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.tag_background))
            holder.txtTag.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.tag_text))
        } else {
            holder.txtTag.visibility = View.GONE
        }
        
        if (contact.isBlocked) {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorError, typedValue, true)
            holder.txtName.setTextColor(typedValue.data)
        } else {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            holder.txtName.setTextColor(typedValue.data)
        }
        
        // Avatar logic
        if (contact.avatarPath != null) {
            holder.imgAvatar.visibility = View.VISIBLE
            holder.txtAvatar.visibility = View.GONE
            try {
                holder.imgAvatar.setImageURI(Uri.parse(contact.avatarPath))
            } catch (e: Exception) {
                holder.imgAvatar.visibility = View.GONE
                holder.txtAvatar.visibility = View.VISIBLE
                holder.txtAvatar.text = contact.displayName.take(1).uppercase()
            }
        } else {
            holder.imgAvatar.visibility = View.GONE
            holder.txtAvatar.visibility = View.VISIBLE
            holder.txtAvatar.text = if (contact.displayName.isNotEmpty()) contact.displayName.take(1).uppercase() else "?"
        }

        holder.itemView.setOnClickListener { onItemClick(contact) }
        holder.itemView.setOnLongClickListener { 
            onLongClick(contact)
            true 
        }
        holder.btnCall.setOnClickListener { onCallClick(contact) }
    }

    override fun getItemCount() = contacts.size
}