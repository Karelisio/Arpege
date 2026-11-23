package com.karelisio.arpege.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.karelisio.arpege.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rapport de plantage 100 % local : la trace est écrite dans un fichier, puis proposée au
 * prochain lancement (partage manuel par l'utilisateur, rien n'est envoyé automatiquement).
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(format(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }

    fun shareIntent(report: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Arpège ${BuildConfig.VERSION_NAME} — rapport de plantage")
            .putExtra(Intent.EXTRA_TEXT, report),
        "Partager le rapport",
    )

    private fun format(thread: Thread, error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())
        return buildString {
            appendLine("Arpège ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) — $date")
            appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread : ${thread.name}")
            appendLine()
            append(sw.toString().take(12_000))
        }
    }
}
