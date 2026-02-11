package com.sentinel.antiscamvn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SmsConversation(
    val address: String,
    val contactName: String?,
    val body: String,
    val date: Long,
    val read: Int,
    val threadId: Long,
    val unreadCount: Int = 0,
    val avatarPath: String? = null,
    val isArchived: Boolean = false,
    val aiTag: String? = null,
    val riskLevel: String? = null
)

class SmsFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var txtEmpty: View? = null
    private var searchView: androidx.appcompat.widget.SearchView? = null
    private var tabLayout: TabLayout? = null
    
    private val fullSmsList = mutableListOf<SmsConversation>() // Stores all data
    private val displayedSmsList = mutableListOf<SmsConversation>() // Stores filtered data
    
    private var adapter: SmsAdapter? = null
    private var currentTab = 0 // 0: Messages, 1: Archived

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadSms()
        } else {
            Toast.makeText(context, "Cần quyền đọc tin nhắn để hiển thị danh sách", Toast.LENGTH_SHORT).show()
            updateEmptyState()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_sms, container, false)
        recyclerView = view.findViewById(R.id.recycler_sms)
        txtEmpty = view.findViewById(R.id.txt_empty)
        searchView = view.findViewById(R.id.search_view)
        tabLayout = view.findViewById(R.id.tab_layout)
        
        // Setup Tabs
        tabLayout?.addTab(tabLayout?.newTab()?.setText("Tin nhắn")!!)
        tabLayout?.addTab(tabLayout?.newTab()?.setText("Lưu trữ")!!)
        
        tabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                filterList(searchView?.query?.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Remove underline from search view to look cleaner
        try {
            val plateId = searchView?.context?.resources?.getIdentifier("android:id/search_plate", null, null) ?: 0
            if (plateId != 0) {
                 searchView?.findViewById<View>(plateId)?.background = null
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        recyclerView?.layoutManager = LinearLayoutManager(context)
        adapter = SmsAdapter(displayedSmsList) { conversation ->
            // Use navigation component with Bundle manually to avoid build errors
            try {
                val bundle = Bundle().apply {
                    putString("address", conversation.address)
                    putString("name", conversation.contactName) // Pass name to detail
                    putLong("thread_id", conversation.threadId)
                }
                findNavController().navigate(R.id.action_sms_to_detail, bundle)
            } catch (e: Exception) {
                // Fallback
                Toast.makeText(context, "Clicked: ${conversation.address}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
        recyclerView?.adapter = adapter
        
        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterList(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        // New Chat FAB
        view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_new_chat).setOnClickListener {
             startActivity(Intent(requireContext(), com.sentinel.antiscamvn.sms.ComposeSmsActivity::class.java))
        }

        checkPermissionAndLoad()
        return view
    }
    
    private fun showNewChatDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Tin nhắn mới")
        
        val input = android.widget.EditText(context)
        input.hint = "Nhập số điện thoại..."
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        builder.setView(input)
        
        builder.setPositiveButton("Chat") { _, _ ->
            val number = input.text.toString().trim()
            if (number.isNotEmpty()) {
                val bundle = Bundle().apply {
                    putString("address", number)
                }
                findNavController().navigate(R.id.action_sms_to_detail, bundle)
            }
        }
        builder.setNegativeButton("Hủy", null)
        builder.show()
    }
    
    private val smsObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            com.sentinel.antiscamvn.utils.LogManager.log("SmsFragment", "SMS database changed. Reloading list.")
            loadSms()
        }
    }

    override fun onResume() {
        super.onResume()
        // Register observer
        try {
            context?.contentResolver?.registerContentObserver(
                android.provider.Telephony.Sms.CONTENT_URI,
                true,
                smsObserver
            )
            com.sentinel.antiscamvn.utils.LogManager.log("SmsFragment", "Registered ContentObserver")
            // Also reload on resume to be safe
            loadSms()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister observer
        try {
            context?.contentResolver?.unregisterContentObserver(smsObserver)
            com.sentinel.antiscamvn.utils.LogManager.log("SmsFragment", "Unregistered ContentObserver")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun filterList(query: String?) {
        val searchText = query?.lowercase() ?: ""
        displayedSmsList.clear()
        
        // Filter by Tab first
        val tabFiltered = if (currentTab == 1) {
            fullSmsList.filter { it.isArchived }
        } else {
            fullSmsList.filter { !it.isArchived }
        }

        if (searchText.isEmpty()) {
            displayedSmsList.addAll(tabFiltered)
        } else {
            displayedSmsList.addAll(tabFiltered.filter { 
                it.address.lowercase().contains(searchText) || 
                it.body.lowercase().contains(searchText) ||
                (it.contactName?.lowercase()?.contains(searchText) == true)
            })
        }
        adapter?.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun checkPermissionAndLoad() {
        val context = context ?: return
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED -> {
                loadSms()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }

    private fun loadSms() {
        // Load in background
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = com.sentinel.antiscamvn.sms.SmsRepository(requireContext())
            val conversations = repository.getConversations()

            withContext(Dispatchers.Main) {
                fullSmsList.clear()
                fullSmsList.addAll(conversations)
                
                // Reset search on reload or apply current filter
                val currentQuery = searchView?.query?.toString()
                filterList(currentQuery)
            }
        }
    }

    private fun updateEmptyState() {
        val emptyText = if (currentTab == 1) "Chưa có cuộc trò chuyện lưu trữ" else "Chưa có tin nhắn nào"
        (txtEmpty as? TextView)?.text = emptyText

        if (displayedSmsList.isEmpty()) {
            recyclerView?.visibility = View.GONE
            txtEmpty?.visibility = View.VISIBLE
        } else {
            recyclerView?.visibility = View.VISIBLE
            txtEmpty?.visibility = View.GONE
        }
    }
}