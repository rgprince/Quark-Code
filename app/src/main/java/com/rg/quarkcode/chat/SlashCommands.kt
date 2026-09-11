package com.rg.quarkcode.chat

import com.rg.quarkcode.backend.OpenCodeCommand
import com.rg.quarkcode.backend.OpenCodeSkill

// Slash popup registry (AndCode IA, Quark-local app commands).
// No @ mentions: AndCode never mapped them, server defines no @ endpoint.
object SlashCommands {

    data class AppCommand(val name: String, val description: String)

    val app: List<AppCommand> = listOf(
        AppCommand("/new", "Start a new chat"),
        AppCommand("/model", "Open model picker"),
        AppCommand("/agent", "Open agents & spaces"),
        AppCommand("/help", "Show available commands")
    )

    fun suggestions(
        query: String,
        backendCommands: List<OpenCodeCommand>,
        backendSkills: List<OpenCodeSkill>
    ): List<SlashSuggestion> {
        val trimmed = query.trim()
        fun matches(name: String): Boolean =
            trimmed.isEmpty() || name.startsWith(trimmed, ignoreCase = true)
        val appSuggestions = app
            .filter { matches(it.name) }
            .map { SlashSuggestion(it.name, it.description, isApp = true) }
        val backend = backendCommands
            .filter { matches("/${it.name}") }
            .map { SlashSuggestion("/${it.name}", it.description.orEmpty()) } +
            backendSkills
                .filter { matches("/${it.name}") }
                .map { SlashSuggestion("/${it.name}", it.description.orEmpty(), isSkill = true) }
        return appSuggestions + backend.sortedBy { it.name }
    }

    /** Backend-known `/name` on the first line, or null (app commands handled separately). */
    fun matchBackend(
        text: String,
        backendCommands: List<OpenCodeCommand>,
        backendSkills: List<OpenCodeSkill>
    ): Pair<String, String>? {
        if (!text.startsWith("/")) return null
        val firstLineEnd = text.indexOf('\n')
        val firstLine = if (firstLineEnd == -1) text else text.substring(0, firstLineEnd)
        val tokens = firstLine.split(" ")
        val raw = tokens.firstOrNull()?.trimStart('/')?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val restOfLine = tokens.drop(1).joinToString(" ")
        val rest = if (firstLineEnd == -1) "" else text.substring(firstLineEnd + 1)
        val args = listOf(restOfLine, rest).filter { it.isNotEmpty() }.joinToString("\n")
        val known = backendCommands.any { it.name == raw } || backendSkills.any { it.name == raw }
        return if (known) raw to args else null
    }
}
