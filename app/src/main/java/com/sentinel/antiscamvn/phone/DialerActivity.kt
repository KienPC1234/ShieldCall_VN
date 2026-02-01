package com.sentinel.antiscamvn.phone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class DialerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Màn hình quay số, có thể chuyển hướng về ContactsFragment
        finish()
    }
}