package com.sentinel.antiscamvn.manager

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.Timer
import java.util.TimerTask

class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null
    private var recordTimer: Timer? = null
    private var recordSeconds = 0
    
    // Callback cập nhật thời gian lên UI
    var onTimerUpdate: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission") // Quyền đã được check ở Activity/Service
    fun startRecording(onStart: () -> Unit, onError: (String) -> Unit) {
        audioFile = File(context.externalCacheDir, "record_${System.currentTimeMillis()}.mp3")
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
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
            startTimer()
            onStart()
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            onError(e.message ?: "Unknown error")
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        
        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            // Lỗi thường gặp nếu stop quá sớm
            audioFile = null
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            stopTimer()
            isRecording = false
        }
        
        return if (audioFile != null && audioFile!!.exists()) audioFile else null
    }

    fun isRecording() = isRecording

    private fun startTimer() {
        recordTimer = Timer()
        recordTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                recordSeconds++
                val min = recordSeconds / 60
                val sec = recordSeconds % 60
                val timeString = String.format("%02d:%02d", min, sec)
                
                // Đẩy về Main Thread
                Handler(Looper.getMainLooper()).post {
                    onTimerUpdate?.invoke(timeString)
                }
            }
        }, 1000, 1000)
    }

    private fun stopTimer() {
        recordTimer?.cancel()
        recordTimer = null
    }
}