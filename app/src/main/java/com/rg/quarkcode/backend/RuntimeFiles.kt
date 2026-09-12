package com.rg.quarkcode.backend

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Layout of the on-device backend inside app-private storage:
 *
 *   files/backend/guest        ← Debian slim rootfs (downloaded on opt-in)
 *   files/backend/workspace    ← agent working directory, bound as /workspace
 *   files/backend/proot-tmp    ← PROOT_TMP_DIR (via ProotSuite state)
 *
 * proot + loaders live in the APK's nativeLibraryDir (only exec-able place).
 * Official opencode installs to /root/.opencode/bin INSIDE the guest.
 * Only arm64 is supported for now (99% of phones).
 */
object RuntimeFiles {

    fun root(context: Context): File = File(context.filesDir, "backend")

    fun guest(context: Context): File = File(root(context), "guest")

    fun workspace(context: Context): File = File(root(context), "workspace")

    /** "arm64" when the device can run the Debian guest, else null. */
    fun supportedAbi(): String? =
        if (Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) "arm64" else null

    fun isPresent(context: Context): Boolean =
        DebianInstaller.isInstalled(context) && GuestOpencode.isInstalled(context)

    fun ensureDirs(context: Context) {
        workspace(context).mkdirs()
    }

    fun guestBytes(context: Context): Long = dirBytes(guest(context))

    fun totalBytes(context: Context): Long = dirBytes(root(context))

    private fun dirBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
