# Quark Code — native Android agent client

Purpose: AndCode-class coding-agent app, redesigned (not cloned), backed by
**native opencode** (`Hope2333/opencode-termux` bionic ELF running
`opencode web` on-device) with **Kai9000-style HTTP-only MCP**
(no stdio child processes).

Backend: AndCode-ported client (URL rules, `GET /provider` catalog with
`/config/providers` fallback, stored selection + reconcile, checked
`prompt_async`, `global/event` SSE with `/event` fallback, archived filter).

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

## V3 scope — faithful backend + drawer/settings (DONE, verified green per push)
- [x] Rename to Quark Code, package `com.rg.quarkcode` (clean install)
- [x] OkHttp-direct Serve client (real server error text), typed SSE events
- [x] Parts chat: thinking/tool/patch/image/error/question cards, deltas, merge
- [x] Stored model pick + reconcile, checked async send, transcript completion
- [x] ModalNavigationDrawer (hamburger): New chat, Agents, Projects, Recents+delete, Schedules, Settings
- [x] Settings: connection test/save, theme, HTTP MCP add/toggle, about
- [x] Schedules: DataStore CRUD + WorkManager reminder notifications
- [x] Composer: model pill full row, meter/cost strip, stable token layouts

## V4 UI batch — AndCode IA, Quark tokens (batched, single push to save CI)
- [x] Timeline grouping (`Timeline.kt`): Reasoning+Tool collapse to one Activity, blank reasoning skipped, todowrite→Todo, Text/Image/Error flush
- [x] Bubbles: user `primary/onPrimary max 340.dp 20/20/5/20dp + HH:mm`, assistant plain selectable body (no bubble)
- [x] `AssistantActivityRow` + bottom-sheet + `ReasoningCard` + `QuarkToolCard` (category icons, `ToolStatusChip`, monospace, 240dp output cap) + warning `PermissionCard` (Allow once / Always / Deny)
- [x] Model picker QoL: shows only chosen provider by default + `Show all` escape + `Change provider in Settings` row
- [x] Settings redesign: Default provider radio (persisted), Connection, segmented Appearance, MCP Switch rows, About; `loadAll()` entry
- [x] Theme: full Void/Paper containers (`primaryContainer`, `tertiary`, `errorContainer`, `surfaceContainer*`, `outline`) — fixes purple fallback clash

## V4.1 batch — provider sync, cost, slash, modes, glow, settings extras
- [x] Provider fix: Settings reads SAVED connection (race fixed), reloads after Test&save, `ChatViewModel.refreshSelection()` on Settings back
- [x] Cost: composer strip removed (meter only); Context sheet `Free` when 0 else `$%.2f`
- [x] Thinking moved to transcript tail (`ThinkingTail` pulse); stray row above composer removed; `animateItem` + `animateContentSize` + smart auto-scroll
- [x] Slash `/` popup (app: /new /model /agent /help + `GET command`/`GET skill`), `POST session/{id}/command`; no `@` (AndCode never mapped it, no server endpoint)
- [x] Modes: `GET agent` chip (build/plan first) + variant ThinkingChip from `model.variants`; sent as `agent`+`variant` in `prompt_async`
- [x] Glow light: pulsing primary bar below editor while sending
- [x] Settings extras: Chat toggles (auto-expand reasoning, detailed tools, shared `ChatPrefs` store), server version, copy-diagnostics

## Later (V4+)
- Changes/review tab, fork/revert, slash autocomplete, terminal + file browser
- R8 release shrink (debug APK 65M), F-Droid flavor

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
