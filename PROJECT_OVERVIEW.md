# Quark — native Android agent client

Purpose: AndCode-class coding-agent app, redesigned (not cloned), backed by
**native opencode** (`Hope2333/opencode-termux` bionic ELF running
`opencode web` on-device) with **Kai9000-style HTTP-only MCP**
(no stdio child processes).

## Backend
- Local native mode: `opencode web --port 4096 --hostname 127.0.0.1` served by
  the Termux `opencode` package (API >= 28), password via
  `OPENCODE_SERVER_PASSWORD`, app talks to `http://localhost:4096`.
- Remote mode: same API over LAN/Tailscale URL (user-provided).
- MCP: Streamable-HTTP `McpClient` (initialize → tools/list → call),
  in-process, ~KBs per server. No node/bun/uvx children.

## V1 scope — DONE (build green 2026-09-10, `rgprince/Quark`, debug APK 65M)
- [x] Connect screen (localhost default, Keystore password, test button)
- [x] Chat screen: message list, tool pulse card, todo card, Allow/Deny card
- [x] Top-right token ring button → Context stats sheet (bottom meter kept too)
- [x] Orbit composer + Model&Runtime sheet (Native/Server segments, favorites)
- [x] Spaces bottom-sheet (Agents/Projects/Recent) instead of AndCode drawer

Debug APK is 65M (unoptimized); release target stays <25M via R8/minify.

## V2 scope — connection fix + live server data (in progress)
- [x] Cleartext HTTP allowed (localhost/LAN), URL normalize, guided errors
- [x] Async send (`prompt_async` + status/message polling), Stop/Abort button
- [x] Live model catalog (`/config/providers`) in Model sheet
- [x] Recent sessions in Spaces + open with history
- [x] Session todos (`/session/:id/todo`), error cards with Retry
- [ ] SSE event stream (needs upstream event-shape check)
- [ ] Terminal + file browser, schedules/heartbeat, R8 release shrink

## Tech stack
Kotlin 2.3.0, AGP 9.3.2, compileSdk 37, minSdk 28 (native ELF needs API 28+),
Compose BOM 2025.09.01 + Material3, Navigation3, Retrofit + kotlinx.serialization,
DataStore, Coil. No KMP, no Electron, no PRoot, no WebView.

## Structure
- `app/src/main/java/com/quark/agent/` — `MainActivity`, `QuarkApp` (Nav3),
  `theme/`, `connect/`, `chat/`, `backend/`
- `Quarkcode/` (sibling, references only): opencode-termux, kai9000, aionui,
  opencode-android, opencode-ram-report.md

## Target size
Single `:app` module, debug APK < 25 MB (opencode-android class, not Kai class).
