package com.sentinel.antiscamvn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ScreenCaptureActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    companion object {
        const val ACTION_SCREENSHOT_CAPTURED = "com.sentinel.antiscamvn.SCREENSHOT_CAPTURED"
        const val EXTRA_SCREENSHOT_PATH = "screenshot_path"
    }

    private val screenshotLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startCapture(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Đã hủy chụp màn hình", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Yêu cầu quyền ngay khi Activity mở lên
        screenshotLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val density = metrics.densityDpi
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        // Đợi một chút để màn hình render
        Handler(Looper.getMainLooper()).postDelayed({
            captureImage()
        }, 1000)
    }

    private fun captureImage() {
        val image = imageReader?.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Crop phần padding thừa nếu có
            val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)

            image.close()
            saveBitmap(finalBitmap)
        } else {
            finish()
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        try {
            val file = File(externalCacheDir, "screenshot_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
            fos.close()

            // Gửi Broadcast báo cho Service biết
            val intent = Intent(ACTION_SCREENSHOT_CAPTURED)
            intent.putExtra(EXTRA_SCREENSHOT_PATH, file.absolutePath)
            sendBroadcast(intent)
            
            Toast.makeText(this, "Đã chụp màn hình", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cleanup()
            finish()
        }
    }

    private fun cleanup() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
    }
}
