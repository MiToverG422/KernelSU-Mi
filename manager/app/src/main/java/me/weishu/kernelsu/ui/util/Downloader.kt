package me.weishu.kernelsu.ui.util

import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import okhttp3.Request
import org.json.JSONArray

private const val UPDATE_RELEASE_API = "https://api.github.com/repos/MiToverG422/KernelSU-Mi/releases?per_page=30"
private const val UPDATE_APK_PREFIX = "MISU_"
private val updateApkVersionRegex = Regex("""_(\d+)(?:[-.]|$)""")

/**
 * @author weishu
 * @date 2023/6/22.
 */
suspend fun download(
    url: String,
    fileName: String,
    onDownloaded: (Uri) -> Unit = {},
    onDownloading: () -> Unit = {},
    onProgress: (Int) -> Unit = {}
) {
    onDownloading()

    val downloadId = DownloadManager.enqueue(
        context = ksuApp,
        url = url,
        fileName = fileName,
        onCompleted = onDownloaded,
    )

    DownloadManager.downloads
        .onEach { map -> map[downloadId]?.let { onProgress(it.progress) } }
        .first { map ->
            val status = map[downloadId]?.status
            status == DownloadManager.Status.COMPLETED ||
                status == DownloadManager.Status.FAILED
        }
}

fun checkNewVersion(): LatestVersionInfo {
    if (!isNetworkAvailable(ksuApp)) return LatestVersionInfo()
    // default null value if failed
    val defaultValue = LatestVersionInfo()
    runCatching {
        ksuApp.okhttpClient.newCall(Request.Builder().url(UPDATE_RELEASE_API).build()).execute()
            .use { response ->
                if (!response.isSuccessful) {
                    return defaultValue
                }
                val body = response.body.string()
                val releases = JSONArray(body)
                var latestApk = defaultValue

                for (releaseIndex in 0 until releases.length()) {
                    val release = releases.getJSONObject(releaseIndex)
                    if (release.optBoolean("draft")) {
                        continue
                    }

                    val changelog = release.optString("body")
                    val assets = release.getJSONArray("assets")
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.getJSONObject(assetIndex)
                        val name = asset.getString("name")
                        if (!name.startsWith(UPDATE_APK_PREFIX) || !name.endsWith(".apk")) {
                            continue
                        }

                        val matchResult = updateApkVersionRegex.find(name) ?: continue
                        val versionCode = matchResult.groupValues[1].toIntOrNull() ?: continue
                        val downloadUrl = asset.getString("browser_download_url")

                        if (versionCode <= latestApk.versionCode) {
                            continue
                        }

                        latestApk = LatestVersionInfo(
                            versionCode,
                            downloadUrl,
                            changelog
                        )
                    }
                }

                return latestApk
            }
    }
    return defaultValue
}
