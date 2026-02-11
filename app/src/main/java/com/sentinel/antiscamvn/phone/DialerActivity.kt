package com.sentinel.antiscamvn.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.databinding.ActivityDialerBinding

class DialerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDialerBinding
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        checkDefaultDialer()
        setupViewPager()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        val missing = permissions.filter { 
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED 
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupViewPager() {
        val adapter = DialerPagerAdapter(this)
        binding.viewPagerDialer.adapter = adapter
        
        TabLayoutMediator(binding.tabLayoutDialer, binding.viewPagerDialer) { tab, position ->
            tab.text = when (position) {
                0 -> "Bàn phím"
                1 -> "Gần đây"
                else -> ""
            }
        }.attach()
    }

    private fun checkDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivityForResult(intent, 1)
            }
        } else {
             val telecomManager = getSystemService(TelecomManager::class.java)
             if (packageName != telecomManager.defaultDialerPackage) {
                 val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                 intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                 startActivity(intent)
             }
        }
    }

    fun makeCall(number: String) {
        val uri = Uri.parse("tel:$number")
        val intent = Intent(Intent.ACTION_CALL, uri)
        try {
            startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    inner class DialerPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DialpadFragment().apply {
                    setOnCallRequestListener { makeCall(it) }
                }
                1 -> RecentsFragment().apply {
                    setOnCallRequestListener { makeCall(it) }
                }
                else -> throw IllegalArgumentException("Invalid position")
            }
        }
    }
}