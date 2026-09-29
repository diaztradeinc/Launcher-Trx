package com.apex.mapboxlab

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/** Local, user-copyable diagnostics. Never sends data or includes the saved token. */
object CrashReport {
    private lateinit var app: Context
    fun install(context: Context) {
        app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, "last-crash.txt").writeText(clean(error.stackTraceToString()).take(16000)) }
            previous?.uncaughtException(thread, error)
                ?: android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    fun stage(value: String) {
        app.getSharedPreferences("startup-diagnostics", Context.MODE_PRIVATE).edit()
            .putString("stage", value).putLong("time", System.currentTimeMillis()).commit()
    }
    fun read(): String = buildString {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        appendLine("TRX Mapbox Lab ${info.versionName}")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.joinToString()}")
        val prefs = app.getSharedPreferences("startup-diagnostics", Context.MODE_PRIVATE)
        appendLine("Last stage: ${prefs.getString("stage", "Not started")}")
        appendLine("Stage time: ${prefs.getLong("time", 0)}")
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val manager = app.getSystemService(ActivityManager::class.java)
            manager.getHistoricalProcessExitReasons(app.packageName, 0, 3).forEach {
                appendLine("Process exit: reason=${it.reason}, status=${it.status}, time=${it.timestamp}, ${clean(it.description.orEmpty())}")
            }
        }
        val crash = File(app.filesDir, "last-crash.txt")
        appendLine(if (crash.exists()) crash.readText() else "No Java crash recorded. Native failures may only report an exit reason and last stage.")
    }
    private fun clean(value: String) = value
        .replace(Regex("[ps]k\\.[A-Za-z0-9_.-]+"), "[REDACTED]")
        .replace(Regex("https?://\\S+"), "[URL]")
}
