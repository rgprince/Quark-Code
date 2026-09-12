package com.rg.quarkcode.backend

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

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

    suspend fun resolve(): OpencodeAsset? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("User-Agent", "Quark-Code")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val text = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string() ?: return@withContext null
        }
        val release = runCatching { json.decodeFromString<OcRelease>(text) }.getOrNull()
            ?: return@withContext null
        // Official install script uses opencode-linux-<arch>.tar.gz.
        val match = release.assets.firstOrNull { asset ->
            asset.name == "opencode-linux-arm64.tar.gz" && asset.url.isNotBlank()
        } ?: return@withContext null
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
            val scope = this
            val request = Request.Builder()
                .url(asset.url)
                .header("User-Agent", "Quark-Code")
                .header("Accept", "application/octet-stream")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("opencode download failed (HTTP ${response.code})")
                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 } ?: asset.size.takeIf { it > 0 } ?: -1L
                tmp.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            scope.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                            report(STAGE_DOWNLOAD, fraction, "${formatMb(done)}" +
                                (if (total > 0) " / ${formatMb(total)}" else ""))
                        }
                        output.flush()
                    }
                }
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
