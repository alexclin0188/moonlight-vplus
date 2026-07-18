package com.alexclin.moonlink.android.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider

import com.alexclin.moonlink.android.BuildConfig

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight on-device crash reporter.
 *
 * Complements Firebase Crashlytics for users on builds that ship without
 * `google-services.json` (forks, sideloads, F-Droid-style distributions).
 * The collected text files are meant to be shared by the user manually next
 * time they launch the app — see [pendingReportFile] / [shareIntentFor].
 *
 * Design choices:
 * - Chains to the previously-installed [Thread.UncaughtExceptionHandler] so
 *   Crashlytics still fires when both are active.
 * - Writes synchronously: the process is dying, so a queued task may never run.
 * - Catches and swallows any failure during write — the user-visible crash
 *   dialog must not be replaced by a nested crash from our reporter.
 * - Keeps up to [MAX_FILES] timestamped crash logs; the oldest are deleted
 *   when the limit is exceeded.
 */
object CrashReporter {

    private const val DIR_NAME = "crash"
    private const val FILE_PREFIX = "crash_"
    private const val FILE_SUFFIX = ".txt"

    /** Maximum number of crash log files to retain. */
    private const val MAX_FILES = 10

    /** Date-time format used in the filename (sortable, no colons on Windows). */
    private val FILE_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(app, thread, throwable)
            } catch (_: Throwable) {
                // Swallow — never let the reporter replace the original crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** @return the most recent saved crash file if one is pending, else null. */
    fun pendingReportFile(ctx: Context): File? {
        val files = listCrashFiles(ctx)
        return if (files.isNotEmpty()) files.last() else null
    }

    /** Read the most recent report's contents, or null on any I/O failure. */
    fun readReport(ctx: Context): String? = runCatching {
        pendingReportFile(ctx)?.readText()
    }.getOrNull()

    /** Best-effort delete of all saved crash reports. */
    fun clear(ctx: Context) {
        listCrashFiles(ctx).forEach { it.delete() }
    }

    /**
     * Create an [Intent.ACTION_SEND] intent for the most recent crash report,
     * or null if there is no pending report.
     */
    fun shareIntentFor(ctx: Context): Intent? {
        val file = pendingReportFile(ctx) ?: return null
        val uri = FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ── internal helpers ──────────────────────────────────────────────────

    /** List crash log files sorted by name (which is chronological). */
    private fun listCrashFiles(ctx: Context): List<File> {
        val dir = File(ctx.filesDir, DIR_NAME)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun writeReport(ctx: Context, thread: Thread, throwable: Throwable) {
        val dir = File(ctx.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()

        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            pw.println("=== Moonlight crash report ===")
            pw.println("time:        $time")
            pw.println("thread:      ${thread.name}")
            pw.println("appVersion:  ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            pw.println("build:       ${BuildConfig.BUILD_TYPE}")
            pw.println("device:      ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            pw.println("android:     ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            pw.println("abi:         ${Build.SUPPORTED_ABIS.joinToString()}")
            pw.println("---- stack ----")
            throwable.printStackTrace(pw)
        }

        // Write to a timestamped file
        val fileName = FILE_PREFIX + FILE_DATE_FORMAT.format(Date()) + FILE_SUFFIX
        File(dir, fileName).writeText(sw.toString())

        // Prune old files beyond the limit
        pruneOldFiles(dir)
    }

    /** Delete the oldest crash files when the total exceeds [MAX_FILES]. */
    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            ?.sortedBy { it.name }
            ?: return

        val excess = files.size - MAX_FILES
        if (excess > 0) {
            files.take(excess).forEach { it.delete() }
        }
    }
}
