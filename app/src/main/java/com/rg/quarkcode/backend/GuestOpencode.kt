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
 * (anomalyco/opencode releases, `opencode-linux-arm64.tar.gz`), extracted
 * on the HOST straight into the guest tree. No guest network needed for
 * this step and no middleman builds, so updates track upstream day-zero.
 *
 * NOTE: sst/opencode (and opencode-ai/opencode v0.0.55, Go, archived) are
 * dead ends — bare `serve` fails with "agent coder not found". Only
 * anomalyco/opencode (v1.x, Bun/TS) can be the backend.
 */
object GuestOpencode {

    const val STAGE_RESOLVE = "resolve"
    const val STAGE_DOWNLOAD = "download"
    const val STAGE_EXTRACT = "extract"
    const val STAGE_VERIFY = "verify"

    const val GUEST_BIN = "/root/.opencode/bin/opencode"

    // Pinned stable — NOT latest. Latest (1.18.30) moves under our feet and
    // re-downloads ~60 MB on every bump; 1.18.25 is the user-approved
    // stable. Bump PINNED_TAG deliberately, never silently.
    const val PINNED_TAG = "v1.18.25"

    private const val PINNED_URL =
        "https://api.github.com/repos/anomalyco/opencode/releases/tags/v1.18.25"

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
            .url(PINNED_URL)
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
        // anomalyco/opencode ships per-arch tarballs:
        // `opencode-linux-arm64.tar.gz` (glibc — matches our Debian guest).
        // The `-musl` variant is for Alpine/static libc, NOT Debian. A legacy
        // `.zip` name is kept as fallback so older cached assets still install.
        // Tolerant order: exact glibc tarball first, then any linux-arm64
        // tarball (e.g. musl if upstream renames), then legacy zip — so a
        // future asset rename surfaces as a working install, not a dead error.
        val match = release.assets.firstOrNull { asset ->
            asset.name == "opencode-linux-arm64.tar.gz" && asset.url.isNotBlank()
        } ?: release.assets.firstOrNull { asset ->
            asset.name.contains("linux-arm64", ignoreCase = true) &&
                asset.name.endsWith(".tar.gz", ignoreCase = true) &&
                asset.url.isNotBlank()
        } ?: release.assets.firstOrNull { asset ->
            asset.name == "opencode-linux-arm64.zip" && asset.url.isNotBlank()
        }
        if (match == null) {
            val names = release.assets.map { it.name }.take(8)
            LocalBackend.appendLog("resolve: opencode assets: ${names.joinToString(", ")}")
            error(
                "Pinned opencode $PINNED_TAG has no linux-arm64 build listed " +
                    "(found: ${names.joinToString(", ").ifBlank { "none" }})."
            )
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
        val tmp = File(RuntimeFiles.root(context), "opencode.pkg")
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
            // Wipe the old Go binary first so a stale v0.0.55 can never
            // shadow the new server-capable one at the same path.
            guestBinary(context).takeIf { it.isFile }?.delete()
            tmp.inputStream().buffered().use { input ->
                if (asset.fileName.endsWith(".zip", ignoreCase = true)) {
                    GuestArchive.extractZip(input, binDir) { files, _ ->
                        report(STAGE_EXTRACT, -1f, "$files files…")
                    }
                } else {
                    GuestArchive.extractTarGz(input, binDir) { files, _ ->
                        report(STAGE_EXTRACT, -1f, "$files files…")
                    }
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
            // PATH + xdg-open self-heal: login shells reset PATH to Debian
            // defaults (no /root/.opencode/bin), so link the binary into
            // /usr/local/bin; also drop a no-op xdg-open so nothing can
            // kill the server with a spawn ENOENT.
            runCatching { DebianInstaller.ensureGuestShims(RuntimeFiles.guest(context)) }
            report(STAGE_VERIFY, 1f, "v${asset.version} ready")
        }
    }

    private fun formatMb(value: Long): String = "%.0f MB".format(value / 1_000_000.0)
}
