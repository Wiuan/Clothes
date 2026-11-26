package com.cloth.wardrobe.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class RemoteAppVersion(
    val versionCode: Int,
    val versionName: String,
    val notes: String = "",
    val apkUrl: String
)

sealed class UpdateCheckResult {
    data class UpToDate(val localName: String, val localCode: Int) : UpdateCheckResult()
    data class Available(
        val remote: RemoteAppVersion,
        val localName: String,
        val localCode: Int
    ) : UpdateCheckResult()

    data class Failed(val message: String) : UpdateCheckResult()
}

/**
 * 从 GitHub 公开仓库检查更新：
 * 1) raw `update/version.json`（versionCode / versionName / notes / apkUrl）
 * 2) 若 apkUrl 为空，再取 releases/latest 的首个 .apk
 */
class AppUpdateRepository(private val context: Context) {

    fun localVersionName(): String = try {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        p.versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    fun localVersionCode(): Int = try {
        val p = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt()
        else @Suppress("DEPRECATION") p.versionCode
    } catch (_: Exception) {
        0
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    fun intentOpenUnknownAppSettings(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val remote = fetchRemoteVersion()
            val localCode = localVersionCode()
            val localName = localVersionName()
            if (remote.versionCode > localCode) {
                UpdateCheckResult.Available(remote, localName, localCode)
            } else {
                UpdateCheckResult.UpToDate(localName, localCode)
            }
        }.getOrElse {
            UpdateCheckResult.Failed(it.message ?: "检查更新失败")
        }
    }

    suspend fun downloadApk(
        remote: RemoteAppVersion,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (remote.apkUrl.isBlank()) error("未配置 apkUrl，请在 update/version.json 填写下载地址")
            val dir = File(context.cacheDir, "apk").also { it.mkdirs() }
            val outFile = File(dir, "clothes-${remote.versionName}.apk")
            downloadToFile(remote.apkUrl, outFile, onProgress)
            validateApkFile(outFile)
            onProgress(1f)
            outFile
        }
    }

    fun installApk(file: File): Result<Unit> = runCatching {
        if (!file.exists()) error("安装包不存在")
        if (!canInstallPackages()) error("NEED_INSTALL_PERMISSION")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun fetchRemoteVersion(): RemoteAppVersion {
        val body = httpGet(VERSION_JSON_URL)
        val o = JSONObject(body)
        var apkUrl = o.optString("apkUrl", "").trim()
        val versionCode = o.getInt("versionCode")
        val versionName = o.optString("versionName", versionCode.toString())
        val notes = o.optString("notes", "")
        if (apkUrl.isBlank()) {
            apkUrl = fetchLatestReleaseApkUrl().orEmpty()
        }
        if (apkUrl.isBlank()) {
            error("version.json 未写 apkUrl，且 Releases 无 APK 资源")
        }
        return RemoteAppVersion(
            versionCode = versionCode,
            versionName = versionName,
            notes = notes,
            apkUrl = apkUrl
        )
    }

    /** 公开仓库：取 latest release 里第一个 .apk 的 browser_download_url */
    private fun fetchLatestReleaseApkUrl(): String? {
        return runCatching {
            val json = httpGet(RELEASES_LATEST_URL)
            val o = JSONObject(json)
            val assets = o.optJSONArray("assets") ?: return@runCatching null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return@runCatching a.optString("browser_download_url", "").ifBlank { null }
                }
            }
            null
        }.getOrNull()
    }

    private fun downloadToFile(url: String, outFile: File, onProgress: (Float) -> Unit) {
        val conn = openGet(url).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 120_000
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("下载失败 HTTP $code: ${err.take(160)}")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        if (total > 0) onProgress((readTotal.toFloat() / total).coerceIn(0f, 0.99f))
                    }
                    output.flush()
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun validateApkFile(file: File) {
        if (!file.exists() || file.length() < 10_000L) {
            error("下载文件过小，请确认 Release 已上传 APK")
        }
        file.inputStream().use { ins ->
            val header = ByteArray(4)
            if (ins.read(header) != 4) error("无法读取安装包")
            // ZIP/APK: PK\x03\x04
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                error("下载内容不是 APK，请检查 apkUrl")
            }
        }
    }

    private fun httpGet(url: String): String {
        val conn = openGet(url)
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("GitHub HTTP $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun openGet(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json, application/json, */*")
            setRequestProperty("User-Agent", "ClothesApp-UpdateChecker")
        }
    }

    companion object {
        const val GITHUB_OWNER = "Wiuan"
        const val GITHUB_REPO = "Clothes"
        /** 发版时更新此文件：versionCode / versionName / notes / apkUrl */
        val VERSION_JSON_URL =
            "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/update/version.json"
        val RELEASES_LATEST_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }
}
