package com.alexclin.moonlink.android.stream.engine

import android.content.Context
import android.os.Environment
import com.alexclin.moonlink.android.util.LimeLog
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 串流启动过程文件日志，输出到应用私有目录 Downloads/moonlink_stream.log
 * 无需额外权限，所有 Android 版本均可写入。
 */
object StreamLogger {
    private const val FILE_NAME = "moonlink_stream.log"
    private var file: File? = null
    private var lastContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun ensureFile(context: Context): File? {
        if (file == null || !file!!.exists() || lastContext !== context) {
            try {
                lastContext = context
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (dir == null) {
                    // 极少数设备 getExternalFilesDir 返回 null，回退到 filesDir
                    File(context.filesDir, "Download").also { it.mkdirs() }
                    file = File(File(context.filesDir, "Download"), FILE_NAME)
                } else {
                    if (!dir.exists()) dir.mkdirs()
                    file = File(dir, FILE_NAME)
                }
                // 写文件头（覆盖旧日志）
                file?.writeText("=== MoonLink 串流日志 ${dateFormat.format(Date())} ===\n")
            } catch (e: Exception) {
                LimeLog.warning("StreamLogger: 创建日志文件失败 ${e.message}")
                return null
            }
        }
        return file
    }

    fun log(context: Context, tag: String, message: String) {
        val time = dateFormat.format(Date())
        val line = "[$time] [$tag] $message\n"
        LimeLog.info("StreamLogger[$tag] $message")
        try {
            val f = ensureFile(context) ?: return
            FileWriter(f, true).use { it.append(line) }
        } catch (e: Exception) {
            // 静默失败，不影响串流
        }
    }

    fun logParams(context: Context, tag: String, params: Map<String, Any?>) {
        val sb = StringBuilder("$tag:\n")
        for ((key, value) in params) {
            sb.append("  $key = $value\n")
        }
        log(context, "PARAMS", sb.toString())
    }
}
