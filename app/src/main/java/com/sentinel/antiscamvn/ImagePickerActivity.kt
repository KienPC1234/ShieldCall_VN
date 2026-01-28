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

class ImagePickerActivity : AppCompatActivity() {

    companion object {
        const val ACTION_IMAGE_PICKED = "com.sentinel.antiscamvn.IMAGE_PICKED"
        const val EXTRA_IMAGE_PATHS = "image_paths" // Changed to plural
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleImageResult(result.data)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickImageLauncher.launch(intent)
    }

    private fun handleImageResult(data: Intent?) {
        val paths = ArrayList<String>()
        try {
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    saveImageToCache(uri)?.let { paths.add(it) }
                }
            } else if (data?.data != null) {
                saveImageToCache(data.data!!)?.let { paths.add(it) }
            }

            if (paths.isNotEmpty()) {
                val intent = Intent(ACTION_IMAGE_PICKED)
                intent.putStringArrayListExtra(EXTRA_IMAGE_PATHS, paths)
                sendBroadcast(intent)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi chọn ảnh", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    private fun saveImageToCache(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(externalCacheDir, "upload_image_${System.currentTimeMillis()}_${(0..1000).random()}.jpg")
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