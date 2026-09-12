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
import kotlin.coroutines.coroutineContext

@Serializable
private data class DistroAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0L
)

@Serializable
private data class DistroRelease(
    @SerialName("tag_name") val tag: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<DistroAsset> = emptyList()
)

data class DebianAsset(
    val version: String,
    val fileName: String,
    val url: String,
    val size: Long
)

/**
 * Debian slim guest installer. Source: proot-distro's versioned
 * `debian-aarch64-pd-*.tar.xz` rootfs tarballs (same recipe Termux uses).
 * Stages are reported out so the UI can show exactly how far it got:
 * resolve → download → extract → configure.
 */
object DebianInstaller {

    const val STAGE_RESOLVE = "resolve"
    const val STAGE_DOWNLOAD = "download"
    const val STAGE_EXTRACT = "extract"
    const val STAGE_CONFIGURE = "configure"

    const val MARKER_NAME = ".installed"

    private const val RELEASES_URL =
        "https://api.github.com/repos/termux/proot-distro/releases?per_page=10"
    private const val RELEASES_FALLBACK_URL =
        "https://api.github.com/repos/theworkjoy/proot-distro/releases?per_page=10"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun installedVersion(context: Context): String? {
        val marker = File(RuntimeFiles.guest(context), MARKER_NAME)
        if (!marker.isFile) return null
        return marker.readText().trim().takeIf { it.isNotEmpty() }
    }

    fun isInstalled(context: Context): Boolean =
        installedVersion(context) != null &&
            File(RuntimeFiles.guest(context), "etc/debian_version").isFile

    suspend fun resolve(): DebianAsset? = withContext(Dispatchers.IO) {
        resolveFrom(RELEASES_URL) ?: resolveFrom(RELEASES_FALLBACK_URL)
    }

    private fun resolveFrom(url: String): DebianAsset? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Quark-Code")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val text = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string() ?: return null
        }
        val releases = runCatching { json.decodeFromString<List<DistroRelease>>(text) }
            .getOrNull() ?: return null
        for (release in releases) {
            if (release.draft) continue
            val match = release.assets.firstOrNull { asset ->
                asset.name.startsWith("debian-aarch64-pd-") &&
                    asset.name.endsWith(".tar.xz") &&
                    asset.url.isNotBlank()
            } ?: continue
            return DebianAsset(
                version = match.name
                    .removePrefix("debian-aarch64-pd-")
                    .removeSuffix(".tar.xz"),
                fileName = match.name,
                url = match.url,
                size = match.size
            )
        }
        return null
    }

    suspend fun install(
        context: Context,
        asset: DebianAsset,
        report: suspend (stage: String, fraction: Float, detail: String) -> Unit
    ) {
        val guest = RuntimeFiles.guest(context)
        report(STAGE_RESOLVE, 1f, asset.fileName)
        // Download (fraction by bytes).
        val tmp = File(RuntimeFiles.root(context), "debian.tmp")
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(asset.url)
                .header("User-Agent", "Quark-Code")
                .header("Accept", "application/octet-stream")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Debian download failed (HTTP ${response.code})")
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
                            val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                            report(STAGE_DOWNLOAD, fraction, "${formatMb(done)}" +
                                (if (total > 0) " / ${formatMb(total)}" else ""))
                        }
                        output.flush()
                    }
                }
            }
        }
        // Extract (fraction by files every 500 + bytes detail).
        guest.mkdirs()
        withContext(Dispatchers.IO) {
            tmp.inputStream().buffered().use { input ->
                GuestArchive.extractTarXz(input, guest) { files, _ ->
                    report(STAGE_EXTRACT, -1f, "$files files…")
                }
            }
        }
        tmp.delete()
        // Configure: resolv.conf, CA bundle from system, workspace mount dir.
        withContext(Dispatchers.IO) {
            report(STAGE_CONFIGURE, 0.3f, "network config…")
            File(guest, "etc/resolv.conf").apply {
                parentFile?.mkdirs()
                writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }
            report(STAGE_CONFIGURE, 0.6f, "certificates…")
            copySystemCaBundle(guest)
            File(guest, "workspace").mkdirs()
            File(guest, "root/.opencode").mkdirs()
            File(guest, MARKER_NAME).writeText(asset.version)
            report(STAGE_CONFIGURE, 1f, "done")
        }
    }

    private fun copySystemCaBundle(guest: File) {
        // apt/curl inside the guest need CA certs; seed the bundle from the
        // Android system store when the rootfs ships none.
        val dest = File(guest, "etc/ssl/certs/ca-certificates.crt")
        if (dest.isFile && dest.length() > 50_000L) return
        val systemDir = File("/system/etc/security/cacerts")
        val certs = systemDir.listFiles { f -> f.isFile }?.sortedBy { it.name } ?: return
        if (certs.isEmpty()) return
        runCatching {
            dest.parentFile?.mkdirs()
            dest.outputStream().bufferedWriter().use { out ->
                certs.forEach { cert ->
                    out.write(cert.readText())
                    out.write("\n")
                }
            }
        }
    }

    private fun formatMb(value: Long): String = "%.0f MB".format(value / 1_000_000.0)
}
