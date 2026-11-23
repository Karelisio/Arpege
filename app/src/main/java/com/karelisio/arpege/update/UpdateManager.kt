package com.karelisio.arpege.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.karelisio.arpege.BuildConfig
import com.karelisio.arpege.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: SemVer, val changelog: String, val apkUrl: String, val apkSize: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Installing(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Mise à jour in-app : interroge releases/latest (au plus toutes les 6 h),
 * télécharge l'APK puis l'installe via PackageInstaller.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    fun checkIfDue(force: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            val now = System.currentTimeMillis()
            if (!force && now - settings.lastUpdateCheck() < SIX_HOURS) return@launch
            _state.value = UpdateState.Checking
            _state.value = runCatching { fetchLatest() }.fold(
                onSuccess = { rel ->
                    settings.setLastUpdateCheck(now)
                    val current = SemVer.parse(BuildConfig.VERSION_NAME)
                    if (rel != null && current != null && rel.version > current) UpdateState.Available(rel)
                    else UpdateState.UpToDate
                },
                onFailure = { if (force) UpdateState.Failed(it.message ?: "Réseau indisponible") else UpdateState.Idle },
            )
        }
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    fun download(release: ReleaseInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            runCatching {
                val file = downloadApk(release) { p -> _state.value = UpdateState.Downloading(release, p) }
                _state.value = UpdateState.Installing(release)
                install(file)
            }.onFailure { _state.value = UpdateState.Failed(it.message ?: "Échec du téléchargement") }
        }
    }

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) return@withContext null
            parseRelease(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadApk(release: ReleaseInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "arpege-${release.version}.apk")
            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            try {
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastEmit = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0 && read - lastEmit > 128 * 1024) {
                                lastEmit = read
                                onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgress(1f)
                out
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("arpege.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    companion object {
        private const val SIX_HOURS = 6L * 60 * 60 * 1000

        fun parseRelease(json: String): ReleaseInfo? {
            val o = JSONObject(json)
            val version = SemVer.parse(o.optString("tag_name")) ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return ReleaseInfo(version, o.optString("body"), a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            return null
        }
    }
}
