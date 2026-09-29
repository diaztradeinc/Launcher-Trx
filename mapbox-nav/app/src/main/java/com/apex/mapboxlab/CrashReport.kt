package com.apex.mapboxlab

import android.app.ActivityManager
import android.app.ApplicationExitInfo
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
    private var lastSurface = ""
    fun surface(width: Int, height: Int, pixelRatio: Float) {
        val value = "${width}x${height} px, ratio=$pixelRatio"
        if (value == lastSurface) return
        lastSurface = value
        app.getSharedPreferences("startup-diagnostics", Context.MODE_PRIVATE).edit()
            .putString("surface", value).commit()
    }
    fun read(): String = buildString {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        appendLine("TRX Mapbox Lab ${info.versionName}")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.joinToString()}")
        val prefs = app.getSharedPreferences("startup-diagnostics", Context.MODE_PRIVATE)
        appendLine("Last stage: ${prefs.getString("stage", "Not started")}")
        appendLine("Map surface: ${prefs.getString("surface", "Not recorded")}")
        appendLine("Stage time: ${prefs.getLong("time", 0)}")
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val manager = app.getSystemService(ActivityManager::class.java)
            manager.getHistoricalProcessExitReasons(app.packageName, 0, 5).forEachIndexed { index, it ->
                appendLine("Process exit: reason=${it.reason}, status=${it.status}, time=${it.timestamp}, ${clean(it.description.orEmpty())}")
                if (Build.VERSION.SDK_INT >= 31 && it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE && index < 3) {
                    val native = runCatching {
                        it.traceInputStream?.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(out.size() + count <= NativeTombstone.MAX_BYTES) { "Trace exceeds size limit" }
                                out.write(buffer, 0, count)
                            }
                            NativeTombstone.summarize(out.toByteArray())
                        } ?: "Android no longer has a native trace for this exit."
                    }.getOrElse { error -> "Native trace unavailable: ${error.javaClass.simpleName}" }
                    appendLine(clean(native))
                }
            }
        }
        val crash = File(app.filesDir, "last-crash.txt")
        appendLine(if (crash.exists()) crash.readText() else "No Java crash recorded. Native failures may only report an exit reason and last stage.")
    }
    private fun clean(value: String) = value
        .replace(Regex("[ps]k\\.[A-Za-z0-9_.-]+"), "[REDACTED]")
        .replace(Regex("https?://\\S+"), "[URL]")
}
