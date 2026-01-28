package com.sentinel.antiscamvn

import android.app.Application
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShieldCallApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Check Debug Mode preference
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val isDebug = prefs.getBoolean("debug_mode", false)

                if (isDebug) {
                    saveCrashLog(thread, throwable)
                } else {
                    // Save crash trace to prefs to ask user on next start
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    prefs.edit()
                        .putString("last_crash_log", sw.toString())
                        .putBoolean("pending_crash_report", true)
                        .apply()
                }
            } catch (e: Exception) {
                Log.e("ShieldCall", "Failed to save crash log", e)
            } finally {
                // Pass control back to the default handler to let the app crash/close properly
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "ShieldCall_Crash_$timestamp.txt"
            
            // Generate Log Content
            val content = StringBuilder()
            content.append("=== SHIELDCALL VN CRASH LOG ===\n")
            content.append("Time: $timestamp\n")
            content.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})\n")
            content.append("Thread: ${thread.name}\n")
            content.append("Debug Mode: ON\n\n")
            content.append("--- STACK TRACE ---\n")
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            content.append(sw.toString())

            // Save to Public Downloads folder using MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toString().toByteArray())
                    }
                }
            } else {
                // For older Android versions (< 10), save to legacy External Storage
                val logDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!logDir.exists()) logDir.mkdirs()
                val file = File(logDir, fileName)
                FileWriter(file).use { it.write(content.toString()) }
            }
            
            Log.e("ShieldCall", "Crash log saved to Downloads folder: $fileName")

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ShieldCall", "Failed to save crash log to Downloads", e)
        }
    }
}
