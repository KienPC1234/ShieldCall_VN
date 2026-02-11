package com.sentinel.antiscamvn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class AudioPickerActivity : AppCompatActivity() {

    companion object {
        const val ACTION_AUDIO_PICKED = "com.sentinel.antiscamvn.AUDIO_PICKED"
        const val EXTRA_AUDIO_PATHS = "audio_paths"
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleAudioResult(result.data)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/wav", "audio/ogg", "audio/aac", "audio/mp4", "audio/amr", "application/octet-stream"))
        }
        pickAudioLauncher.launch(Intent.createChooser(intent, "Chọn file ghi âm"))
    }

    private fun handleAudioResult(data: Intent?) {
        val paths = ArrayList<String>()
        try {
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    saveAudioToCache(uri)?.let { paths.add(it) }
                }
            } else if (data?.data != null) {
                saveAudioToCache(data.data!!)?.let { paths.add(it) }
            }

            if (paths.isNotEmpty()) {
                val intent = Intent(ACTION_AUDIO_PICKED)
                intent.putStringArrayListExtra(EXTRA_AUDIO_PATHS, paths)
                sendBroadcast(intent)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi chọn file âm thanh", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    private fun saveAudioToCache(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            // Keep original extension or use .pcm/mp3 if unknown
            val file = File(externalCacheDir, "upload_audio_${System.currentTimeMillis()}_${(0..1000).random()}.bin")
            val outputStream = FileOutputStream(file)

            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}