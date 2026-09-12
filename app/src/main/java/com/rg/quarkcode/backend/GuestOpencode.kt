package com.rg.quarkcode.backend

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class OcAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0L
)

@Serializable
private data class OcRelease(
    @SerialName("tag_name") val tag: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<OcAsset> = emptyList()
)

@Serializable
data class OpencodeAsset(
    val version: String,
    val fileName: String,
    val url: String,
    val size: Long
)

/**
 * Official opencode installer — untouched upstream bits
 * (opencode-ai/opencode releases, `opencode-linux-arm64.tar.gz`), extracted
 * on the HOST straight into the guest tree. No guest network needed for
 * this step and no middleman builds, so updates track upstream day-zero.
 */
object GuestOpencode {

    const val STAGE_RESOLVE = "resolve"
    const val STAGE_DOWNLOAD = "download"
    const val STAGE_EXTRACT = "extract"
    const val STAGE_VERIFY = "verify"

    const val GUEST_BIN = "/root/.opencode/bin/opencode"

    private const val LATEST_URL =
        "https://api.github.com/repos/opencode-ai/opencode/releases/latest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun guestBinary(context: Context): File =
        File(RuntimeFiles.guest(context), GUEST_BIN.removePrefix("/"))

    fun isInstalled(context: Context): Boolean {
        val f = guestBinary(context)
        return f.isFile && f.length() > 10_000_000L
    }

    /** Throws with a human-readable reason — the UI shows it verbatim. */
    suspend fun resolve(): OpencodeAsset = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("User-Agent", "Quark-Code")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val text = http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val limited = response.code == 403 && body.contains("rate limit", ignoreCase = true)
                error(
                    if (limited) "GitHub rate-limited this network — wait a few minutes and retry."
                    else "opencode release lookup failed (HTTP ${response.code})."
                )
            }
            if (body.isBlank()) error("opencode release lookup returned nothing.")
            body
        }
        val release = runCatching { json.decodeFromString<OcRelease>(text) }.getOrNull()
            ?: error("opencode release list was unreadable.")
        // Official install script uses opencode-linux-<arch>.tar.gz.
        val match = release.assets.firstOrNull { asset ->
            asset.name == "opencode-linux-arm64.tar.gz" && asset.url.isNotBlank()
        }
        if (match == null) {
            val names = release.assets.map { it.name }.take(10)
            LocalBackend.appendLog("resolve: opencode assets: ${names.joinToString(", ")}")
            error("Latest opencode has no linux-arm64 build listed.")
        }
        OpencodeAsset(
            version = release.tag.trim().removePrefix("v").ifBlank { "unknown" },
            fileName = match.name,
            url = match.url,
            size = match.size
        )
    }

    suspend fun install(
        context: Context,
        asset: OpencodeAsset,
        report: suspend (stage: String, fraction: Float, detail: String) -> Unit
    ) {
        report(STAGE_RESOLVE, 1f, asset.fileName)
        val tmp = File(RuntimeFiles.root(context), "opencode.tgz")
        withContext(Dispatchers.IO) {
            HttpDownload.get(this, asset.url, tmp, asset.size) { done, total ->
                val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                val resumed = if (done > 0 && total > 0 && done < total) " (resumed)" else ""
                report(STAGE_DOWNLOAD, fraction, "${formatMb(done)}" +
                    (if (total > 0) " / ${formatMb(total)}" else "") + resumed)
            }
        }
        withContext(Dispatchers.IO) {
            val binDir = guestBinary(context).parentFile!!
            binDir.mkdirs()
            tmp.inputStream().buffered().use { input ->
                GuestArchive.extractTarGz(input, binDir) { files, _ ->
                    report(STAGE_EXTRACT, -1f, "$files files…")
                }
            }
            tmp.delete()
            val bin = guestBinary(context)
            if (!bin.isFile) {
                // Upstream sometimes nests the binary one level deep.
                val nested = binDir.walkTopDown().firstOrNull {
                    it.isFile && it.name == "opencode" && it.length() > 10_000_000L
                } ?: error("opencode binary missing from ${asset.fileName}")
                nested.copyTo(bin, overwrite = true)
            }
            bin.setExecutable(true, false)
            report(STAGE_VERIFY, 1f, "v${asset.version} ready")
        }
    }

    private fun formatMb(value: Long): String = "%.0f MB".format(value / 1_000_000.0)
}
