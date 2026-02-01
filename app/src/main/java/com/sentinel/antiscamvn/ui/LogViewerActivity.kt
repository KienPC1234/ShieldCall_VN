package com.sentinel.antiscamvn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.utils.LogManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        val recycler = findViewById<RecyclerView>(R.id.recycler_log_files)
        val txtContent = findViewById<TextView>(R.id.txt_log_content)
        val scrollContent = findViewById<View>(R.id.scroll_log_content)
        val txtContentTitle = findViewById<View>(R.id.txt_log_content_title)
        val btnClose = findViewById<Button>(R.id.btn_close_log)
        val btnCopy = findViewById<Button>(R.id.btn_copy_log)

        btnClose.setOnClickListener { finish() }

        val files = LogManager.getLogFiles(this)
        
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = LogFileAdapter(files) { file ->
            // Show content
            recycler.visibility = View.GONE
            scrollContent.visibility = View.VISIBLE
            txtContentTitle.visibility = View.VISIBLE
            btnCopy.visibility = View.VISIBLE
            
            val content = LogManager.readLogFile(file)
            txtContent.text = content
            
            btnCopy.setOnClickListener {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("App Log", content)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Đã copy log vào bộ nhớ tạm", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // Change button behavior to "Back to list"
            btnClose.text = "Quay lại danh sách"
            btnClose.setOnClickListener {
                recycler.visibility = View.VISIBLE
                scrollContent.visibility = View.GONE
                txtContentTitle.visibility = View.GONE
                btnCopy.visibility = View.GONE
                btnClose.text = "Đóng"
                btnClose.setOnClickListener { finish() }
            }
        }
    }

    class LogFileAdapter(
        private val files: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<LogFileAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtName: TextView = v.findViewById(android.R.id.text1)
            val txtDate: TextView = v.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            
            // Add "Newest" tag to the first item
            if (position == 0) {
                val spannable = android.text.SpannableString("${file.name}  [MỚI NHẤT]")
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.RED),
                    file.name.length + 2,
                    spannable.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    file.name.length + 2,
                    spannable.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                holder.txtName.text = spannable
            } else {
                holder.txtName.text = file.name
            }
            
            val date = Date(file.lastModified())
            holder.txtDate.text = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(date)
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount() = files.size
    }
}
