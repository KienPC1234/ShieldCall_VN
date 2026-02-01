package com.sentinel.antiscamvn.ui

import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsMessage(
    val id: String,
    val body: String,
    val date: Long,
    val isSent: Boolean, // True if sent by me (or failed), False if received
    val read: Boolean = true,
    val type: Int = 1 // Telephony.Sms.MESSAGE_TYPE_INBOX
)

class MessageAdapter(private val list: List<ChatItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
        private const val TYPE_DATE_HEADER = 3
        private const val TYPE_NEW_INDICATOR = 4
    }

    class SentViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val txtBody: TextView = v.findViewById(R.id.txt_message_body)
        val txtTime: TextView = v.findViewById(R.id.txt_message_time)
        val txtStatus: TextView = v.findViewById(R.id.txt_message_status)
    }

    class ReceivedViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val txtBody: TextView = v.findViewById(R.id.txt_message_body)
        val txtTime: TextView = v.findViewById(R.id.txt_message_time)
    }

    class DateHeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val txtDate: TextView = v.findViewById(R.id.txt_date_header)
    }

    class NewIndicatorViewHolder(v: View) : RecyclerView.ViewHolder(v)

    override fun getItemViewType(position: Int): Int {
        return when (val item = list[position]) {
            is ChatItem.Message -> if (item.message.isSent) TYPE_SENT else TYPE_RECEIVED
            is ChatItem.DateHeader -> TYPE_DATE_HEADER
            is ChatItem.NewMessageIndicator -> TYPE_NEW_INDICATOR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SENT -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_sent, parent, false)
                SentViewHolder(v)
            }
            TYPE_RECEIVED -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_received, parent, false)
                ReceivedViewHolder(v)
            }
            TYPE_DATE_HEADER -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(v)
            }
            TYPE_NEW_INDICATOR -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_new_messages_indicator, parent, false)
                NewIndicatorViewHolder(v)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = list[position]) {
            is ChatItem.Message -> {
                val sms = item.message
                val date = Date(sms.date)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)

                if (holder is SentViewHolder) {
                    holder.txtBody.text = sms.body
                    holder.txtTime.text = timeFormat
                    
                    if (sms.type == Telephony.Sms.MESSAGE_TYPE_FAILED || sms.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                        holder.txtStatus.visibility = View.VISIBLE
                        holder.txtStatus.text = "Lỗi gửi"
                        holder.txtStatus.setTextColor(0xFFFF0000.toInt()) // Red
                    } else {
                        holder.txtStatus.visibility = View.GONE 
                    }
                } else if (holder is ReceivedViewHolder) {
                    holder.txtBody.text = sms.body
                    holder.txtTime.text = timeFormat
                }
            }
            is ChatItem.DateHeader -> {
                if (holder is DateHeaderViewHolder) {
                    val date = Date(item.date)
                    holder.txtDate.text = formatHeaderDate(date)
                }
            }
            is ChatItem.NewMessageIndicator -> {
                // No binding needed
            }
        }
    }

    private fun formatHeaderDate(date: Date): String {
        val now = Date()
        val calendar = java.util.Calendar.getInstance()
        calendar.time = now
        val todayYear = calendar.get(java.util.Calendar.YEAR)
        val todayDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)

        calendar.time = date
        val dateYear = calendar.get(java.util.Calendar.YEAR)
        val dateDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)

        return if (todayYear == dateYear && todayDay == dateDay) {
            "Hôm nay"
        } else if (todayYear == dateYear && todayDay - 1 == dateDay) {
            "Hôm qua"
        } else {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
        }
    }

    override fun getItemCount() = list.size
}
