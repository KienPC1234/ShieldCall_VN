package com.sentinel.antiscamvn.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sentinel.antiscamvn.R
import com.sentinel.antiscamvn.network.*
import com.sentinel.antiscamvn.utils.LogManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

class AudioCaptureService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        
        var onWarningReceived: ((String) -> Unit)? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var outputStream: FileOutputStream? = null
    private var currentFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != 0 && resultData != null) {
                    startCapture(resultCode, resultData)
                }
            }
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "AudioCaptureChannel")
            .setContentTitle("ShieldCall Analysis")
            .setContentText("Recording and analyzing call...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1002, notification)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        try {
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setAudioPlaybackCaptureConfig(config)
                .setBufferSizeInBytes(bufferSize * 2)
                .build()

            val fileName = "CallRecord_${System.currentTimeMillis()}.pcm"
            currentFile = File(getExternalFilesDir(null), fileName)
            outputStream = FileOutputStream(currentFile)
            
            isRecording = true
            audioRecord?.startRecording()
            
            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        outputStream?.write(buffer, 0, read)
                    }
                }
            }.start()
            
        } catch (e: Exception) {
             LogManager.log("AudioCapture", "Error: ${e.message}")
             stopCapture()
        }
    }

    private fun stopCapture() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            mediaProjection?.stop()
            outputStream?.close()
        } catch (e: Exception) { }
        
        // Upload file after finishing recording
        currentFile?.let { file ->
            uploadAndAnalyze(file)
        }
        
        stopForeground(true)
        stopSelf()
    }

    private fun uploadAndAnalyze(file: File) {
        val requestFile = file.asRequestBody("audio/pcm".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("audio", file.name, requestFile)
        val phoneBody = "AutoCaptured".toRequestBody("text/plain".toMediaTypeOrNull())

        RetrofitClient.instance.analyzeAudio(body, phoneBody).enqueue(object : Callback<AudioAnalysisResponse> {
            override fun onResponse(call: Call<AudioAnalysisResponse>, response: Response<AudioAnalysisResponse>) {
                val res = response.body()
                if (response.isSuccessful && res != null) {
                    // Send notification or trigger callback for UI
                    val message = if (res.isScam) "⚠ CẢNH BÁO LỪA ĐẢO: ${res.warningMessage}" else "✅ Cuộc gọi an toàn"
                    onWarningReceived?.invoke(message)
                    showResultNotification(message)
                }
            }
            override fun onFailure(call: Call<AudioAnalysisResponse>, t: Throwable) {
                LogManager.log("AudioCapture", "Upload failed: ${t.message}")
            }
        })
    }

    private fun showResultNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "AudioCaptureChannel")
            .setContentTitle("Kết quả phân tích cuộc gọi")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(1003, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "AudioCaptureChannel",
                "Audio Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}