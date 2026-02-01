package com.sentinel.antiscamvn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.sms.SmsSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsDetailFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var edtMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var btnAttach: ImageButton? = null
    private var layoutAttachment: FrameLayout? = null
    private var imgAttachment: ImageView? = null
    private var btnRemoveAttachment: ImageButton? = null
    
    private var adapter: MessageAdapter? = null
    private val chatItems = mutableListOf<ChatItem>()
    private var currentAddress: String = ""
    private var currentName: String? = null
    private var currentAvatarPath: String? = null
    private var isArchived = false

    private var selectedAttachmentUri: Uri? = null

    private var isPickingAvatar = false 

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            if (isPickingAvatar) {
                onAvatarPicked(uri)
                isPickingAvatar = false
            } else {
                attachImage(uri)
            }
        }
        isPickingAvatar = false
    }
    
    private var onAvatarPickedCallback: ((Uri) -> Unit)? = null
    
    private fun onAvatarPicked(uri: Uri) {
        onAvatarPickedCallback?.invoke(uri)
        onAvatarPickedCallback = null
    }

    private val smsObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            com.sentinel.antiscamvn.utils.LogManager.log("SmsDetailFragment", "SMS database changed. Reloading messages.")
            loadMessages()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sms_detail, container, false)
        
        requireContext().contentResolver.registerContentObserver(
            android.provider.Telephony.Sms.CONTENT_URI, 
            true, 
            smsObserver
        )
        
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar_detail)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        currentAddress = arguments?.getString("address") ?: ""
        currentName = arguments?.getString("name")
        
        val txtTitle = view.findViewById<TextView>(R.id.txt_toolbar_title)
        txtTitle.text = if (!currentName.isNullOrEmpty()) currentName else currentAddress
        
        val isTrusted = com.sentinel.antiscamvn.utils.TrustedSenderUtils.isTrusted(currentAddress, currentName)
        view.findViewById<View>(R.id.img_verified_detail).visibility = if (isTrusted) View.VISIBLE else View.GONE

        recyclerView = view.findViewById(R.id.recycler_detail)
        edtMessage = view.findViewById(R.id.edt_message)
        
        val draftBody = arguments?.getString("draft_body")
        if (!draftBody.isNullOrEmpty()) {
            edtMessage?.setText(draftBody)
        }
        
        btnSend = view.findViewById(R.id.btn_send)
        btnAttach = view.findViewById(R.id.btn_attach)
        layoutAttachment = view.findViewById(R.id.layout_attachment_preview)
        imgAttachment = view.findViewById(R.id.img_attachment_preview)
        btnRemoveAttachment = view.findViewById(R.id.btn_remove_attachment)
        
        view.findViewById<ImageButton>(R.id.btn_call).setOnClickListener {
            initiateCall()
        }
        
        view.findViewById<ImageButton>(R.id.btn_delete).setOnClickListener {
            confirmDelete()
        }
        
        view.findViewById<ImageButton>(R.id.btn_more_info).setOnClickListener {
             showInfoBottomSheet()
        }
        
        btnRemoveAttachment?.setOnClickListener {
            removeAttachment()
        }

        recyclerView?.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        adapter = MessageAdapter(chatItems)
        recyclerView?.adapter = adapter

        btnSend?.setOnClickListener {
            val content = edtMessage?.text.toString().trim()
            if (selectedAttachmentUri != null) {
                sendMms(content, selectedAttachmentUri!!)
            } else if (content.isNotEmpty()) {
                sendMessage(content)
            }
        }
        
        btnAttach?.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        loadMessages()
        loadConversationInfo()
        
        return view
    }
    
    private fun attachImage(uri: Uri) {
        selectedAttachmentUri = uri
        layoutAttachment?.visibility = View.VISIBLE
        imgAttachment?.setImageURI(uri)
        // Ensure we can access it later
        try {
             requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { e.printStackTrace() }
    }
    
    private fun removeAttachment() {
        selectedAttachmentUri = null
        layoutAttachment?.visibility = View.GONE
        imgAttachment?.setImageDrawable(null)
    }

    private fun sendMms(body: String, imageUri: Uri) {
        // Send via intent to default SMS app
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.putExtra("address", currentAddress)
            intent.putExtra("sms_body", body)
            intent.putExtra(Intent.EXTRA_STREAM, imageUri)
            intent.type = "image/*"
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // Target default SMS app if possible
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(requireContext())
            if (defaultSmsPackage != null) {
                intent.setPackage(defaultSmsPackage)
            }
            
            startActivity(intent)
            
            // Clear attachment after handoff
            removeAttachment()
            edtMessage?.text?.clear()
            
        } catch (e: Exception) {
            Toast.makeText(context, "Không tìm thấy ứng dụng gửi tin nhắn", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadConversationInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = com.sentinel.antiscamvn.sms.SmsRepository(requireContext())
            val convos = repository.getConversations()
            val convo = convos.find { it.address == currentAddress }
            if (convo != null) {
                withContext(Dispatchers.Main) {
                    isArchived = convo.isArchived
                    if (convo.contactName != null) {
                        currentName = convo.contactName
                        view?.findViewById<TextView>(R.id.txt_toolbar_title)?.text = currentName
                    }
                    currentAvatarPath = convo.avatarPath
                }
            }
        }
    }
    
    private fun showInfoBottomSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.info_bottom_sheet, null)
        dialog.setContentView(sheetView)
        
        val btnEditInfo = sheetView.findViewById<View>(R.id.btn_add_contact)
        btnEditInfo.setOnClickListener {
             showEditAliasDialog()
             dialog.dismiss()
        }
        
        val btnArchive = sheetView.findViewById<TextView>(R.id.btn_archive)
        btnArchive.text = if (isArchived) "Bỏ lưu trữ cuộc trò chuyện" else "Lưu trữ cuộc trò chuyện"
        btnArchive.setOnClickListener {
             toggleArchive()
             dialog.dismiss()
        }
        
        val btnSystemContact = sheetView.findViewById<View>(R.id.btn_system_contact) 
        btnSystemContact?.setOnClickListener {
            addToSystemContacts()
            dialog.dismiss()
        }
        
        showButtonActions(sheetView, dialog)
        dialog.show()
    }
    
    private fun toggleArchive() {
        lifecycleScope.launch(Dispatchers.IO) {
            val repository = com.sentinel.antiscamvn.sms.SmsRepository(requireContext())
            val newState = !isArchived
            repository.archiveConversation(currentAddress, newState)
            withContext(Dispatchers.Main) {
                isArchived = newState
                val msg = if (newState) "Đã lưu trữ" else "Đã bỏ lưu trữ"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun addToSystemContacts() {
        try {
            val intent = Intent(Intent.ACTION_INSERT)
            intent.type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
            intent.putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, currentAddress)
            intent.putExtra(android.provider.ContactsContract.Intents.Insert.NAME, currentName)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở danh bạ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditAliasDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext()).create()
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_alias, null)
        dialog.setView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val edtName = dialogView.findViewById<EditText>(R.id.edt_alias_name)
        val btnSave = dialogView.findViewById<View>(R.id.btn_save)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val imgAvatar = dialogView.findViewById<ImageView>(R.id.img_avatar_preview)
        val txtInitial = dialogView.findViewById<TextView>(R.id.txt_avatar_initial)
        val btnChangeAvatar = dialogView.findViewById<View>(R.id.btn_change_avatar)
        
        edtName.setText(currentName ?: "")
        
        if (currentAvatarPath != null) {
            imgAvatar.visibility = View.VISIBLE
            txtInitial.visibility = View.GONE
            imgAvatar.setImageURI(Uri.parse(currentAvatarPath))
        } else {
             imgAvatar.visibility = View.GONE
             txtInitial.visibility = View.VISIBLE
             txtInitial.text = (currentName ?: currentAddress).take(1).uppercase()
        }
        
        onAvatarPickedCallback = { uri ->
            currentAvatarPath = uri.toString() 
            imgAvatar.visibility = View.VISIBLE
            txtInitial.visibility = View.GONE
            imgAvatar.setImageURI(uri)
            
            try {
                 requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
        }
        
        btnChangeAvatar.setOnClickListener {
             isPickingAvatar = true
             pickImageLauncher.launch("image/*")
        }
        
        btnSave.setOnClickListener {
            val newName = edtName.text.toString().trim()
            lifecycleScope.launch(Dispatchers.IO) {
                val repository = com.sentinel.antiscamvn.sms.SmsRepository(requireContext())
                repository.saveContactAlias(currentAddress, newName.ifEmpty { null }, currentAvatarPath)
                withContext(Dispatchers.Main) {
                    currentName = newName.ifEmpty { null }
                    view?.findViewById<TextView>(R.id.txt_toolbar_title)?.text = currentName ?: currentAddress
                    Toast.makeText(context, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showButtonActions(sheetView: View, dialog: com.google.android.material.bottomsheet.BottomSheetDialog) {
        sheetView.findViewById<View>(R.id.btn_block).setOnClickListener {
            Toast.makeText(context, "Đã thêm vào danh sách chặn (Demo)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        sheetView.findViewById<View>(R.id.btn_make_default).setOnClickListener {
             requestDefaultSmsApp()
             dialog.dismiss()
        }
    }

    private fun requestDefaultSmsApp() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = requireContext().getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                    Toast.makeText(context, "Ứng dụng đã là mặc định", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                    startActivityForResult(intent, 102)
                }
            }
        } else {
            val intent = android.content.Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, requireContext().packageName)
            startActivity(intent)
        }
    }
    
    private fun initiateCall() {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:$currentAddress")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể thực hiện cuộc gọi", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun confirmDelete() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Xóa cuộc trò chuyện?")
            .setMessage("Cuộc trò chuyện này sẽ bị xóa vĩnh viễn.")
            .setPositiveButton("Xóa") { _, _ ->
                 deleteConversation()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    private fun deleteConversation() {
        if (currentThreadId == -1L) {
             Toast.makeText(context, "Không thể xác định cuộc trò chuyện để xóa", Toast.LENGTH_SHORT).show()
             return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val repository = com.sentinel.antiscamvn.sms.SmsRepository(requireContext())
            val success = repository.deleteConversation(currentThreadId)
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(context, "Đã xóa cuộc trò chuyện", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    Toast.makeText(context, "Xóa thất bại (Cần đặt làm ứng dụng SMS mặc định)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private var currentThreadId: Long = -1

    private fun loadMessages() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val repository = com.sentinel.antiscamvn.sms.SmsRepository(requireContext())
            
            if (currentThreadId == -1L) {
                 currentThreadId = arguments?.getLong("thread_id", -1L) ?: -1L
            }

            if ((currentThreadId == -1L || currentThreadId == 0L) && currentAddress.isNotEmpty()) {
                 currentThreadId = repository.getThreadIdForAddress(currentAddress) ?: -1L
            }
            
            // Mark as read by ADDRESS and update Local DB
            if (currentAddress.isNotEmpty()) {
                 repository.markAddressAsRead(currentAddress)
            } else if (currentThreadId != -1L) {
                 repository.markAsRead(currentThreadId)
            }
            
            val messages = repository.getMessagesForThread(currentThreadId, currentAddress)

            withContext(Dispatchers.Main) {
                chatItems.clear()
                var lastDate = 0L
                var addedNewIndicator = false
                
                val sortedMessages = messages.sortedBy { it.date }
                
                sortedMessages.forEach { msg ->
                     if (!isSameDay(lastDate, msg.date)) {
                         chatItems.add(ChatItem.DateHeader(msg.date))
                         lastDate = msg.date
                     }
                     
                     // Check 'read' property logic
                     if (!addedNewIndicator && !msg.isSent && !msg.read) {
                         chatItems.add(ChatItem.NewMessageIndicator)
                         addedNewIndicator = true
                     }
                     
                     chatItems.add(ChatItem.Message(msg))
                }
                
                adapter?.notifyDataSetChanged()
                if (chatItems.isNotEmpty()) {
                    recyclerView?.scrollToPosition(chatItems.size - 1)
                }
            }
        }
    }
    
    private fun isSameDay(date1: Long, date2: Long): Boolean {
        if (date1 == 0L) return false
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(date1)) == fmt.format(java.util.Date(date2))
    }

    private fun sendMessage(content: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
             requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 101)
             return
        }
        
        val isDefault = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = requireContext().getSystemService(android.app.role.RoleManager::class.java)
            roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            Telephony.Sms.getDefaultSmsPackage(requireContext()) == requireContext().packageName
        } else {
            true 
        }
        
        if (!isDefault) {
            Toast.makeText(context, "Lưu ý: Bạn nên đặt ứng dụng làm mặc định để tin nhắn được lưu ổn định hơn", Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val sender = SmsSender(requireContext())
            val success = sender.sendSms(currentAddress, content)

            withContext(Dispatchers.Main) {
                if (success) {
                    val newMsg = SmsMessage(
                        id = System.currentTimeMillis().toString(),
                        body = content,
                        date = System.currentTimeMillis(),
                        isSent = true,
                        read = true,
                        type = android.provider.Telephony.Sms.MESSAGE_TYPE_SENT
                    )
                    
                    if (chatItems.isEmpty()) {
                        chatItems.add(ChatItem.DateHeader(newMsg.date))
                    } else {
                        val lastItem = chatItems.last()
                        if (lastItem is ChatItem.Message) {
                            if (!isSameDay(lastItem.message.date, newMsg.date)) {
                                chatItems.add(ChatItem.DateHeader(newMsg.date))
                            }
                        }
                    }
                    
                    chatItems.add(ChatItem.Message(newMsg))
                    adapter?.notifyItemInserted(chatItems.size - 1)
                    recyclerView?.scrollToPosition(chatItems.size - 1)
                    edtMessage?.text?.clear()
                    Toast.makeText(context, "Đã gửi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gửi thất bại. Kiểm tra sóng hoặc tài khoản.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().contentResolver.unregisterContentObserver(smsObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}