package com.sentinel.antiscamvn.phone

import android.provider.CallLog
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R
import java.util.Date

data class CallLogItem(
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long
)

class CallLogAdapter(
    private val logs: List<CallLogItem>,
    private val onCallClick: (String) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val imgType: ImageView = v.findViewById(R.id.img_call_type)
        val txtNumber: TextView = v.findViewById(R.id.txt_log_number)
        val txtDate: TextView = v.findViewById(R.id.txt_log_date)
        val btnCall: ImageButton = v.findViewById(R.id.btn_log_call)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_call_log, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = logs[position]
        holder.txtNumber.text = item.number
        holder.txtDate.text = DateUtils.getRelativeTimeSpanString(item.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

        when (item.type) {
            CallLog.Calls.INCOMING_TYPE -> {
                holder.imgType.setImageResource(android.R.drawable.sym_call_incoming)
                holder.imgType.setColorFilter(android.graphics.Color.BLUE)
            }
            CallLog.Calls.OUTGOING_TYPE -> {
                holder.imgType.setImageResource(android.R.drawable.sym_call_outgoing)
                holder.imgType.setColorFilter(android.graphics.Color.GREEN)
            }
            CallLog.Calls.MISSED_TYPE -> {
                holder.imgType.setImageResource(android.R.drawable.sym_call_missed)
                holder.imgType.setColorFilter(android.graphics.Color.RED)
            }
            else -> holder.imgType.setImageResource(android.R.drawable.sym_call_incoming)
        }

        holder.btnCall.setOnClickListener { onCallClick(item.number) }
        holder.itemView.setOnClickListener { onCallClick(item.number) }
    }

    override fun getItemCount() = logs.size
}
