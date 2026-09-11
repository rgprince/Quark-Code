# Quark Code — Session Handoff Report

Date: 2026-09-10 · Repo: `rgprince/Quark` (private) · Package: `com.rg.quarkcode`
Display name: **Quark Code** · Last verified build: ✅ green (Actions, `quark-debug-apk`, ~67M debug)

## 1. What was built

**Backend (ported from `and-code`, OkHttp-direct, no Retrofit)**
- `backend/ServeApi.kt` — GET/POST/PATCH/PUT/DELETE + `postUnit`, basic auth,
  path encoding, 15s/120s/30s timeouts, `encodeDefaults` JSON. Every failure
  throws the server's own error text (first 3 lines, 240 chars) — no more bare numbers.
- `backend/ServeEvents.kt` — typed SSE: `global/event` primary, `/event`
  fallback (400/404/405/501), backoff retry, envelope-vs-direct parse.
  Handles deltas, part updates, `permission.asked`, `question.asked`,
  `session.idle/error/status`, created/updated.
- `backend/OpenCodeUrl.kt` — http only for loopback/LAN/Tailscale, https anywhere.
- `backend/ModelStore.kt` — stored pick (`provider_id`/`model_id`), favorites,
  recents (cap 3, `prov/model` keys), AndCode reconcile priority.
- MCP is server-side (`/mcp` via ServeApi; unused in-process `McpClient.kt`
  removed 2026-09-11, restorable from git).
- `backend/ConnectionStore.kt`, `ThemeStore.kt` — connection + theme prefs.
- Chat engine (`chat/ChatViewModel.kt` + `ChatParts.kt`): fire `prompt_async`
  (server-default agent; model only if both IDs valid) → delta streaming →
  transcript completion (fresh assistant message) → reconcile; 120s bounded
  poll fallback; abort-aware errors; todowrite + API todos; question answers;
  archived sessions filtered.

**UI (our styling, AndCode information architecture)**
- Hamburger drawer: New chat, Agents, Projects, Recent chats (+delete),
  Schedules, Settings.
- Chat: parts rendering (text/reasoning-thinking/tool/patch/image/error/
  question cards), activity summary row, permission Allow/Deny (once→always),
  token ring top-right + Context sheet + bottom meter, orbit composer
  (pill row, meter strip, Stop button), Model sheet (Favorites/Recents/All,
  search, Native/Server segments), Spaces sheet (legacy, still wired).
- Settings: connection test/save (re-attaches), theme system/dark/light,
  HTTP MCP list/add/start-stop, about.
- Schedules: DataStore CRUD + WorkManager daily reminder notifications
  (prompt in notification, tap opens app; honset — no headless execution).
- Stack: AGP 9.3.2, Gradle 9.5+ (CI uses 9.7.x), compileSdk/target 37,
  minSdk 28, Kotlin 2.3.0, Compose BOM 2025.09.01, Navigation3, WorkManager 2.10.0.

## 2. What is left (V4, biggest first)

1. **Verify on-device end-to-end** — connect `localhost:4096`, pick model
   (persists), send (streams), permission-gated command (card → Allow),
   reopen from recents. Report exact error text if anything fails.
2. **Review/diff tab** — `GET session/{id}/diff` exists in client scope;
   needs UI (AndCode: `sessionDiff` + `vcsDiff`).
3. **Terminal + file browser** — endpoints known (`file`, `file/content`,
   `find`, `vcs/*`); needs xterm-equivalent + editor UI.
4. **Slash commands + skills** — `GET /command`, `POST /command`,
   `GET /skill` unwired.
5. **R8 release shrink** — debug APK ~65-67M; enable minify for release,
   target <25M.
6. **F-Droid flavor + metadata** — AndCode has `fdroid/` flavor + yml as reference.
7. **Schedules execution** — currently reminders only; AndCode runs agent
   sessions headlessly (large: foreground service + execution coordinator).
8. **Share/fork/summarize/rename** — endpoints known, UI missing.

## 3. What needs focus / known risks

- **Model 400s**: if a reconciled pick goes stale the server 400s with a real
  message now (visible). Watch for provider-ID scheme mismatches between
  `GET /provider` and `prompt_async` on exotic servers.
- **SSE shape drift**: parser is tolerant (unknown → `Unknown`), polling covers
  gaps. If deltas stop appearing but polling works, re-check `properties` shape.
- **`question.asked` custom answers**: only option buttons implemented.
- **Schedules timing**: WorkManager one-shot is inexact (day granularity OK);
  exact alarms need `SCHEDULE_EXACT_ALARM` (not implemented).
- **Notifications**: `POST_NOTIFICATIONS` requested on 33+; denial = silent schedules.
- **Old package**: `com.quark.agent` builds may still be installed alongside —
  uninstall to avoid confusion.
- **Reference repos** (do not import into app, study only): `Quarkcode/`
  holds `opencode-termux`, `kai9000` (541M), `aionui` (935M blobless),
  `opencode-android` (823K, size model), `opencode-ram-report.md`.

## 4. Test checklist for next session

- [ ] Fresh install → Connect `http://localhost:4096` → Start chatting
- [ ] Model sheet lists live providers → pick → restart app → pick persists
- [ ] Send message → streams in → completes → token ring/cost update
- [ ] `rm`-style or shell command → permission card → Allow continues run
- [ ] Stop button aborts cleanly (no error card)
- [ ] Drawer recents reopen with history; delete works
- [ ] Settings theme + MCP add/toggle; Schedules fires notification
- [ ] `gh run list` green on every push; APK artifact downloads

## 5. File map (start here next session)

- App entry: `app/.../MainActivity.kt`, `QuarkApp.kt` (drawer + Nav3 routes)
- Chat: `chat/ChatViewModel.kt`, `ChatParts.kt`, `MessageList.kt`, `ToolCard.kt`
- Backend: `backend/ServeApi.kt`, `ServeEvents.kt`, `ServeModels.kt`
- Docs: `PROJECT_OVERVIEW.md`, `DEPENDENCY_TREE.md`, this file
- Bootstrap rules: `Android/libs.versions.toml.txt`,
  `Android/compatibility-matrix.txt`, `Android/jetpack-compose.mdc.txt`
