package com.sentinel.antiscamvn.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.sentinel.antiscamvn.MainActivity
import com.sentinel.antiscamvn.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeSmsActivity : AppCompatActivity() {

    private lateinit var edtRecipient: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabStartChat: ExtendedFloatingActionButton
    private val suggestionList = mutableListOf<ContactItem>()
    private val adapter = ContactAdapter(suggestionList) { contact ->
        openChat(contact.phoneNumber)
    }

    private var searchJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CONTACTS] == true || 
            permissions[Manifest.permission.READ_SMS] == true) {
            loadSuggestions()
        } else {
            Toast.makeText(this, "Cần quyền danh bạ/tin nhắn để gợi ý", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
        if (uri != null) {
            resolveContactUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_sms)

        val toolbar = findViewById<Toolbar>(R.id.toolbar_compose)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        edtRecipient = findViewById(R.id.edt_recipient)
        recyclerView = findViewById(R.id.recycler_contacts)
        fabStartChat = findViewById(R.id.fab_start_chat)

        edtRecipient.addTextChangedListener(PhoneNumberFormattingTextWatcher())
        
        edtRecipient.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    fabStartChat.visibility = View.VISIBLE
                    searchJob?.cancel()
                    searchJob = lifecycleScope.launch {
                        delay(300)
                        filterSuggestions(query)
                    }
                } else {
                    fabStartChat.visibility = View.GONE
                    loadSuggestions()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<ImageButton>(R.id.btn_pick_contact).setOnClickListener {
             pickContactLauncher.launch(null)
        }
        
        fabStartChat.setOnClickListener {
            val input = edtRecipient.text.toString().trim()
            if (input.isNotEmpty()) {
                openChat(input)
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        handleIntent(intent)
        
        checkPermissionsAndLoad()
    }
    
    private fun checkPermissionsAndLoad() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
             permissions.add(Manifest.permission.READ_SMS)
        }
        
        if (permissions.isEmpty()) {
            loadSuggestions()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
    
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data
        if (data != null && (data.scheme == "sms" || data.scheme == "smsto")) {
            val address = data.schemeSpecificPart
            if (!address.isNullOrEmpty()) {
                edtRecipient.setText(address)
            }
        }
    }

    private fun resolveContactUri(uri: Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                
                if (idIndex != -1 && hasPhoneIndex != -1) {
                    val id = it.getString(idIndex)
                    val hasPhone = it.getInt(hasPhoneIndex)
                    
                    if (hasPhone > 0) {
                        val phones = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )
                        phones?.use { pCursor ->
                            if (pCursor.moveToFirst()) {
                                val numberIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numberIndex != -1) {
                                    val number = pCursor.getString(numberIndex)
                                    edtRecipient.setText(number)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openChat(address: String) {
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        mainIntent.putExtra("sender_address", address)
        
        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            mainIntent.putExtra("draft_body", intent.getStringExtra(Intent.EXTRA_TEXT))
        }
        
        startActivity(mainIntent)
        finish()
    }

    // Load recent conversations AND contacts
    private fun loadSuggestions() {
        lifecycleScope.launch(Dispatchers.IO) {
            val suggestions = mutableListOf<ContactItem>()
            val seenNumbers = HashSet<String>()
            
            // 1. Load Recent Conversations (Custom App Data)
            try {
                if (ContextCompat.checkSelfPermission(this@ComposeSmsActivity, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    val repository = SmsRepository(this@ComposeSmsActivity)
                    val conversations = repository.getConversations().take(10) // Top 10 recent
                    
                    conversations.forEach { conv ->
                        val cleanNum = normalizeNumber(conv.address)
                        if (!seenNumbers.contains(cleanNum)) {
                            seenNumbers.add(cleanNum)
                            suggestions.add(ContactItem(
                                name = conv.contactName ?: conv.address,
                                phoneNumber = conv.address,
                                photoUri = conv.avatarPath,
                                isRecent = true
                            ))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            // 2. Load Top Contacts
            try {
                if (ContextCompat.checkSelfPermission(this@ComposeSmsActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val projection = arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    )
                    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED} DESC LIMIT 20"

                    val cursor = contentResolver.query(uri, projection, null, null, sortOrder)
                    cursor?.use {
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                        while (it.moveToNext()) {
                            val name = it.getString(nameIdx)
                            val number = it.getString(numIdx)
                            val cleanNum = normalizeNumber(number)
                            
                            if (!seenNumbers.contains(cleanNum)) {
                                seenNumbers.add(cleanNum)
                                val photo = it.getString(photoIdx)
                                suggestions.add(ContactItem(name, number, photo, isRecent = false))
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            withContext(Dispatchers.Main) {
                suggestionList.clear()
                suggestionList.addAll(suggestions)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun filterSuggestions(query: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val suggestions = mutableListOf<ContactItem>()
            val seenNumbers = HashSet<String>()
            val lowerQuery = query.lowercase()
            
            // 1. Search in Existing Conversations
            try {
                if (ContextCompat.checkSelfPermission(this@ComposeSmsActivity, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    val repository = SmsRepository(this@ComposeSmsActivity)
                    // We load all conversations to filter, which might be heavy but fine for a prototype
                    val conversations = repository.getConversations() 
                    
                    conversations.forEach { conv ->
                        if (conv.address.contains(query) || conv.contactName?.lowercase()?.contains(lowerQuery) == true) {
                            val cleanNum = normalizeNumber(conv.address)
                            if (!seenNumbers.contains(cleanNum)) {
                                seenNumbers.add(cleanNum)
                                suggestions.add(ContactItem(
                                    name = conv.contactName ?: conv.address,
                                    phoneNumber = conv.address,
                                    photoUri = conv.avatarPath,
                                    isRecent = true
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            // 2. Search in Contacts
            try {
                if (ContextCompat.checkSelfPermission(this@ComposeSmsActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val projection = arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    )
                    val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                    val selectionArgs = arrayOf("%$query%", "%$query%")
                    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT 20"

                    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                    cursor?.use {
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                        while (it.moveToNext()) {
                            val name = it.getString(nameIdx)
                            val number = it.getString(numIdx)
                            val cleanNum = normalizeNumber(number)
                            
                            if (!seenNumbers.contains(cleanNum)) {
                                seenNumbers.add(cleanNum)
                                val photo = it.getString(photoIdx)
                                suggestions.add(ContactItem(name, number, photo, isRecent = false))
                            }
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            withContext(Dispatchers.Main) {
                suggestionList.clear()
                suggestionList.addAll(suggestions)
                adapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun normalizeNumber(number: String): String {
        return number.replace("[^0-9]".toRegex(), "")
    }
}

data class ContactItem(
    val name: String, 
    val phoneNumber: String, 
    val photoUri: String?,
    val isRecent: Boolean = false // To differentiate UI if needed
)

class ContactAdapter(
    private val contacts: List<ContactItem>,
    private val onClick: (ContactItem) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(android.R.id.text1)
        val txtPhone: TextView = view.findViewById(android.R.id.text2)
        val imgPhoto: android.widget.ImageView = view.findViewById(android.R.id.icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.txtName.text = contact.name
        
        // Show "Recent" tag if applicable
        if (contact.isRecent && !contact.name.equals(contact.phoneNumber)) {
            holder.txtPhone.text = "${contact.phoneNumber} (Gần đây)"
        } else {
            holder.txtPhone.text = contact.phoneNumber
        }
        
        if (contact.photoUri != null) {
            holder.imgPhoto.setImageURI(Uri.parse(contact.photoUri))
        } else {
            holder.imgPhoto.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        
        holder.itemView.setOnClickListener { onClick(contact) }
    }

    override fun getItemCount() = contacts.size
}