package ru.autolog.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.autolog.app.BuildConfig

/** Checks GitHub Releases for a newer APK and installs it in-app. */
object AppUpdateChecker {
    data class RemoteRelease(
        val versionName: String,
        val tagName: String,
        val apkUrl: String,
        val apkName: String,
        /** Business “what’s new” from GitHub release body; empty if none. */
        val notes: String = "",
    )

    sealed class CheckResult {
        data class UpToDate(val current: String, val remote: String) : CheckResult()
        data class UpdateAvailable(val release: RemoteRelease) : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
                ?: return@withContext CheckResult.Failed("В GitHub нет релиза с APK")
            val current = BuildConfig.APP_VERSION
            if (isNewer(release.versionName, current)) {
                CheckResult.UpdateAvailable(release)
            } else {
                CheckResult.UpToDate(current = current, remote = release.versionName)
            }
        }.getOrElse {
            CheckResult.Failed(it.message ?: "Не удалось проверить обновления")
        }
    }

    suspend fun downloadApk(context: Context, release: RemoteRelease): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val out = File(dir, "autolog-update.apk")
            if (out.exists()) out.delete()
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Autolog/${BuildConfig.APP_VERSION}")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    error("Скачивание APK: HTTP ${conn.responseCode}")
                }
                conn.inputStream.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                conn.disconnect()
            }
            if (!out.exists() || out.length() < 1_000L) {
                error("Скачанный APK пустой или слишком маленький")
            }
            out
        }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** True if [remote] is strictly newer than [local] (MAJOR.MINOR.PATCH). */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote) ?: return false
        val l = parseVersion(local) ?: return false
        return compareVersions(r, l) > 0
    }

    private fun fetchLatestRelease(): RemoteRelease? {
        val url =
            "https://api.github.com/repos/${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}/releases/latest"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Autolog/${BuildConfig.APP_VERSION}")
        }
        try {
            if (conn.responseCode !in 200..299) {
                error("GitHub: HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").orEmpty()
            val versionName = normalizeVersion(tag.ifBlank { json.optString("name") })
                ?: error("Непонятная версия в релизе")
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            var apkName = "autolog.apk"
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name").orEmpty()
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    apkName = name
                    break
                }
            }
            val urlApk = apkUrl ?: return null
            return RemoteRelease(
                versionName = versionName,
                tagName = tag.ifBlank { versionName },
                apkUrl = urlApk,
                apkName = apkName,
                notes = sanitizeReleaseNotes(
                    if (json.isNull("body")) null else json.optString("body"),
                ),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Trims GitHub release body for in-app display (plain business notes). */
    fun sanitizeReleaseNotes(raw: String?): String {
        // org.json optString() turns JSON null into the literal "null".
        if (raw.isNullOrBlank() || raw.equals("null", ignoreCase = true)) return ""
        return raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
            .lines()
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
            .let { if (it.equals("null", ignoreCase = true)) "" else it }
    }

    private fun normalizeVersion(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        return if (parseVersion(cleaned) != null) cleaned else null
    }

    private fun parseVersion(raw: String): Triple<Int, Int, Int>? {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split('.')
        if (parts.size < 2) return null
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    private fun compareVersions(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        return compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
    }
}
