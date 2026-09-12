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
    runtimeDir: File
) {
    data class Paths(
        val home: File,
        val tmp: File,
        val nativeLibDir: File,
        val proot: File,
        val loader: File,
        val loader32: File
    ) {
        fun baseEnv(): Map<String, String> = mapOf(
            "HOME" to home.absolutePath,
            "TMPDIR" to tmp.absolutePath,
            "LD_LIBRARY_PATH" to nativeLibDir.absolutePath,
            "PROOT_LOADER" to loader.absolutePath,
            "PROOT_LOADER_32" to loader32.absolutePath,
            "PROOT_TMP_DIR" to tmp.absolutePath
        )
    }

    fun ensureInstalled(): Paths {
        val stateRoot = File(runtimeDir, "proot-suite").apply { mkdirs() }
        val home = File(stateRoot, "home").apply { mkdirs() }
        val tmp = File(stateRoot, "tmp").apply { mkdirs() }
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
        return Paths(home, tmp, nativeLibDir, proot, loader, loader32)
    }

    companion object {
        const val PROOT_LIB = "libquark_proot.so"
        const val LOADER_LIB = "libquark_proot_loader.so"
        const val LOADER32_LIB = "libquark_proot_loader32.so"
    }
}
