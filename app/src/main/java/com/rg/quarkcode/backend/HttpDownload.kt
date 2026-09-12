package com.rg.quarkcode.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Resumable file downloads. Partial `.tmp` files survive failed attempts and
 * retries continue from the last byte via HTTP Range — a 200 MB rootfs that
 * dies at 90% restarts at 90%, never at zero. Callers keep the tmp file
 * until the following stage (extract) succeeds, so every step is re-runnable.
 */
object HttpDownload {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun get(
        scope: CoroutineScope,
        url: String,
        tmp: File,
        expectedTotal: Long,
        report: suspend (done: Long, total: Long) -> Unit
    ): Long {
        tmp.parentFile?.mkdirs()
        val existing = if (tmp.isFile) tmp.length() else 0L
        // Already complete from an earlier attempt — skip the network entirely.
        if (expectedTotal > 0 && existing >= expectedTotal) {
            report(existing, expectedTotal)
            return existing
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Quark-Code")
            .header("Accept", "application/octet-stream")
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // Server says the range is past EOF: our tmp is at least
                // complete (or the file shrank upstream — restart then).
                if (expectedTotal > 0 && existing >= expectedTotal) {
                    report(existing, expectedTotal)
                    return existing
                }
                tmp.delete()
                return get(scope, url, tmp, expectedTotal, report)
            }
            if (!response.isSuccessful) error("Download failed (HTTP ${response.code})")
            val body = response.body ?: error("Empty download body")
            val append = response.code == 206 && existing > 0
            val remaining = body.contentLength()
            val total = if (append && remaining > 0) {
                existing + remaining
            } else {
                remaining.takeIf { it > 0 } ?: expectedTotal.takeIf { it > 0 } ?: -1L
            }
            if (!append && existing > 0) tmp.delete()
            body.byteStream().use { input ->
                RandomAccessFile(tmp, "rw").use { raf ->
                    if (append) raf.seek(existing)
                    val buf = ByteArray(256 * 1024)
                    var done = if (append) existing else 0L
                    report(done, total)
                    while (true) {
                        scope.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        report(done, total)
                    }
                }
            }
            val final = tmp.length()
            if (expectedTotal > 0 && final < expectedTotal) {
                error("Download stopped short (${formatMb(final)} of ${formatMb(expectedTotal)}) — retry resumes it")
            }
            return final
        }
    }

    private fun formatMb(value: Long): String = "%.0f MB".format(value / 1_000_000.0)
}
