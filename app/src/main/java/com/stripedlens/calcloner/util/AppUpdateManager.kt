package com.stripedlens.calcloner.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val tagName: String,
    val downloadUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val releaseUrl: String
)

object AppUpdateManager {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/theamazingaustin/CalCloner/releases/latest"

    /**
     * Compares release tag strings formatted like `v<major>.<minor>-<month>.<day>-<build>`.
     * Returns true if [remoteTag] is strictly newer than [localTag].
     */
    fun isNewerTag(remoteTag: String, localTag: String): Boolean {
        if (remoteTag.isBlank() || localTag.isBlank()) return false
        if (remoteTag.trim().equals(localTag.trim(), ignoreCase = true)) return false

        // Regex capturing: major.minor - month.day - build
        // e.g. v2.2-9.18-4 or v2.2
        val regex = Regex("""^v?(\d+)\.(\d+)(?:-(\d+)\.(\d+)-(\d+))?""")
        val remoteMatch = regex.matchEntire(remoteTag.trim())
        val localMatch = regex.matchEntire(localTag.trim())

        if (remoteMatch != null && localMatch != null) {
            val rMajor = remoteMatch.groupValues[1].toIntOrNull() ?: 0
            val lMajor = localMatch.groupValues[1].toIntOrNull() ?: 0
            if (rMajor != lMajor) return rMajor > lMajor

            val rMinor = remoteMatch.groupValues[2].toIntOrNull() ?: 0
            val lMinor = localMatch.groupValues[2].toIntOrNull() ?: 0
            if (rMinor != lMinor) return rMinor > lMinor

            val rMonth = remoteMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val lMonth = localMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            if (rMonth != lMonth) return rMonth > lMonth

            val rDay = remoteMatch.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val lDay = localMatch.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            if (rDay != lDay) return rDay > lDay

            val rBuild = remoteMatch.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val lBuild = localMatch.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            return rBuild > lBuild
        }

        // Fallback: if either string does not conform to the pattern, treat distinct as newer
        return remoteTag != localTag
    }

    /**
     * Queries the GitHub Releases API for the latest release and checks if a newer APK is available.
     */
    suspend fun checkForUpdate(currentTag: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CalCloner-Update-Checker")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != 200) return@withContext null

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val tagName = json.optString("tag_name")
            val releaseUrl = json.optString("html_url")

            if (isNewerTag(tagName, currentTag)) {
                val assets = json.optJSONArray("assets") ?: return@withContext null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val downloadUrl = asset.optString("browser_download_url")
                        val size = asset.optLong("size", 0L)
                        return@withContext AppUpdateInfo(
                            tagName = tagName,
                            downloadUrl = downloadUrl,
                            apkName = name,
                            sizeBytes = size,
                            releaseUrl = releaseUrl
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Downloads the APK file from GitHub Releases into the app's cache directory and
     * launches the Android system package installer.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()
            val apkFile = File(updatesDir, updateInfo.apkName)
            if (apkFile.exists()) apkFile.delete()

            val url = URL(updateInfo.downloadUrl)
            var conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            // Follow HTTP redirects (GitHub asset downloads redirect to AWS S3)
            var redirectCount = 0
            while ((conn.responseCode == 301 || conn.responseCode == 302 || conn.responseCode == 303) && redirectCount < 5) {
                val newUrl = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = URL(newUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                redirectCount++
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: updateInfo.sizeBytes
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes.toFloat())
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
