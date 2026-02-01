package com.sentinel.antiscamvn.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.utils.ProfileManager

class ProfileFragment : Fragment() {

    private var imgAvatar: ImageView? = null
    private var txtInitial: TextView? = null
    private var edtNickname: TextInputEditText? = null
    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            imgAvatar?.setImageURI(uri)
            imgAvatar?.visibility = View.VISIBLE
            txtInitial?.visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_profile)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        imgAvatar = view.findViewById(R.id.img_profile_avatar)
        txtInitial = view.findViewById(R.id.txt_profile_initial)
        edtNickname = view.findViewById(R.id.edt_nickname)
        
        val btnEditAvatar = view.findViewById<ImageButton>(R.id.btn_edit_avatar)
        val btnSave = view.findViewById<Button>(R.id.btn_save_profile)

        // Load current data
        val currentNickname = ProfileManager.getNickname(requireContext())
        val currentAvatar = ProfileManager.getAvatarUri(requireContext())

        edtNickname?.setText(currentNickname)
        
        if (currentAvatar != null) {
            try {
                imgAvatar?.setImageURI(Uri.parse(currentAvatar))
                imgAvatar?.visibility = View.VISIBLE
                txtInitial?.visibility = View.GONE
            } catch (e: Exception) {
                // Fallback if URI is invalid
                setInitial(currentNickname)
            }
        } else {
            setInitial(currentNickname)
        }

        btnEditAvatar.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSave.setOnClickListener {
            val newNickname = edtNickname?.text.toString().trim()
            if (newNickname.isNotEmpty()) {
                ProfileManager.setNickname(requireContext(), newNickname)
                
                if (selectedImageUri != null) {
                    // Persist permission if needed, but for simple URI storage we just save the string
                    // Ideally we should copy the file to internal storage
                    ProfileManager.setAvatarUri(requireContext(), selectedImageUri.toString())
                }
                
                Toast.makeText(context, "Đã lưu hồ sơ", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } else {
                Toast.makeText(context, "Tên không được để trống", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun setInitial(name: String) {
        imgAvatar?.visibility = View.GONE
        txtInitial?.visibility = View.VISIBLE
        txtInitial?.text = name.firstOrNull()?.toString()?.uppercase() ?: "?"
    }
}
