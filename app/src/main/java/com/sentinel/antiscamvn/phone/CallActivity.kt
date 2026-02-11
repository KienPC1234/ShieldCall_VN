package com.sentinel.antiscamvn.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.manager.AudioCaptureService
import com.sentinel.antiscamvn.utils.LogManager
import java.util.concurrent.TimeUnit

class CallActivity : AppCompatActivity(), ShieldInCallService.CallStateListener {

    private lateinit var txtName: TextView
    private lateinit var txtStatus: TextView
    private lateinit var btnMute: ImageButton
    private lateinit var btnSpeaker: ImageButton
    private lateinit var btnEnd: ImageButton
    private lateinit var btnAnswer: ImageButton
    private lateinit var btnReject: ImageButton
    private lateinit var btnAnalyze: com.google.android.material.button.MaterialButton
    private lateinit var layoutControls: LinearLayout
    private lateinit var layoutIncoming: LinearLayout
    private lateinit var cardWarning: CardView
    private lateinit var txtWarning: TextView
    private lateinit var rootLayout: View

    private val REQUEST_MEDIA_PROJECTION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        rootLayout = findViewById(R.id.call_root_layout)
        txtName = findViewById(R.id.txt_caller_name)
        txtStatus = findViewById(R.id.txt_call_status)
        btnMute = findViewById(R.id.btn_mute)
        btnSpeaker = findViewById(R.id.btn_speaker)
        btnEnd = findViewById(R.id.btn_end_call)
        btnAnswer = findViewById(R.id.btn_answer)
        btnReject = findViewById(R.id.btn_reject)
        btnAnalyze = findViewById(R.id.btn_analyze)
        layoutControls = findViewById(R.id.layout_controls)
        layoutIncoming = findViewById(R.id.layout_incoming_controls)
        cardWarning = findViewById(R.id.card_warning)
        txtWarning = findViewById(R.id.txt_warning_detail)

        val isIncoming = intent.getBooleanExtra("is_incoming", false)
        updateUIState(if (isIncoming) Call.STATE_RINGING else Call.STATE_DIALING)

        ShieldInCallService.addListener(this)
        
        // Initial state check
        ShieldInCallService.currentCall?.let {
             updateCallInfo(it)
             onStateChanged(it)
        } ?: finish()

        setupButtons()
        
        // Listen for warnings
        AudioCaptureService.onWarningReceived = { warningJson ->
            runOnUiThread {
                try {
                    val obj = org.json.JSONObject(warningJson)
                    val risk = obj.optString("risk", "INFO")
                    val message = obj.optString("message", "")
                    
                    cardWarning.visibility = View.VISIBLE
                    txtWarning.text = message
                    
                    if (risk == "DANGER" || risk == "WARNING") {
                        rootLayout.setBackgroundColor(android.graphics.Color.parseColor("#420000")) // Dark red background
                    }
                } catch (e: Exception) {
                    // Fallback for non-json
                    cardWarning.visibility = View.VISIBLE
                    txtWarning.text = warningJson
                }
            }
        }
    }

    private fun setupButtons() {
        btnEnd.setOnClickListener {
            ShieldInCallService.currentCall?.disconnect()
            finish()
        }

        btnAnswer.setOnClickListener {
            ShieldInCallService.currentCall?.answer(0)
        }

        btnReject.setOnClickListener {
            ShieldInCallService.currentCall?.reject(false, null)
            finish()
        }

        btnMute.setOnClickListener {
            val call = ShieldInCallService.currentCall ?: return@setOnClickListener
            // Toggle mute logic (requires InCallService audio state management - simplified here)
            // In a real app, you'd check call.details.callAudioState
        }
        
        btnAnalyze.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
            } else {
                 android.widget.Toast.makeText(this, "Android 10+ required for analysis", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            btnAnalyze.text = "Đang phân tích..."
            btnAnalyze.isEnabled = false
        }
    }

    override fun onStateChanged(call: Call) {
        updateUIState(call.state)
        updateCallInfo(call)
    }

    private fun updateCallInfo(call: Call) {
        val handle = call.details.handle?.schemeSpecificPart
        txtName.text = handle ?: "Unknown"
    }

    private fun updateUIState(state: Int) {
        when (state) {
            Call.STATE_RINGING -> {
                txtStatus.text = "Incoming Call..."
                layoutIncoming.visibility = View.VISIBLE
                layoutControls.visibility = View.GONE
                btnAnalyze.visibility = View.GONE
            }
            Call.STATE_ACTIVE -> {
                txtStatus.text = "00:00" // Timer logic needed
                layoutIncoming.visibility = View.GONE
                layoutControls.visibility = View.VISIBLE
                btnAnalyze.visibility = View.VISIBLE
            }
            Call.STATE_DIALING -> {
                txtStatus.text = "Dialing..."
                layoutIncoming.visibility = View.GONE
                layoutControls.visibility = View.VISIBLE // Allow hangup
                btnAnalyze.visibility = View.GONE
            }
            Call.STATE_DISCONNECTED -> {
                txtStatus.text = "Ended"
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShieldInCallService.removeListener(this)
        val stopIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
        startService(stopIntent)
    }
}
