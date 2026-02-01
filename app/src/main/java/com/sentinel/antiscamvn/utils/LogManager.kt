package com.sentinel.antiscamvn.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private const val MAX_LOG_FILES = 15
    private const val LOG_DIR_NAME = "app_logs"
    private var currentLogFile: File? = null

    fun init(context: Context) {
        val logDir = File(context.filesDir, LOG_DIR_NAME)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        rotateLogs(logDir)
        createNewLogFile(logDir)
    }

    private fun createNewLogFile(logDir: File) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "log_$timestamp.txt"
        currentLogFile = File(logDir, fileName)
        log("LogManager", "Session started: $timestamp")
    }

    private fun rotateLogs(logDir: File) {
        val files = logDir.listFiles() ?: return
        if (files.size >= MAX_LOG_FILES) {
            // Sort by modification time (oldest first)
            val sortedFiles = files.sortedBy { it.lastModified() }
            val filesToDelete = sortedFiles.take(files.size - MAX_LOG_FILES + 1) // +1 because we are about to create a new one
            
            for (file in filesToDelete) {
                file.delete()
            }
        }
    }

    fun log(tag: String, message: String) {
        // Log to Logcat
        Log.d(tag, message)

        // Write to file
        val file = currentLogFile ?: return
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp [$tag] $message\n"

        try {
            val writer = FileWriter(file, true)
            writer.append(logEntry)
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    
    fun getLogFiles(context: Context): List<File> {
        val logDir = File(context.filesDir, LOG_DIR_NAME)
        return logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun readLogFile(file: File): String {
        return try {
            file.readText()
        } catch (e: IOException) {
            "Error reading file: ${e.message}"
        }
    }
}
