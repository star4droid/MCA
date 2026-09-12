package com.star4droid.mc.animation

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class McaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeLogToFile(thread, throwable)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeLogToFile(thread: Thread, throwable: Throwable) {
        val baseDir = getExternalFilesDir(null) ?: filesDir
        val logsDir = File(baseDir, "logs").apply {
            if (!exists()) mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(logsDir, "crash_$timestamp.txt")

        FileWriter(logFile, true).use { fw ->
            PrintWriter(fw).use { pw ->
                pw.println("==================================================")
                pw.println("MCA CRASH REPORT - $timestamp")
                pw.println("Thread: ${thread.name} (id: ${thread.id})")
                pw.println("Exception: ${throwable.javaClass.name}")
                pw.println("Message: ${throwable.message}")
                pw.println("==================================================")
                throwable.printStackTrace(pw)
                pw.println("\n")
            }
        }
        Log.e("McaApplication", "Uncaught exception logged to ${logFile.absolutePath}")
    }
}
