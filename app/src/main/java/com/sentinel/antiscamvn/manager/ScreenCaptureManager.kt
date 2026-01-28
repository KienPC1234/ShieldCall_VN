package com.sentinel.antiscamvn.manager

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // Callback trả về kết quả
    var onCaptureSuccess: ((File, Bitmap) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun startProjection(resultCode: Int, data: Intent, windowManager: WindowManager) {
        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        // Android 14 requirement: Register a callback before creating VirtualDisplay
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopCapture()
            }
        }, Handler(Looper.getMainLooper()))

        val width: Int
        val height: Int
        val density: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            width = metrics.bounds.width()
            height = metrics.bounds.height()
            density = context.resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
            density = metrics.densityDpi
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

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
    }

    fun captureFrame(windowManager: WindowManager) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        processImage(width)
    }

    private fun processImage(width: Int) {
        // Xử lý trên Background Thread
        Thread {
            try {
                // Đợi một chút để ImageReader có dữ liệu nếu vừa khởi tạo
                Thread.sleep(100)
                
                var image = imageReader?.acquireLatestImage()
                // Retry nếu null
                if (image == null) {
                    Thread.sleep(200)
                    image = imageReader?.acquireLatestImage()
                }

                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = createBitmap(width + rowPadding / pixelStride, image.height)
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Crop phần thừa
                    val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, image.height)
                    image.close()

                    // Lưu file
                    val file = File(context.externalCacheDir, "screenshot_${System.currentTimeMillis()}.jpg")
                    val fos = FileOutputStream(file)
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.close()

                    // Trả kết quả về Main Thread
                    Handler(Looper.getMainLooper()).post {
                        onCaptureSuccess?.invoke(file, finalBitmap)
                    }
                } else {
                     Handler(Looper.getMainLooper()).post {
                        onError?.invoke("Không lấy được hình ảnh (null)")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    onError?.invoke(e.message ?: "Capture failed")
                }
            }
        }.start()
    }

    fun stopCapture() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        virtualDisplay = null
        mediaProjection = null
        imageReader = null
    }
}