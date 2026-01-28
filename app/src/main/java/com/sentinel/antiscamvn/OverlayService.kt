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
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.manager.AudioRecorderManager
import com.sentinel.antiscamvn.manager.ScreenCaptureManager
import com.sentinel.antiscamvn.network.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var headView: View? = null
    private var menuView: View? = null
    private var windowView: View? = null
    private var recordBubbleView: View? = null

    private var paramsHead: WindowManager.LayoutParams? = null
    private var paramsMenu: WindowManager.LayoutParams? = null
    private var paramsWindow: WindowManager.LayoutParams? = null
    private var paramsRecordBubble: WindowManager.LayoutParams? = null

    private val CHANNEL_ID = "OverlayServiceChannel"

    // Chat UI
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // Modules
    private lateinit var audioManager: AudioRecorderManager
    private lateinit var screenCaptureManager: ScreenCaptureManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // Init Managers
        audioManager = AudioRecorderManager(this)
        screenCaptureManager = ScreenCaptureManager(this)
        
        setupManagerCallbacks()
    }
    
    private fun setupManagerCallbacks() {
        // Audio Callback: Cập nhật thời gian trên bong bóng hoặc cửa sổ
        audioManager.onTimerUpdate = { timeString ->
             updateRecordingTimerUI(timeString)
        }

        // Screen Capture Callback
        screenCaptureManager.onCaptureSuccess = { file, bitmap ->
             showWindow(Feature.RESULT)
             addMessage("Đã chụp màn hình.", true, bitmap)
             uploadScreenshot(file)
        }
        
        screenCaptureManager.onError = { error ->
             Toast.makeText(this, "Lỗi: $error", Toast.LENGTH_SHORT).show()
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
            "START_SCREEN_CAPTURE" -> {
                val resultCode = intent.getIntExtra("RESULT_CODE", 0)
                val data = intent.getParcelableExtra<Intent>("DATA_INTENT")
                if (resultCode != 0 && data != null) {
                    screenCaptureManager.startCapture(resultCode, data, windowManager)
                }
            }
            else -> {
                showHead()
            }
        }

        startForeground(1, createNotification())
        return START_NOT_STICKY
    }

    // --- UI LOGIC ---

    private fun getThemedContext(): Context = ContextThemeWrapper(this, R.style.Theme_ShieldCallVN)

    private fun showHead() {
        if (headView != null) return
        removeMenu()
        removeWindow()
        removeRecordBubble()

        headView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_floating_head, null)
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        
        paramsHead = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 200 }

        setupDragListener(headView!!, paramsHead!!) { if (menuView == null) showMenu() else removeMenu() }
        windowManager.addView(headView, paramsHead)
    }
    
    private fun showRecordBubble() {
        if (recordBubbleView != null) return
        removeWindow(); removeMenu(); 
        if (headView != null) { windowManager.removeView(headView); headView = null }

        recordBubbleView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_floating_head, null)
        val icon = recordBubbleView?.findViewById<ImageView>(R.id.img_head)
        icon?.setImageResource(android.R.drawable.ic_btn_speak_now)
        icon?.setColorFilter(android.graphics.Color.RED)

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        paramsRecordBubble = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = paramsHead?.x ?: 0; y = paramsHead?.y ?: 200 }

        setupDragListener(recordBubbleView!!, paramsRecordBubble!!) {
            removeRecordBubble()
            showWindow(Feature.RECORD)
        }
        windowManager.addView(recordBubbleView, paramsRecordBubble)
    }

    private fun showMenu() {
        if (menuView != null) return
        menuView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_floating_menu, null)
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        paramsMenu = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (paramsHead?.x ?: 0) + 160; y = paramsHead?.y ?: 200 }

        menuView?.findViewById<View>(R.id.btn_chat)?.setOnClickListener { removeMenu(); showWindow(Feature.CHAT) }
        menuView?.findViewById<View>(R.id.btn_record)?.setOnClickListener { removeMenu(); showWindow(Feature.RECORD) }
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
        windowView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_window_chat, null)
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        
        paramsWindow = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            if (feature == Feature.WARNING) (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
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
            if (audioManager.isRecording()) showRecordBubble() else showHead()
        }

        when (feature) {
            Feature.CHAT -> {
                txtTitle.text = "Chat AI Assistant"
                layoutLoading.visibility = View.GONE; layoutRecording.visibility = View.GONE
                recyclerChat.visibility = View.VISIBLE; layoutInput.visibility = View.VISIBLE
                setupChat(view)
            }
            Feature.RECORD -> {
                txtTitle.text = "Ghi âm cuộc gọi"
                layoutLoading.visibility = View.GONE; layoutRecording.visibility = View.VISIBLE
                recyclerChat.visibility = View.GONE; layoutInput.visibility = View.GONE
                
                if (!audioManager.isRecording()) {
                    audioManager.startRecording(
                        onStart = { showRecordBubble() },
                        onError = { Toast.makeText(this, "Lỗi ghi âm: $it", Toast.LENGTH_SHORT).show() }
                    )
                }
                btnStopRecord.setOnClickListener { 
                    val file = audioManager.stopRecording()
                    if (file != null) uploadAudio(file)
                }
            }
            Feature.RESULT -> {
                txtTitle.text = "Kết quả phân tích"
                layoutLoading.visibility = View.VISIBLE; layoutRecording.visibility = View.GONE
                recyclerChat.visibility = View.VISIBLE; layoutInput.visibility = View.VISIBLE
                setupChat(view)
            }
            Feature.WARNING -> {
                txtTitle.text = "Kiểm tra cuộc gọi..."
                layoutLoading.visibility = View.VISIBLE; layoutRecording.visibility = View.GONE
                recyclerChat.visibility = View.GONE; layoutInput.visibility = View.GONE
            }
        }
    }
    
    private fun updateRecordingTimerUI(time: String) {
        // Cập nhật timer trên Window to (nếu đang mở)
        if (windowView != null && windowView!!.isAttachedToWindow) {
            val txtTimer = windowView!!.findViewById<TextView>(R.id.txt_record_timer)
            txtTimer?.text = time
        }
        // Có thể cập nhật timer trên bong bóng nếu muốn (cần thêm TextView vào layout bubble)
    }

    private fun setupChat(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_chat)
        if (!::chatAdapter.isInitialized) chatAdapter = ChatAdapter(chatMessages)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = chatAdapter

        val edtInput = view.findViewById<EditText>(R.id.edt_chat_input)
        view.findViewById<View>(R.id.btn_send).setOnClickListener {
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
        if (!::chatAdapter.isInitialized) chatAdapter = ChatAdapter(chatMessages)
        
        val recycler = windowView?.findViewById<RecyclerView>(R.id.recycler_chat)
        if (recycler != null && recycler.isAttachedToWindow) {
            if (recycler.adapter == null) {
                 recycler.layoutManager = LinearLayoutManager(this)
                 recycler.adapter = chatAdapter
            }
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recycler.scrollToPosition(chatMessages.size - 1)
        }
    }

    // --- NETWORK --- (Giữ nguyên logic network cũ nhưng gọn hơn)
    private fun sendChatToAI(message: String) {
        RetrofitClient.instance.chatWithAI(ChatRequest(userMessage = message)).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                addMessage(response.body()?.aiResponse ?: "Lỗi", false)
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) { addMessage("Lỗi mạng", false) }
        })
    }

    private fun uploadAudio(file: File) {
        showWindow(Feature.RESULT)
        windowView?.findViewById<TextView>(R.id.txt_loading_status)?.text = "Đang gửi file ghi âm..."
        val body = MultipartBody.Part.createFormData("audio", file.name, file.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
        val phoneBody = "Unknown".toRequestBody("text/plain".toMediaTypeOrNull())

        RetrofitClient.instance.analyzeAudio(body, phoneBody).enqueue(object : Callback<AudioAnalysisResponse> {
            override fun onResponse(call: Call<AudioAnalysisResponse>, response: Response<AudioAnalysisResponse>) {
                hideLoading()
                val result = response.body()
                val prefix = if (result?.isScam == true) "⚠ CẢNH BÁO: " else "✅ An toàn: "
                addMessage("$prefix${result?.warningMessage}\n${result?.transcript}", false)
            }
            override fun onFailure(call: Call<AudioAnalysisResponse>, t: Throwable) { hideLoading(); addMessage("Lỗi gửi file", false) }
        })
    }

    private fun uploadScreenshot(file: File) {
        // Loading đã được show ở callback onCaptureSuccess
        val body = MultipartBody.Part.createFormData("image", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
        RetrofitClient.instance.analyzeImage(body).enqueue(object : Callback<ImageAnalysisResponse> {
            override fun onResponse(call: Call<ImageAnalysisResponse>, response: Response<ImageAnalysisResponse>) {
                hideLoading()
                val r = response.body()
                addMessage("OCR:\n${r?.ocrText}\n\nĐánh giá: ${r?.riskAnalysis?.riskLevel}", false)
            }
            override fun onFailure(call: Call<ImageAnalysisResponse>, t: Throwable) { hideLoading(); addMessage("Lỗi gửi ảnh", false) }
        })
    }
    
    private fun checkPhoneNumber(phone: String) {
         RetrofitClient.instance.checkPhoneNumber(phone).enqueue(object : Callback<RiskResponse> {
             override fun onResponse(call: Call<RiskResponse>, response: Response<RiskResponse>) {
                 hideLoading()
                 windowView?.findViewById<TextView>(R.id.txt_title)?.text = "SĐT: $phone"
                 addMessage("Mức độ: ${response.body()?.riskLevel}", false)
             }
             override fun onFailure(call: Call<RiskResponse>, t: Throwable) { hideLoading(); addMessage("Lỗi mạng", false) }
         })
    }

    private fun hideLoading() {
        windowView?.findViewById<View>(R.id.layout_loading)?.visibility = View.GONE
        windowView?.findViewById<View>(R.id.recycler_chat)?.visibility = View.VISIBLE
    }

    // --- UTILS ---
    private fun setupDragListener(view: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0; private var startY = 0; private var touchX = 0f; private var touchY = 0f; private var isClick = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; touchX = event.rawX; touchY = event.rawY; isClick = true; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt(); val dy = (event.rawY - touchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) { isClick = false; params.x = startX + dx; params.y = startY + dy; windowManager.updateViewLayout(view, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> { if (isClick) onClick(); return true }
                }
                return false
            }
        })
    }

    private fun removeMenu() { if (menuView != null) { windowManager.removeView(menuView); menuView = null } }
    private fun removeWindow() { if (windowView != null) { windowManager.removeView(windowView); windowView = null } }
    private fun removeRecordBubble() { if (recordBubbleView != null) { windowManager.removeView(recordBubbleView); recordBubbleView = null } }

    private fun createNotification(): Notification {
        val contentPendingIntent = PendingIntent.getActivity(this, 0, Intent(this, SettingsActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) CHANNEL_ID else "")
            .setContentTitle("ShieldCall đang chạy").setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_LOW))
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (headView != null) windowManager.removeView(headView)
        removeMenu(); removeWindow(); removeRecordBubble()
        audioManager.stopRecording()
        screenCaptureManager.stopCapture()
    }
}