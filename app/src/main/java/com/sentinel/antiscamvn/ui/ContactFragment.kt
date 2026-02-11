package com.sentinel.antiscamvn.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var txtEmpty: TextView? = null
    private var searchView: androidx.appcompat.widget.SearchView? = null
    
    private val fullContactList = mutableListOf<ContactItem>()
    private val displayedContactList = mutableListOf<ContactItem>()
    private var adapter: ContactAdapter? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadContacts()
        } else {
            Toast.makeText(context, "Cần quyền danh bạ để hiển thị danh sách", Toast.LENGTH_SHORT).show()
            updateEmptyState()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_contacts, container, false)
        
        recyclerView = view.findViewById(R.id.recycler_contacts)
        txtEmpty = view.findViewById(R.id.txt_empty)
        searchView = view.findViewById(R.id.search_view)
        val dialpadContainer = view.findViewById<FrameLayout>(R.id.dialpad_container)
        val fabDialpad = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_dialpad)
        
        recyclerView?.layoutManager = LinearLayoutManager(context)
        adapter = ContactAdapter(displayedContactList, 
            onItemClick = { contact ->
                // Navigate to SMS Detail
                try {
                    val bundle = Bundle().apply {
                        putString("address", contact.phoneNumber)
                        putString("name", contact.displayName)
                    }
                    findNavController().navigate(R.id.nav_sms_detail, bundle)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onCallClick = { contact ->
                initiateCall(contact.phoneNumber)
            },
            onLongClick = { contact ->
                showBlockDialog(contact)
            }
        )
        recyclerView?.adapter = adapter

        fabDialpad.setOnClickListener {
            if (dialpadContainer.visibility == View.VISIBLE) {
                dialpadContainer.visibility = View.GONE
                fabDialpad.setImageResource(android.R.drawable.ic_menu_call)
            } else {
                dialpadContainer.visibility = View.VISIBLE
                fabDialpad.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                
                // Load DialpadFragment if not already there
                val dialpadFragment = childFragmentManager.findFragmentByTag("dialpad")
                if (dialpadFragment == null) {
                    val newDialpad = com.sentinel.antiscamvn.phone.DialpadFragment()
                    newDialpad.setOnCallRequestListener { number ->
                        initiateCall(number)
                    }
                    childFragmentManager.beginTransaction()
                        .replace(R.id.dialpad_container, newDialpad, "dialpad")
                        .commit()
                }
            }
        }

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

        // Remove underline
        try {
            val plateId = searchView?.context?.resources?.getIdentifier("android:id/search_plate", null, null) ?: 0
            if (plateId != 0) {
                 searchView?.findViewById<View>(plateId)?.background = null
            }
        } catch (e: Exception) { e.printStackTrace() }

        view.findViewById<View>(R.id.fab_add_contact).setOnClickListener {
            // Open system add contact intent
            val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
            startActivity(intent)
        }

        checkPermissionAndLoad()
        return view
    }
    
    private fun showBlockDialog(contact: ContactItem) {
        val title = if (contact.isBlocked) "Bỏ chặn ${contact.phoneNumber}?" else "Chặn ${contact.phoneNumber}?"
        val msg = if (contact.isBlocked) "Bạn sẽ nhận lại được cuộc gọi và tin nhắn từ số này." else "Cuộc gọi và tin nhắn từ số này sẽ bị chặn."
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(if (contact.isBlocked) "Bỏ chặn" else "Chặn") { _, _ ->
                 lifecycleScope.launch(Dispatchers.IO) {
                     val repo = SmsRepository(requireContext())
                     // Try to match exact blocked record if possible, but cleaner is usually enough
                     if (contact.isBlocked) repo.unblockNumber(contact.phoneNumber)
                     else repo.blockNumber(contact.phoneNumber)
                     
                     withContext(Dispatchers.Main) {
                         Toast.makeText(context, "Đã cập nhật", Toast.LENGTH_SHORT).show()
                         loadContacts()
                     }
                 }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload in case changes were made
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        }
    }

    private fun checkPermissionAndLoad() {
        val context = context ?: return
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> {
                loadContacts()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val context = context ?: return@launch
            val repository = SmsRepository(context)
            
            // 0. Get Blocked Numbers
            val blockedNumbers = try {
                repository.getAllBlockedNumbers().toSet()
            } catch (e: Exception) {
                emptySet<String>()
            }
            
            // 1. Get Local Aliases
            val aliases = try {
                repository.getAllAliases().associateBy { it.phoneNumber }
            } catch (e: Exception) {
                emptyMap()
            }

            // 2. Get System Contacts
            val contactsMap = mutableMapOf<String, ContactItem>() // Key by Phone Number to dedup
            
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone._ID,
                        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    ),
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use {
                    val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                    val keyIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                    while (it.moveToNext()) {
                        val id = it.getString(idIdx)
                        val key = it.getString(keyIdx)
                        val name = it.getString(nameIdx)
                        var number = it.getString(numIdx) ?: continue
                        val photo = it.getString(photoIdx)
                        
                        // Normalize number for better matching (remove spaces, etc)
                        val cleanNumber = number.replace(" ", "").replace("-", "")
                        
                        // Check if we have an alias for this number (try various formats)
                        // Simple exact match first
                        var alias = aliases[cleanNumber]
                        
                        // Try alternative formats if no match
                        if (alias == null) {
                             if (cleanNumber.startsWith("0")) {
                                 alias = aliases["+84" + cleanNumber.substring(1)]
                             } else if (cleanNumber.startsWith("+84")) {
                                 alias = aliases["0" + cleanNumber.substring(3)]
                             }
                        }

                        val finalName = alias?.displayName ?: name
                        val finalAvatar = alias?.avatarPath ?: photo
                        
                        // Check blocked
                        val isBlocked = blockedNumbers.contains(cleanNumber) || blockedNumbers.contains(number)

                        // Avoid duplicates: if name and number are same, skip?
                        // Or just put in map by number.
                        // Contacts might have multiple numbers.
                        contactsMap[cleanNumber] = ContactItem(id, key, finalName, number, finalAvatar, isBlocked)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // 3. Add any "Ghost" Aliases (Saved in app but not in system contacts?)
            aliases.forEach { (number, entity) ->
                // Normalize aliases keys might differ from system keys
                val cleanNumber = number.replace(" ", "").replace("-", "")
                if (!contactsMap.containsKey(cleanNumber)) {
                    // Check if we already have it under alternative format
                     var found = false
                     if (cleanNumber.startsWith("0")) {
                         if (contactsMap.containsKey("+84" + cleanNumber.substring(1))) found = true
                     } else if (cleanNumber.startsWith("+84")) {
                         if (contactsMap.containsKey("0" + cleanNumber.substring(3))) found = true
                     }
                     
                     if (!found) {
                         val isBlocked = blockedNumbers.contains(cleanNumber)
                         contactsMap[cleanNumber] = ContactItem(
                             id = "LOC_${entity.phoneNumber}",
                             lookupKey = null,
                             displayName = entity.displayName ?: entity.phoneNumber,
                             phoneNumber = entity.phoneNumber,
                             avatarPath = entity.avatarPath,
                             isBlocked = isBlocked
                         )
                     }
                }
            }
            
            // 4. Add Blocked Numbers that are NOT in contacts
            // User requested "Support unblock on contacts".
            // It's helpful to show blocked numbers even if not saved, so user can unblock them.
            blockedNumbers.forEach { blockedNum ->
                 val cleanNum = blockedNum.replace(" ", "").replace("-", "")
                 if (!contactsMap.containsKey(cleanNum)) {
                     // Check alternative formats
                     var found = false
                     if (cleanNum.startsWith("0")) {
                         if (contactsMap.containsKey("+84" + cleanNum.substring(1))) found = true
                     } else if (cleanNum.startsWith("+84")) {
                         if (contactsMap.containsKey("0" + cleanNum.substring(3))) found = true
                     }
                     
                     if (!found) {
                         contactsMap[cleanNum] = ContactItem(
                             id = "BLK_$blockedNum",
                             lookupKey = null,
                             displayName = "Đã chặn", // Or just the number
                             phoneNumber = blockedNum,
                             avatarPath = null,
                             isBlocked = true
                         )
                     }
                 }
            }

            val sortedList = contactsMap.values.sortedBy { it.displayName }

            withContext(Dispatchers.Main) {
                fullContactList.clear()
                fullContactList.addAll(sortedList)
                filterList(searchView?.query?.toString())
            }
        }
    }
    
    private fun filterList(query: String?) {
        val searchText = query?.lowercase() ?: ""
        displayedContactList.clear()
        
        if (searchText.isEmpty()) {
            displayedContactList.addAll(fullContactList)
        } else {
            displayedContactList.addAll(fullContactList.filter { 
                it.displayName.lowercase().contains(searchText) || 
                it.phoneNumber.contains(searchText)
            })
        }
        adapter?.notifyDataSetChanged()
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        if (displayedContactList.isEmpty()) {
            recyclerView?.visibility = View.GONE
            txtEmpty?.visibility = View.VISIBLE
        } else {
            recyclerView?.visibility = View.VISIBLE
            txtEmpty?.visibility = View.GONE
        }
    }

    private fun initiateCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phoneNumber")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể thực hiện cuộc gọi", Toast.LENGTH_SHORT).show()
        }
    }
}