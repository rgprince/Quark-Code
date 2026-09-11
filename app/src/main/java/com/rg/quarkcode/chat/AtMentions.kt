package com.rg.quarkcode.chat

// @ file-mention helpers (aionui IA, Quark tokens).
// The server resolves literal `@path` tokens natively, so insertion is
// text-only: `@relative/path` + trailing space (the space lets a second `@` parse).
object AtMentions {

    private val boundary = setOf(' ', '\n', '\t', ',', ';', '!', '?', '(', ')', '[', ']', '{', '}')

    /** Active @query (text after the last boundary @), or null. Skips @@ session lane. */
    fun detectQuery(text: String): String? {
        var i = text.length - 1
        while (i >= 0) {
            val c = text[i]
            if (c == ' ' || c == '\n' || c == '\t') return null
            if (c == '@') {
                if (i > 0 && text[i - 1] == '@') return null
                if (i > 0 && !boundary.contains(text[i - 1])) return null
                return text.substring(i + 1)
            }
            i--
        }
        return null
    }

    /** Start index of the active @token, or -1. */
    fun tokenStart(text: String): Int {
        var i = text.length - 1
        while (i >= 0) {
            val c = text[i]
            if (c == ' ' || c == '\n' || c == '\t') return -1
            if (c == '@') {
                if (i > 0 && text[i - 1] == '@') return -1
                if (i > 0 && !boundary.contains(text[i - 1])) return -1
                return i
            }
            i--
        }
        return -1
    }

    fun insert(text: String, path: String): String {
        val start = tokenStart(text) ?: return text
        var end = start + 1
        while (end < text.length && text[end] != ' ' && text[end] != '\n' && text[end] != '\t') end++
        return text.substring(0, start) + "@" + path + " " + text.substring(end).trimStart()
    }

    /** Client-side rank over GET find/file paths (aionui tier table, simplified). */
    fun rank(paths: List<String>, query: String): List<AtFile> {
        val q = query.lowercase()
        return paths.mapNotNull { path ->
            val name = path.substringAfterLast('/').lowercase()
            val score = when {
                q.isEmpty() -> 50
                name == q -> 400
                name.startsWith(q) -> 300
                name.contains(q) -> 200
                path.lowercase().contains(q) -> 100
                else -> return@mapNotNull null
            }
            score to AtFile(path, path.substringAfterLast('/'))
        }.sortedByDescending { it.first }.take(8).map { it.second }
    }

    /** Strip markdown/code for TTS readout (kai9000 speakable-text idea). */
    fun speakable(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), " code block ")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("[*_~>|#]"), "")
        .replace(Regex("\\[([^\\]]*)\\]\\([^\\)]*\\)"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
}
