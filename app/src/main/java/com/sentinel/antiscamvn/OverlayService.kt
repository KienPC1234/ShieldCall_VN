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
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import androidx.preference.PreferenceManager
import androidx.core.content.IntentCompat

enum class Feature { CHAT, RESULT, WARNING }

class OverlayService : Service() {

    companion object {
        const val ACTION_SHOW_HEAD = "com.sentinel.antiscamvn.SHOW_HEAD"
        const val ACTION_SHOW_POPUP = "com.sentinel.antiscamvn.SHOW_POPUP"
        const val ACTION_START_SCREEN_CAPTURE = "com.sentinel.antiscamvn.START_SCREEN_CAPTURE"
        const val ACTION_STOP_SERVICE = "com.sentinel.antiscamvn.STOP_SERVICE"
        const val ACTION_SHOW_ICON = "com.sentinel.antiscamvn.SHOW_ICON"
    }

    private lateinit var windowManager: WindowManager
    private var headView: View? = null
    private var menuView: View? = null
    private var windowView: View? = null
    private var captureBubbleView: View? = null

    // Recording UI
    private var recordingTimerView: View? = null
    private var recordingStopView: View? = null

    private var paramsHead: WindowManager.LayoutParams? = null
    private var paramsMenu: WindowManager.LayoutParams? = null
    private var paramsWindow: WindowManager.LayoutParams? = null
    private var paramsCaptureBubble: WindowManager.LayoutParams? = null
    private var paramsRecordingTimer: WindowManager.LayoutParams? = null
    private var paramsRecordingStop: WindowManager.LayoutParams? = null

    private val CHANNEL_ID = "OverlayServiceChannel"
    private val HIDE_NOTIFICATION_ID = 100

    // Chat UI
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private var sessionId: String = UUID.randomUUID().toString()

    // Modules
    private lateinit var audioManager: AudioRecorderManager
    private lateinit var screenCaptureManager: ScreenCaptureManager
    
    // State
    private val capturedImages = mutableListOf<File>()
    private val pendingImages = mutableListOf<File>()
    private val pendingBitmaps = mutableListOf<Bitmap>()
    private var isIconHidden = false

    // Broadcast Receiver
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ImagePickerActivity.ACTION_IMAGE_PICKED -> {
                    val paths = intent.getStringArrayListExtra(ImagePickerActivity.EXTRA_IMAGE_PATHS)
                    showWindow(Feature.CHAT)
                    if (!paths.isNullOrEmpty()) {
                        paths.take(5).forEach { path ->
                            if (pendingImages.size < 5) {
                                val file = File(path)
                                pendingImages.add(file)
                                try {
                                    BitmapFactory.decodeFile(path)?.let { pendingBitmaps.add(it) }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                        Handler(Looper.getMainLooper()).postDelayed({ updatePendingImagesUI() }, 200)
                    }
                }
                ACTION_SHOW_ICON -> showFloatingIcon()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        audioManager = AudioRecorderManager(this)
        screenCaptureManager = ScreenCaptureManager(this)
        
        setupManagerCallbacks()

        val filter = IntentFilter()
        filter.addAction(ImagePickerActivity.ACTION_IMAGE_PICKED)
        filter.addAction(ACTION_SHOW_ICON)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }

        checkAndReportCrash()
    }
    
    private fun setupManagerCallbacks() {
        audioManager.onTimerUpdate = { timeString -> updateRecordingTimerUI(timeString) }
        screenCaptureManager.onCaptureSuccess = { file, _ ->
             capturedImages.add(file)
             Toast.makeText(this, "Đã chụp (${capturedImages.size})", Toast.LENGTH_SHORT).show()
             captureBubbleView?.visibility = View.VISIBLE
        }
        screenCaptureManager.onError = { error ->
             logError(Exception(error))
             Toast.makeText(this, "Lỗi chụp: $error", Toast.LENGTH_SHORT).show()
             captureBubbleView?.visibility = View.VISIBLE
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else { 0 }

        if (intent?.action == ACTION_START_SCREEN_CAPTURE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
        }

        startForeground(1, createNotification(), types)

        when (intent?.action) {
            ACTION_STOP_SERVICE -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SHOW_HEAD -> showHead()
            ACTION_SHOW_POPUP -> {
                val phone = intent.getStringExtra("PHONE") ?: "Unknown"
                showWindow(Feature.WARNING)
                checkPhoneNumber(phone)
            }
            ACTION_START_SCREEN_CAPTURE -> {
                val resultCode = intent.getIntExtra("RESULT_CODE", 0)
                val data = IntentCompat.getParcelableExtra(intent, "DATA_INTENT", Intent::class.java)
                if (resultCode != 0 && data != null) {
                    screenCaptureManager.startProjection(resultCode, data, windowManager)
                    capturedImages.clear()
                    showCaptureBubble()
                }
            }
            ACTION_SHOW_ICON -> showFloatingIcon()
            else -> if (!isIconHidden) showHead()
        }

        return START_NOT_STICKY
    }

    private fun checkAndReportCrash() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("pending_crash_report", false)) {
            val log = prefs.getString("last_crash_log", "") ?: ""
            prefs.edit().putBoolean("pending_crash_report", false).apply()
            
            Toast.makeText(this, "Phát hiện lỗi lần trước. Đang gửi báo cáo...", Toast.LENGTH_LONG).show()
            val report = CrashReport(
                deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})",
                stackTrace = log
            )
            RetrofitClient.instance.reportCrash(report).enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {}
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
            })
        }
    }

    private fun hideFloatingIcon() {
        isIconHidden = true
        removeHead(); removeMenu(); removeWindow(); removeCaptureBubble(); removeRecordingUI()
        
        // Show persistent notification to restore via Broadcast
        val showIntent = Intent(ACTION_SHOW_ICON).setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ShieldCall đang ẩn")
            .setContentText("Chạm để hiện lại icon nổi")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
            
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(HIDE_NOTIFICATION_ID, notification)
        Toast.makeText(this, "Đã ẩn icon. Xem trong thông báo để hiện lại.", Toast.LENGTH_SHORT).show()
    }

    private fun showFloatingIcon() {
        isIconHidden = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(HIDE_NOTIFICATION_ID)
        showHead()
    }

    // --- SESSION VALIDATION ---
    private fun validateSessionAndExecute(onValid: () -> Unit) {
        if (!RetrofitClient.isNetworkAvailable()) {
            Toast.makeText(this, "Mất kết nối mạng. Vui lòng kiểm tra lại.", Toast.LENGTH_LONG).show()
            return
        }

        RetrofitClient.instance.checkSession(sessionId).enqueue(object : Callback<SessionStatusResponse> {
            override fun onResponse(call: Call<SessionStatusResponse>, response: Response<SessionStatusResponse>) {
                val status = response.body()
                if (status?.isValid == true) {
                    onValid()
                } else {
                    sessionId = status?.newSessionId ?: UUID.randomUUID().toString()
                    chatMessages.clear()
                    chatAdapter.notifyDataSetChanged()
                    Toast.makeText(this@OverlayService, "Phiên hết hạn. Đã tạo phiên mới.", Toast.LENGTH_SHORT).show()
                    onValid()
                }
            }
            override fun onFailure(call: Call<SessionStatusResponse>, t: Throwable) {
                Toast.makeText(this@OverlayService, "Không thể kết nối server.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- UI LOGIC ---

    private fun getThemedContext(): Context = ContextThemeWrapper(this, R.style.Theme_ShieldCallVN)

    private fun showHead() {
        if (isIconHidden || (headView != null && headView!!.isAttachedToWindow)) return
        removeMenu(); removeWindow(); removeRecordingUI(); removeCaptureBubble()

        headView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_floating_head, null)
        paramsHead = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 200 }

        setupDragListener(headView!!, paramsHead!!) { if (menuView == null) showMenu() else removeMenu() }
        windowManager.addView(headView, paramsHead)
    }

    private fun showMenu() {
        if (menuView != null && menuView!!.isAttachedToWindow) return
        menuView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_floating_menu, null)
        paramsMenu = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (paramsHead?.x ?: 0) + 160; y = paramsHead?.y ?: 200 }

        menuView?.findViewById<View>(R.id.btn_chat)?.setOnClickListener { removeMenu(); showWindow(Feature.CHAT) }
        menuView?.findViewById<View>(R.id.btn_record)?.setOnClickListener { removeMenu(); startRecordingFlow() }
        menuView?.findViewById<View>(R.id.btn_screenshot)?.setOnClickListener {
            removeMenu()
            val intent = Intent(this, ScreenCaptureActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(intent)
        }
        menuView?.findViewById<View>(R.id.btn_hide)?.setOnClickListener { hideFloatingIcon() }
        
        windowManager.addView(menuView, paramsMenu)
    }

    private fun showWindow(feature: Feature) {
        if (windowView != null && windowView!!.isAttachedToWindow) removeWindow()
        windowView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_window_chat, null)
        
        val flags = if (feature == Feature.WARNING) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        } else {
            // Allow focus for Chat so user can type
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        paramsWindow = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = if (feature == Feature.WARNING) Gravity.TOP else Gravity.CENTER
            dimAmount = 0.3f
            if (feature != Feature.WARNING) {
                this.flags = this.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            if (feature == Feature.WARNING) y = 100 
        }

        setupWindowUI(windowView!!, feature)
        windowManager.addView(windowView, paramsWindow)
    }

    private fun setupWindowUI(view: View, feature: Feature) {
        view.findViewById<View>(R.id.btn_close_window).setOnClickListener { removeWindow(); showHead() }
        view.findViewById<View>(R.id.img_reset_chat).setOnClickListener {
            sessionId = UUID.randomUUID().toString()
            chatMessages.clear()
            chatAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Đã làm mới cuộc hội thoại", Toast.LENGTH_SHORT).show()
        }

        when (feature) {
            Feature.CHAT -> {
                view.findViewById<TextView>(R.id.txt_title).text = "Chat AI Assistant"
                view.findViewById<View>(R.id.layout_loading).visibility = View.GONE
                view.findViewById<RecyclerView>(R.id.recycler_chat).visibility = View.VISIBLE
                view.findViewById<View>(R.id.layout_input).visibility = View.VISIBLE
                setupChat(view)
                updatePendingImagesUI()
            }
            Feature.RESULT -> {
                view.findViewById<TextView>(R.id.txt_title).text = "Kết quả phân tích"
                view.findViewById<View>(R.id.layout_loading).visibility = View.VISIBLE
                setupChat(view)
            }
            Feature.WARNING -> {
                view.findViewById<TextView>(R.id.txt_title).text = "Kiểm tra cuộc gọi..."
                view.findViewById<View>(R.id.layout_loading).visibility = View.VISIBLE
                view.findViewById<View>(R.id.layout_input).visibility = View.GONE
            }
        }
    }

    private fun setupChat(view: View) {
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_chat)
        if (!::chatAdapter.isInitialized) chatAdapter = ChatAdapter(chatMessages)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = chatAdapter

        val edtInput = view.findViewById<EditText>(R.id.edt_chat_input)
        view.findViewById<View>(R.id.btn_send).setOnClickListener {
            val content = edtInput.text.toString().trim()
            if (content.isNotEmpty() || pendingImages.isNotEmpty()) {
                validateSessionAndExecute {
                    val currentBitmaps = pendingBitmaps.toList()
                    val currentFiles = pendingImages.toList()
                    addMessage(content, true, currentBitmaps)
                    if (currentFiles.isNotEmpty()) uploadMessageWithImages(currentFiles)
                    else sendChatToAIStream(content)
                    
                    edtInput.setText("")
                    pendingImages.clear(); pendingBitmaps.clear(); updatePendingImagesUI()
                }
            }
        }
        
        view.findViewById<View>(R.id.btn_attach_image).setOnClickListener {
             removeWindow(); showHead()
             val intent = Intent(this, ImagePickerActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
             startActivity(intent)
        }
    }

    private fun updatePendingImagesUI() {
        if (windowView == null || !windowView!!.isAttachedToWindow) return
        val container = windowView!!.findViewById<LinearLayout>(R.id.layout_pending_images) ?: return
        val scrollView = windowView!!.findViewById<View>(R.id.scroll_pending_images) ?: return
        
        container.removeAllViews()
        if (pendingBitmaps.isNotEmpty()) {
            scrollView.visibility = View.VISIBLE
            pendingBitmaps.forEachIndexed { index, bitmap ->
                val frame = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(140, 140).apply { setMargins(8, 0, 8, 0) }
                }
                val iv = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }
                val btnDel = ImageView(this).apply {
                    val size = (24 * resources.displayMetrics.density).toInt()
                    layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.TOP or Gravity.END }
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    setBackgroundResource(R.drawable.bg_circle_shadow)
                    setPadding(4, 4, 4, 4)
                    setOnClickListener {
                        if (index < pendingImages.size && index < pendingBitmaps.size) { 
                            pendingImages.removeAt(index); pendingBitmaps.removeAt(index); updatePendingImagesUI() 
                        }
                    }
                }
                frame.addView(iv); frame.addView(btnDel); container.addView(frame)
            }
        } else {
            scrollView.visibility = View.GONE
        }
    }

    private fun addMessage(content: String, isUser: Boolean, images: List<Bitmap> = emptyList()) {
        chatMessages.add(ChatMessage(content, isUser, images))
        if (!::chatAdapter.isInitialized) chatAdapter = ChatAdapter(chatMessages)
        val recycler = windowView?.findViewById<RecyclerView>(R.id.recycler_chat)
        if (recycler != null && recycler.isAttachedToWindow) {
            if (recycler.adapter == null) { recycler.layoutManager = LinearLayoutManager(this); recycler.adapter = chatAdapter }
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            recycler.scrollToPosition(chatMessages.size - 1)
        }
    }

    private fun sendChatToAIStream(message: String) {
        addMessage("...", false); val aiIdx = chatMessages.size - 1
        RetrofitClient.instance.chatWithAIStream(ChatRequest(userMessage = message, sessionId = sessionId)).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    Thread { 
                        try {
                            val reader = BufferedReader(InputStreamReader(response.body()!!.byteStream()))
                            var line: String?; var full = ""
                            while (reader.readLine().also { line = it } != null) {
                                if (line!!.isNotBlank()) {
                                    full += line + "\n"
                                    windowView?.post { chatMessages[aiIdx] = ChatMessage(full, false); chatAdapter.notifyItemChanged(aiIdx) }
                                }
                            }
                        } catch (e: Exception) { logError(e) }
                    }.start()
                } else updateAiMessage(aiIdx, "Lỗi server: ${response.code()}")
            }
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) { updateAiMessage(aiIdx, "Lỗi mạng"); logError(t) }
        })
    }

    private fun uploadMessageWithImages(files: List<File>) {
        addMessage("...", false); val aiIdx = chatMessages.size - 1
        val parts = files.map { MultipartBody.Part.createFormData("images", it.name, it.asRequestBody("image/jpeg".toMediaTypeOrNull())) }
        RetrofitClient.instance.analyzeImages(parts).enqueue(object : Callback<ImageAnalysisResponse> {
            override fun onResponse(call: Call<ImageAnalysisResponse>, response: Response<ImageAnalysisResponse>) {
                val r = response.body()
                updateAiMessage(aiIdx, "Phân tích:\n${r?.ocrText}\n\nĐánh giá: ${r?.riskAnalysis?.riskLevel}")
            }
            override fun onFailure(call: Call<ImageAnalysisResponse>, t: Throwable) { updateAiMessage(aiIdx, "Lỗi gửi ảnh"); logError(t) }
        })
    }

    private fun checkPhoneNumber(phone: String) {
        if (!RetrofitClient.isNetworkAvailable()) return
        RetrofitClient.instance.checkPhoneNumber(phone).enqueue(object : Callback<RiskResponse> {
            override fun onResponse(call: Call<RiskResponse>, response: Response<RiskResponse>) {
                hideLoading()
                windowView?.findViewById<TextView>(R.id.txt_title)?.text = "SĐT: $phone"
                addMessage("Mức độ: ${response.body()?.riskLevel}\n${response.body()?.riskLabel}", false)
            }
            override fun onFailure(call: Call<RiskResponse>, t: Throwable) { hideLoading() }
        })
    }

    private fun startRecordingFlow() {
        removeWindow(); removeMenu(); removeCaptureBubble()
        if (!audioManager.isRecording()) {
            val isDebug = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("debug_mode", false)
            try {
                audioManager.startRecording(debugMode = isDebug, onStart = { showRecordingUI() }, onError = { logError(Exception(it)); showHead() })
            } catch (e: Exception) { logError(e); showHead() }
        } else showRecordingUI()
    }

    private fun showRecordingUI() {
        if (recordingTimerView != null && recordingTimerView!!.isAttachedToWindow) return
        removeWindow(); removeMenu(); removeCaptureBubble(); removeHead()
        recordingTimerView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_recording_timer, null)
        paramsRecordingTimer = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 100 }
        
        recordingStopView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_recording_stop, null)
        paramsRecordingStop = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 200 }

        recordingStopView?.findViewById<View>(R.id.btn_stop_recording_overlay)?.setOnClickListener {
             val file = audioManager.stopRecording(); removeRecordingUI()
             if (file != null) { uploadAudio(file) } else showHead()
        }
        setupDragListener(recordingTimerView!!, paramsRecordingTimer!!) {}
        setupDragListener(recordingStopView!!, paramsRecordingStop!!) {}
        windowManager.addView(recordingTimerView, paramsRecordingTimer); windowManager.addView(recordingStopView, paramsRecordingStop)
    }

    private fun showCaptureBubble() {
        if (captureBubbleView != null && captureBubbleView!!.isAttachedToWindow) return
        removeWindow(); removeMenu(); removeRecordingUI(); removeHead()
        captureBubbleView = LayoutInflater.from(getThemedContext()).inflate(R.layout.layout_capture_controls, null)
        paramsCaptureBubble = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 200 }
        setupDragListener(captureBubbleView!!, paramsCaptureBubble!!) {} 
        // Button Capture
        captureBubbleView?.findViewById<View>(R.id.btn_capture_frame)?.setOnClickListener {
            captureBubbleView?.visibility = View.GONE
            Handler(Looper.getMainLooper()).postDelayed({
                screenCaptureManager.captureFrame(windowManager)
            }, 150)
        }
        captureBubbleView?.findViewById<View>(R.id.btn_finish_capture)?.setOnClickListener {
             removeCaptureBubble(); screenCaptureManager.stopCapture()
             if (capturedImages.isNotEmpty()) {
                 val bitmaps = capturedImages.mapNotNull { f -> try { BitmapFactory.decodeFile(f.absolutePath) } catch(e: Exception) { null } }
                 addMessage("Ảnh chụp màn hình", true, bitmaps)
                 showWindow(Feature.RESULT)
                 uploadMultipleScreenshots(capturedImages.toList())
                 capturedImages.clear()
             } else showHead()
        }
        windowManager.addView(captureBubbleView, paramsCaptureBubble)
    }

    private fun updateAiMessage(idx: Int, txt: String) {
        if (idx in chatMessages.indices) { chatMessages[idx] = ChatMessage(txt, false); chatAdapter.notifyItemChanged(idx) }
    }

    private fun uploadAudio(f: File) {
        showWindow(Feature.RESULT); windowView?.findViewById<TextView>(R.id.txt_loading_status)?.text = "Đang gửi file..."
        val body = MultipartBody.Part.createFormData("audio", f.name, f.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
        RetrofitClient.instance.analyzeAudio(body, "Unknown".toRequestBody("text/plain".toMediaTypeOrNull())).enqueue(object : Callback<AudioAnalysisResponse> {
            override fun onResponse(call: Call<AudioAnalysisResponse>, response: Response<AudioAnalysisResponse>) {
                hideLoading(); val res = response.body(); val p = if (res?.isScam == true) "⚠ CẢNH BÁO: " else "✅ An toàn: "
                addMessage("$p${res?.warningMessage}\n${res?.transcript}", false)
            }
            override fun onFailure(call: Call<AudioAnalysisResponse>, t: Throwable) { hideLoading(); logError(t) }
        })
    }

    private fun uploadMultipleScreenshots(fs: List<File>) {
        val parts = fs.map { MultipartBody.Part.createFormData("images", it.name, it.asRequestBody("image/jpeg".toMediaTypeOrNull())) }
        RetrofitClient.instance.analyzeImages(parts).enqueue(object : Callback<ImageAnalysisResponse> {
            override fun onResponse(call: Call<ImageAnalysisResponse>, r: Response<ImageAnalysisResponse>) {
                hideLoading(); val b = r.body(); addMessage("OCR:\n${b?.ocrText}\n\nĐánh giá: ${b?.riskAnalysis?.riskLevel}", false)
            }
            override fun onFailure(call: Call<ImageAnalysisResponse>, t: Throwable) { hideLoading(); logError(t) }
        })
    }

    private fun updateRecordingTimerUI(t: String) {
        if (recordingTimerView?.isAttachedToWindow == true) recordingTimerView!!.findViewById<TextView>(R.id.txt_recording_time)?.text = t
    }

    private fun removeHead() { if (headView?.isAttachedToWindow == true) { windowManager.removeView(headView); headView = null } }
    private fun removeMenu() { if (menuView?.isAttachedToWindow == true) { windowManager.removeView(menuView); menuView = null } }
    private fun removeWindow() { if (windowView?.isAttachedToWindow == true) { windowManager.removeView(windowView); windowView = null } }
    private fun removeRecordingUI() {
        if (recordingTimerView?.isAttachedToWindow == true) { windowManager.removeView(recordingTimerView); recordingTimerView = null }
        if (recordingStopView?.isAttachedToWindow == true) { windowManager.removeView(recordingStopView); recordingStopView = null }
    }
    private fun removeCaptureBubble() { if (captureBubbleView?.isAttachedToWindow == true) { windowManager.removeView(captureBubbleView); captureBubbleView = null } }

    private fun hideLoading() {
        windowView?.findViewById<View>(R.id.layout_loading)?.visibility = View.GONE
        windowView?.findViewById<View>(R.id.recycler_chat)?.visibility = View.VISIBLE
    }

    private fun createNotification(): Notification {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID).setContentTitle("ShieldCall đang chạy").setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText("Bấm để mở ứng dụng").setContentIntent(intent).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun logError(t: Throwable) {
        try {
            val f = File(externalCacheDir, "error_logs.txt"); val w = FileWriter(f, true)
            w.append("---" + System.currentTimeMillis() + "---\n${t.message}\n${t.stackTraceToString()}\n\n"); w.close()
        } catch (e: Exception) {}
    }

    private fun setupDragListener(v: View, p: WindowManager.LayoutParams, onClick: () -> Unit) {
        val cardHead = v.findViewById<View>(R.id.card_head)
        v.setOnTouchListener(object : View.OnTouchListener {
            private var sx = 0; private var sy = 0; private var tx = 0f; private var ty = 0f; private var ic = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { 
                        sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; ic = true
                        cardHead?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(100)?.start()
                        return true 
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) { 
                            ic = false; p.x = sx + dx; p.y = sy + dy
                            try { windowManager.updateViewLayout(view, p) } catch (e: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> { 
                        cardHead?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(100)?.start()
                        if (ic) onClick(); return true 
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        removeHead(); removeMenu(); removeWindow(); removeRecordingUI(); removeCaptureBubble()
        audioManager.stopRecording(); screenCaptureManager.stopCapture()
    }
}
