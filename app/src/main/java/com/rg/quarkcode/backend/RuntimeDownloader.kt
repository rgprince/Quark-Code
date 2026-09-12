package com.rg.quarkcode.backend

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0L
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tag: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList()
)

data class RuntimeAsset(
    val version: String,
    val fileName: String,
    val url: String,
    val size: Long
)

/**
 * Resolves + downloads the native Bionic opencode ELF from the
 * Hope2333/opencode-termux releases (single zero-glibc ELF, API 28+).
 * Nothing is hardcoded: the newest release carrying an
 * `opencode-*-aarch64-android-native` asset wins, so new upstream versions
 * flow in without an app update.
 */
object RuntimeDownloader {

    private const val RELEASES_URL =
        "https://api.github.com/repos/Hope2333/opencode-termux/releases?per_page=10"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolveLatest(): RuntimeAsset? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("User-Agent", "Quark-Code")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val text = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string() ?: return null
        }
        val releases = runCatching { json.decodeFromString<List<GithubRelease>>(text) }
            .getOrNull() ?: return null
        for (release in releases) {
            if (release.draft) continue
            val match = release.assets.firstOrNull { asset ->
                asset.name.startsWith("opencode-") &&
                    asset.name.contains("aarch64-android-native") &&
                    !asset.name.endsWith(".deb") &&
                    !asset.name.endsWith(".xz") &&
                    asset.url.isNotBlank()
            } ?: continue
            val version = release.tag.trim().removePrefix("v")
                .ifBlank { match.name.substringAfter("opencode-").substringBefore("-aarch64") }
            return RuntimeAsset(
                version = version.ifBlank { "unknown" },
                fileName = match.name,
                url = match.url,
                size = match.size
            )
        }
        return null
    }

    /**
     * Streams [asset] to [dest] (via .tmp + rename), chmod 700, progress
     * callback on the calling context. Returns the version on success.
     */
    suspend fun download(
        asset: RuntimeAsset,
        dest: File,
        onProgress: suspend (bytes: Long, total: Long) -> Unit
    ): String {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        val request = Request.Builder()
            .url(asset.url)
            .header("User-Agent", "Quark-Code")
            .header("Accept", "application/octet-stream")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (HTTP ${response.code})")
            val body = response.body ?: error("Empty download body")
            val total = body.contentLength().takeIf { it > 0 } ?: asset.size.takeIf { it > 0 } ?: -1L
            tmp.parentFile?.mkdirs()
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                    output.flush()
                }
            }
        }
        if (tmp.length() < 10_000_000L) {
            tmp.delete()
            error("Downloaded file too small (${tmp.length()} bytes) — likely an error page")
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) error("Could not install runtime file")
        dest.setExecutable(true, true)
        dest.setReadable(true, true)
        return asset.version
    }
}
