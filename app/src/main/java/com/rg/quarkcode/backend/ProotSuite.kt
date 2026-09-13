package com.rg.quarkcode.backend

import android.content.Context
import java.io.File

/**
 * The APK-embedded proot launcher suite (fetched at build time into jniLibs
 * by scripts/fetch_proot_assets.py). nativeLibraryDir is the ONLY location
 * Android grants exec rights, so proot + loaders must come from the APK —
 * everything else (Debian, opencode) downloads at runtime on opt-in.
 */
class ProotSuite(
    private val context: Context,
    private val runtimeDir: File
) {
    data class Paths(
        val home: File,
        val tmp: File,
        val nativeLibDir: File,
        val libsDir: File,
        val proot: File,
        val loader: File,
        val loader32: File
    ) {
        fun baseEnv(): Map<String, String> = mapOf(
            "HOME" to home.absolutePath,
            "TMPDIR" to tmp.absolutePath,
            // libsDir FIRST: it holds the SONAME-exact names (libtalloc.so.2)
            // that AGP strips from jniLibs (*.so.2 is not packaged — only
            // the unversioned *.so copy survives in the APK). nativeLibDir
            // stays second so the exec-able proot + loaders still resolve.
            "LD_LIBRARY_PATH" to "${libsDir.absolutePath}:${nativeLibDir.absolutePath}",
            "PROOT_LOADER" to loader.absolutePath,
            "PROOT_LOADER_32" to loader32.absolutePath,
            "PROOT_TMP_DIR" to tmp.absolutePath
        )
    }

    fun ensureInstalled(): Paths {
        val stateRoot = File(runtimeDir, "proot-suite").apply { mkdirs() }
        val home = File(stateRoot, "home").apply { mkdirs() }
        val tmp = File(stateRoot, "tmp").apply { mkdirs() }
        val libsDir = File(stateRoot, "libs").apply { mkdirs() }
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val proot = File(nativeLibDir, PROOT_LIB)
        val loader = File(nativeLibDir, LOADER_LIB)
        val loader32 = File(nativeLibDir, LOADER32_LIB)
        require(proot.isFile && proot.canExecute()) {
            "Embedded proot missing for this ABI — reinstall the app"
        }
        require(loader.isFile && loader.canExecute()) {
            "Embedded proot loader missing for this ABI — reinstall the app"
        }
        require(loader32.isFile && loader32.canExecute()) {
            "Embedded proot 32-bit loader missing — reinstall the app"
        }
        // Self-heal the support libs AGP drops: materialize SONAME-exact
        // names into app-private libsDir from the unversioned *.so copies
        // that DO survive APK packaging (same bytes, see
        // scripts/fetch_proot_assets.py). The linker loads them via
        // LD_LIBRARY_PATH — they only need read access, not exec.
        materializeSupportLibs(nativeLibDir, libsDir)
        return Paths(home, tmp, nativeLibDir, libsDir, proot, loader, loader32)
    }

    private fun materializeSupportLibs(nativeLibDir: File, libsDir: File) {
        // (unversioned APK name, SONAME the proot binary asks for)
        val pairs = listOf(
            "libtalloc.so" to "libtalloc.so.2",
            "libandroid-shmem.so" to "libandroid-shmem.so"
        )
        // Mirror every talloc/shmem file that made it into the APK so
        // future SONAMEs keep working without a code change.
        nativeLibDir.listFiles { f ->
            f.isFile && (f.name.startsWith("libtalloc") || f.name.startsWith("libandroid-shmem"))
        }?.forEach { src ->
            copyIfStale(src, File(libsDir, src.name))
        }
        for ((unversioned, versioned) in pairs) {
            val src = File(nativeLibDir, unversioned)
            val versionedInApk = File(nativeLibDir, versioned)
            if (versionedInApk.isFile) {
                copyIfStale(versionedInApk, File(libsDir, versioned))
            } else if (src.isFile) {
                // Same bytes — the fetch script writes both names from one
                // download, so copying .so -> .so.2 is exact.
                copyIfStale(src, File(libsDir, versioned))
            }
        }
        val tallocReady = File(libsDir, "libtalloc.so.2").isFile ||
            File(nativeLibDir, "libtalloc.so.2").isFile
        require(tallocReady || File(nativeLibDir, "libtalloc.so").isFile) {
            "Embedded proot support lib missing (libtalloc) — reinstall the app"
        }
    }

    private fun copyIfStale(src: File, dst: File) {
        if (dst.isFile && dst.length() == src.length()) return
        src.inputStream().use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        runCatching { dst.setReadable(true, false) }
        runCatching { Runtime.getRuntime().exec(arrayOf("chmod", "755", dst.absolutePath)).waitFor() }
    }

    companion object {
        const val PROOT_LIB = "libquark_proot.so"
        const val LOADER_LIB = "libquark_proot_loader.so"
        const val LOADER32_LIB = "libquark_proot_loader32.so"
    }
}
