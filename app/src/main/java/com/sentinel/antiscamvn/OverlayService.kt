package com.sentinel.antiscamvn

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.network.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var headView: View? = null
    private var menuView: View? = null
    private var windowView: View? = null

    private var paramsHead: WindowManager.LayoutParams? = null
    private var paramsMenu: WindowManager.LayoutParams? = null
    private var paramsWindow: WindowManager.LayoutParams? = null

    private val CHANNEL_ID = "OverlayServiceChannel"

    // Chat State
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // Recording State
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null
    private var recordTimer: Timer? = null
    private var recordSeconds = 0

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenCaptureActivity.ACTION_SCREENSHOT_CAPTURED) {
                val path = intent.getStringExtra(ScreenCaptureActivity.EXTRA_SCREENSHOT_PATH)
                if (path != null) {
                    handleScreenshotCaptured(path)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Init Retrofit with Context for Mock Interceptor
        RetrofitClient.init(this)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Register Screenshot Receiver
        val filter = IntentFilter(ScreenCaptureActivity.ACTION_SCREENSHOT_CAPTURED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }
    }

    private enum class Feature { CHAT, RECORD, RESULT, WARNING }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_SERVICE" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "SHOW_HEAD" -> {
                showHead()
            }
            "SHOW_POPUP" -> {
                val phone = intent.getStringExtra("PHONE") ?: "Unknown"
                showWindow(Feature.WARNING)
                checkPhoneNumber(phone)
            }
            else -> {
                // Default start
                showHead()
            }
        }

        startForeground(1, createNotification())
        return START_NOT_STICKY
    }

    // --- UI SETUP ---

    private fun getThemedContext(): Context {
        return ContextThemeWrapper(this, R.style.Theme_ShieldCallVN)
    }

    private fun showHead() {
        if (headView != null) return
        removeMenu()
        removeWindow()

        // Use Themed Context to avoid Crash when inflating XML with attributes
        headView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_floating_head, null)
        
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        
        paramsHead = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Drag Logic for Head
        headView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = paramsHead!!.x
                        initialY = paramsHead!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true // Assume click initially
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        // If moved significantly, it's a drag, not a click
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                            paramsHead!!.x = initialX + dx
                            paramsHead!!.y = initialY + dy
                            windowManager.updateViewLayout(headView, paramsHead)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            // Toggle Menu
                            if (menuView == null) showMenu() else removeMenu()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(headView, paramsHead)
    }

    private fun showMenu() {
        if (menuView != null) return
        
        val headX = paramsHead?.x ?: 0
        val headY = paramsHead?.y ?: 200

        // Use Themed Context here too
        menuView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_floating_menu, null)
        
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        
        paramsMenu = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = headX + 160 // Offset to the right of the head
            y = headY
        }

        // Setup Button Listeners
        menuView?.findViewById<View>(R.id.btn_chat)?.setOnClickListener {
            removeMenu()
            showWindow(Feature.CHAT)
        }
        menuView?.findViewById<View>(R.id.btn_record)?.setOnClickListener {
            removeMenu()
            showWindow(Feature.RECORD)
        }
        menuView?.findViewById<View>(R.id.btn_screenshot)?.setOnClickListener {
            removeMenu()
            val intent = Intent(this, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        windowManager.addView(menuView, paramsMenu)
    }

    private fun showWindow(feature: Feature) {
        if (windowView != null) removeWindow()

        // Use Themed Context
        windowView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_window_chat, null)
        
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        paramsWindow = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            if (feature == Feature.WARNING) 
                (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                 WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or 
                 WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            else WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (feature == Feature.WARNING) Gravity.TOP else Gravity.CENTER
            dimAmount = 0.3f
            if (feature == Feature.WARNING) y = 100
        }

        setupWindowUI(windowView!!, feature)
        windowManager.addView(windowView, paramsWindow)
    }

    private fun setupWindowUI(view: View, feature: Feature) {
        val btnClose = view.findViewById<View>(R.id.btn_close_window)
        val txtTitle = view.findViewById<TextView>(R.id.txt_title)
        val layoutLoading = view.findViewById<View>(R.id.layout_loading)
        val layoutRecording = view.findViewById<View>(R.id.layout_recording)
        val recyclerChat = view.findViewById<RecyclerView>(R.id.recycler_chat)
        val layoutInput = view.findViewById<View>(R.id.layout_input)
        val btnStopRecord = view.findViewById<Button>(R.id.btn_stop_record)
        
        btnClose.setOnClickListener {
            removeWindow()
            if (isRecording) stopRecording(false)
            // If closed from Warning, maybe show Head bubble instead of closing completely?
            if (feature == Feature.WARNING) showHead() 
        }

        when (feature) {
            Feature.CHAT -> {
                txtTitle.text = "Chat AI Assistant"
                layoutLoading.visibility = View.GONE
                layoutRecording.visibility = View.GONE
                recyclerChat.visibility = View.VISIBLE
                layoutInput.visibility = View.VISIBLE
                setupChat(view)
            }
            Feature.RECORD -> {
                txtTitle.text = "Ghi âm cuộc gọi"
                layoutLoading.visibility = View.GONE
                layoutRecording.visibility = View.VISIBLE
                recyclerChat.visibility = View.GONE
                layoutInput.visibility = View.GONE
                
                startRecording(view)
                
                btnStopRecord.setOnClickListener {
                    stopRecording(true)
                }
            }
            Feature.RESULT -> {
                txtTitle.text = "Kết quả phân tích"
                layoutLoading.visibility = View.VISIBLE
                layoutRecording.visibility = View.GONE
                recyclerChat.visibility = View.VISIBLE
                layoutInput.visibility = View.GONE
            }
            Feature.WARNING -> {
                txtTitle.text = "Kiểm tra cuộc gọi..."
                layoutLoading.visibility = View.VISIBLE // Re-use loading layout for initial check
                view.findViewById<TextView>(R.id.txt_loading_status).text = "Đang kiểm tra số điện thoại..."
                layoutRecording.visibility = View.GONE
                recyclerChat.visibility = View.GONE
                layoutInput.visibility = View.GONE
                
                // We will reuse Chat UI to display the warning result elegantly
            }
        }
    }

    private fun checkPhoneNumber(phoneNumber: String) {
        RetrofitClient.instance.checkPhoneNumber(phoneNumber).enqueue(object : Callback<RiskResponse> {
            override fun onResponse(call: Call<RiskResponse>, response: Response<RiskResponse>) {
                if (windowView == null) return // Window closed
                
                // Switch UI to show result
                val layoutLoading = windowView?.findViewById<View>(R.id.layout_loading)
                val recyclerChat = windowView?.findViewById<RecyclerView>(R.id.recycler_chat)
                val txtTitle = windowView?.findViewById<TextView>(R.id.txt_title)
                
                layoutLoading?.visibility = View.GONE
                recyclerChat?.visibility = View.VISIBLE
                
                txtTitle?.text = "Thông tin cuộc gọi: $phoneNumber"

                if (response.isSuccessful) {
                    val body = response.body()
                    val riskLevel = body?.riskLevel ?: "UNKNOWN"
                    val label = body?.riskLabel ?: "Không rõ"
                    
                    // Setup Chat Adapter just to show the message
                    chatAdapter = ChatAdapter(chatMessages)
                    recyclerChat?.layoutManager = LinearLayoutManager(this@OverlayService)
                    recyclerChat?.adapter = chatAdapter
                    
                    val msg = "Mức độ: $riskLevel\nCảnh báo: $label"
                    addMessage(msg, false)
                    
                    // Add recommendations
                    body?.recommendations?.forEach { 
                        addMessage("Khuyên dùng: $it", false)
                    }

                } else {
                     addMessage("Không thể kiểm tra số này.", false)
                }
            }

            override fun onFailure(call: Call<RiskResponse>, t: Throwable) {
                if (windowView == null) return
                windowView?.findViewById<View>(R.id.layout_loading)?.visibility = View.GONE
                windowView?.findViewById<RecyclerView>(R.id.recycler_chat)?.visibility = View.VISIBLE
                
                // Initialize adapter if failure happened before setup
                chatAdapter = ChatAdapter(chatMessages)
                val recyclerChat = windowView?.findViewById<RecyclerView>(R.id.recycler_chat)
                recyclerChat?.layoutManager = LinearLayoutManager(this@OverlayService)
                recyclerChat?.adapter = chatAdapter
                
                addMessage("Lỗi mạng: ${t.message}", false)
            }
        })
    }

    // --- CHAT LOGIC ---

    private fun setupChat(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_chat)
        chatAdapter = ChatAdapter(chatMessages)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = chatAdapter

        val edtInput = view.findViewById<EditText>(R.id.edt_chat_input)
        val btnSend = view.findViewById<View>(R.id.btn_send)

        btnSend.setOnClickListener {
            val content = edtInput.text.toString().trim()
            if (content.isNotEmpty()) {
                addMessage(content, true)
                edtInput.setText("")
                sendChatToAI(content)
            }
        }
    }

    private fun addMessage(content: String, isUser: Boolean, image: Bitmap? = null) {
        chatMessages.add(ChatMessage(content, isUser, image))
        val recycler = windowView?.findViewById<RecyclerView>(R.id.recycler_chat)
        if (recycler != null) {
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recycler.scrollToPosition(chatMessages.size - 1)
        }
    }

    private fun sendChatToAI(message: String) {
        val request = ChatRequest(userMessage = message)
        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful) {
                    val reply = response.body()?.aiResponse ?: "Không nhận được phản hồi."
                    addMessage(reply, false)
                } else {
                    addMessage("Lỗi kết nối AI.", false)
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                addMessage("Lỗi mạng: ${t.message}", false)
            }
        })
    }

    // --- RECORDING LOGIC ---

    @SuppressLint("MissingPermission") // Checked in PermissionActivity
    private fun startRecording(view: View) {
        val txtTimer = view.findViewById<TextView>(R.id.txt_record_timer)
        
        audioFile = File(externalCacheDir, "record_${System.currentTimeMillis()}.mp3")
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        try {
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordSeconds = 0
            
            // Timer UI
            recordTimer = Timer()
            recordTimer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    recordSeconds++
                    Handler(Looper.getMainLooper()).post {
                        val min = recordSeconds / 60
                        val sec = recordSeconds % 60
                        txtTimer.text = String.format("%02d:%02d", min, sec)
                    }
                }
            }, 1000, 1000)

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể ghi âm", Toast.LENGTH_SHORT).show()
            removeWindow()
        }
    }

    private fun stopRecording(analyze: Boolean) {
        try {
            if (isRecording) {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                recordTimer?.cancel()
                isRecording = false
                
                if (analyze && audioFile != null) {
                    uploadAudio(audioFile!!)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uploadAudio(file: File) {
        showWindow(Feature.RESULT) // Switch to result view
        val loadingText = windowView?.findViewById<TextView>(R.id.txt_loading_status)
        loadingText?.text = "Đang gửi file ghi âm..."

        val reqFile = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("audio", file.name, reqFile)
        val phoneBody = "Unknown".toRequestBody("text/plain".toMediaTypeOrNull())

        RetrofitClient.instance.analyzeAudio(body, phoneBody).enqueue(object : Callback<AudioAnalysisResponse> {
            override fun onResponse(call: Call<AudioAnalysisResponse>, response: Response<AudioAnalysisResponse>) {
                hideLoadingShowResult()
                if (response.isSuccessful) {
                    val result = response.body()
                    val warning = if (result?.isScam == true) "⚠ CẢNH BÁO LỪA ĐẢO\n" else "✅ An toàn\n"
                    addMessage("$warning${result?.warningMessage}\n\nNội dung: ${result?.transcript}", false)
                } else {
                    addMessage("Lỗi phân tích âm thanh.", false)
                }
            }
            override fun onFailure(call: Call<AudioAnalysisResponse>, t: Throwable) {
                hideLoadingShowResult()
                addMessage("Lỗi gửi file: ${t.message}", false)
            }
        })
    }

    // --- SCREENSHOT LOGIC ---

    private fun handleScreenshotCaptured(path: String) {
        showWindow(Feature.RESULT) // Open window to show processing
        val loadingText = windowView?.findViewById<TextView>(R.id.txt_loading_status)
        loadingText?.text = "Đang phân tích ảnh màn hình..."

        val file = File(path)
        val bitmap = BitmapFactory.decodeFile(path)
        
        // Setup Chat UI in result mode
        setupChat(windowView!!)
        addMessage("Đã chụp màn hình. Đang phân tích...", true, bitmap)

        val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", file.name, reqFile)

        RetrofitClient.instance.analyzeImage(body).enqueue(object : Callback<ImageAnalysisResponse> {
            override fun onResponse(call: Call<ImageAnalysisResponse>, response: Response<ImageAnalysisResponse>) {
                hideLoadingShowResult()
                if (response.isSuccessful) {
                    val result = response.body()
                    val risk = result?.riskAnalysis
                    val msg = "Kết quả OCR:\n${result?.ocrText}\n\nĐánh giá: ${risk?.riskLevel} - ${risk?.details}"
                    addMessage(msg, false)
                } else {
                    addMessage("Lỗi phân tích ảnh.", false)
                }
            }
            override fun onFailure(call: Call<ImageAnalysisResponse>, t: Throwable) {
                hideLoadingShowResult()
                addMessage("Lỗi gửi ảnh: ${t.message}", false)
            }
        })
    }

    private fun hideLoadingShowResult() {
        windowView?.findViewById<View>(R.id.layout_loading)?.visibility = View.GONE
        windowView?.findViewById<View>(R.id.recycler_chat)?.visibility = View.VISIBLE
        windowView?.findViewById<View>(R.id.layout_input)?.visibility = View.VISIBLE
    }

    // --- CLEANUP ---

    private fun removeMenu() {
        if (menuView != null) {
            windowManager.removeView(menuView)
            menuView = null
        }
    }

    private fun removeWindow() {
        if (windowView != null) {
            windowManager.removeView(windowView)
            windowView = null
        }
    }

    private fun createNotification(): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CHANNEL_ID
        } else {
            ""
        }
        
        // Content Intent -> Open Settings
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, settingsIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Show Head
        val showIntent = Intent(this, OverlayService::class.java).apply { action = "SHOW_HEAD" }
        val showPendingIntent = PendingIntent.getService(
            this, 1, showIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop Service
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = "STOP_SERVICE" }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, 
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return Notification.Builder(this, channelId)
            .setContentTitle("ShieldCall đang chạy")
            .setContentText("Chạm để mở Cài đặt. Bong bóng đang hiển thị.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .addAction(Notification.Action.Builder(null, "Hiện công cụ", showPendingIntent).build())
            .addAction(Notification.Action.Builder(null, "Tắt ứng dụng", stopPendingIntent).build())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_LOW 
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenshotReceiver)
        if (headView != null) windowManager.removeView(headView)
        removeMenu()
        removeWindow()
        stopRecording(false)
    }
}