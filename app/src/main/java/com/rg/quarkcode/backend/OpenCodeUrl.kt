package com.rg.quarkcode.backend

// URL rules: prepend http, trailing slash,
// http allowed only for loopback / LAN / Tailscale / ULA, https anywhere.
object OpenCodeUrl {

    fun normalize(raw: String): String {
        var value = raw.trim().trimEnd('/')
        if (value.isEmpty()) error("Empty server URL")
        if (!value.contains("://")) value = "http://$value"
        val lower = value.lowercase()
        val afterScheme = lower.substringAfter("://")
        val host = afterScheme.substringBefore(':').substringBefore('/')
        val secure = lower.startsWith("https://")
        if (!secure && !isLocalHost(host)) {
            error("Plain http is allowed only for local networks; use https or a local address.")
        }
        return "$value/"
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().removeSuffix(".").removeSuffix(".local")
        if (h == "localhost" || h == "::1") return true
        if (h.startsWith("127.")) return true
        if (h.startsWith("10.")) return true
        if (h.startsWith("192.168.")) return true
        if (h.startsWith("169.254.")) return true
        if (h.startsWith("fc") || h.startsWith("fd")) return true
        if (h.startsWith("172.")) {
            val second = h.split(".").getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        if (h.startsWith("100.")) {
            val second = h.split(".").getOrNull(1)?.toIntOrNull()
            if (second != null && second in 64..127) return true
        }
        return false
    }
}
