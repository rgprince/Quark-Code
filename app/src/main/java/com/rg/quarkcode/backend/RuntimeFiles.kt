package com.rg.quarkcode.backend

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Layout of the on-device backend inside app-private storage:
 *
 *   files/backend/opencode     ← downloaded native Bionic ELF (Hope2333 line)
 *   files/backend/home         ← HOME for the server (config, opencode.db)
 *   files/backend/workspace    ← agent working directory (import/export via picker later)
 *
 * Execution route: W^X forbids exec() of files in the writable app home on
 * Android 10+, so the ELF is launched through the system linker
 * (/system/bin/linker64), which may map+run app-data ELFs. No proot, no
 * glibc, no root. Only arm64 is supported for now (99% of phones).
 */
object RuntimeFiles {

    fun root(context: Context): File = File(context.filesDir, "backend")

    fun elf(context: Context): File = File(root(context), "opencode")

    fun home(context: Context): File = File(root(context), "home")

    fun workspace(context: Context): File = File(root(context), "workspace")

    /** "arm64" when the device can run the native line, else null. */
    fun supportedAbi(): String? =
        if (Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) "arm64" else null

    fun linker(): String = "/system/bin/linker64"

    fun isPresent(context: Context): Boolean {
        val f = elf(context)
        return f.isFile && f.length() > 10_000_000L
    }

    fun ensureDirs(context: Context) {
        home(context).mkdirs()
        workspace(context).mkdirs()
        File(home(context), "tmp").mkdirs()
    }

    fun totalBytes(context: Context): Long {
        val r = root(context)
        if (!r.exists()) return 0L
        return r.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
