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
- MCP: server-side via opencode's `/mcp` endpoint (Settings list/add/toggle).
  Unused in-process `McpClient.kt` removed 2026-09-11 (see DEPENDENCY_TREE.md).

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

## V4.2 batch — slash-all, thinking lag, todos, drawer, provider keys, composer, review
- [x] Slash popup scrolls full list (`heightIn 280.dp`, no take(6))
- [x] Thinking lag fixed: tail clears on first streamed content + on transcript answer
- [x] Ring label capped at 100%
- [x] Session todos collapsible + dismissible (`todosVisible`, re-shows on change)
- [x] Drawer slimmed: recents open-only (delete removed), Review/Schedules/Settings
- [x] Provider tap → auth dialog: `GET provider/auth` methods shown, `PUT auth/{id}` save key, `DELETE auth/{id}` disconnect
- [x] Composer: short model label (provider hidden), tighter rows, 44dp send
- [x] Review screen (drawer): `GET session/{id}/diff` file cards + `PATCH` rename + `POST summarize` (no share endpoint on server)

## Later (V4+)
- Terminal + file browser, R8 release shrink (debug APK 65M), F-Droid flavor
- Voice/TTS (needs RECORD_AUDIO), GitHub integration, workspaces, guest browser

## V4.3 batch — opencode-style editor, providers submenu, queue, visibility
- [x] Composer rebuilt (subagent spec): bordered `surface` card, `BasicTextField` 1-4 lines, model pill ≤168dp left + `weight` spacer + 38dp send pinned right, mode/meter strip BELOW box, 34×3dp `CompactMeter` bar, `imePadding`
- [x] Settings providers → lazy submenu (`ProvidersRoute` + `ProvidersScreen` search/list/dialog); landing shows summary row only
- [x] Send queue: Interrupt/Queue toggle (Settings Chat + `ChatPrefs`), `Queued N` pill, drain on idle; offline queue auto-sends on attach
- [x] Model visibility: hide eye in Show-all mode, Hidden section to unhide, `ModelStore.hidden`, pick unhides

## V4.4 batch — voice, @ mentions, server info (subagent research)
- [x] Voice, framework-only (kai9000 has NO vosk/mic/wake code — TTS-output only via unapproved KMP lib): mic button + `RecognizerIntent` transcript insert (`RECORD_AUDIO`), framework `TextToSpeech` readout (per-message speaker in footer, auto-speak toggle, markdown-stripped, stops on send)
- [x] `@` file mentions (aionui IA): `GET find/file` ranked popup, insert literal `@path` (server resolves natively); and-code/kai9000 confirmed no @ mapping
- [x] Server info screen: version, `GET`/`PATCH config` editor, providers/commands/skills lists; diagnostics upgraded (app version, memory, storage). No log viewer exists upstream — no endpoint to wire

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

## V5 scope — full M3 Expressive redesign (single-push, CI minutes saved)
- [x] Theme: 4-way Void/Paper/Dynamic/System (`ThemeMode.DYNAMIC`, dynamic
  schemes on API 31+ with Void/Paper fallback, expressive shape scale)
- [x] Edge-to-edge shell (`enableEdgeToEdge`, `adjustResize`, Scaffold
  innerPadding → list contentPadding, composer IME-safe)
- [x] Chat timeline: responsive bubbles (timestamp inside, selection),
  markdown-lite (fenced code + copy, bold, inline code, headings),
  expressive Activity/Reasoning/Tool/Patch/Error cards, 48dp footer,
  LoadingIndicator thinking tail, permission/question entrances
- [x] OrbitComposer rebuild: 32dp container, Assist/Filter chips,
  56dp XL send↔stop morph, determinate meter, 48dp slash/@ menus
- [x] TopBar: token-aware badge dot (no hardcoded colors), ring with semantics
- [x] SpacesSheet deleted (drawer is the single recents source; VM flag kept
  dormant for zero-logic-change); drawer FAB + hero copy
- [x] Connect hero + quick-fill chips + XL action + error card; ContextSheet
  progress + Export/Copy; ModelSheet ListItem rows
- [x] Approved deps only (BOM 2025.09.01 kept): re-added window-size-class +
  adaptive-navigation-suite with approved coordinates; no markdown/coil3 adds
  (markdown-lite is dependency-free)

## V5.1 scope — compact density (phone-first, from screenshots)
- [x] Tool calls are one-line terminal rows (status dot + category-tinted icon
  + mono name + pretty summary, no raw JSON); errors auto-expand; subagents
  (`task`) render as violet "subagent · description" rows
- [x] Transcript density: bodyMedium assistant text, 8dp gaps, 12dp margins,
  slim bubbles, compact tail (hidden while activity row runs), tighter cards
- [x] Composer slimmed (bodyMedium input, 48dp send, tighter paddings/gaps)
- [x] Questions are single-tap FlowRow chips (no radios/confirm); empty state
  shrunk; permission card tightened
- [x] Settings redesigned with top tabs (Connection/Look/Chat/MCP/Server)

## V5.2 scope — busy correctness + composer merge + thought timing
- [x] Send/stop bug fixed two ways: stale idle events ignored for 4s after a
  new run (VM `ignoreIdleUntil`), and UI derives `busy` from sending flags OR
  live streaming/running tools — stop shows whenever work is live
- [x] `send()` treats running tools as busy (queue/interrupt, no dead taps)
- [x] Mode strip merged into the editor: color line on top (primary = build,
  tertiary = plan), compact mode/variant pills in the action row, meter is a
  2dp bar in one shared slot with sending progress
- [x] Thinking is a tiny inline row; when done it becomes a clickable
  "Thought for 2.3s" row that expands the last reasoning (UI-local timer)
- [x] Todo card is a plain small box (no flashy container/progress)
- [x] Question card has a proper header (icon + "Agent needs your input")

## V5.4 scope — editor blunder fix + meter text + settings fill
- [x] Fullscreen editor fixed: outer Row is height(IntrinsicSize.Min) so the
  mode strip's fillMaxHeight stays bounded (was infinite measure)
- [x] Token count re-added compactly beside the model icon: `12.4k(6%)`
- [x] Editor shape 32dp → 18dp (slightly rectangular)
- [x] Settings filled: connection quick-fill chips, Look text-size preview,
  Termux start-command copy, About fact rows (all zero-backend-cost)

## V5.5 scope — model menu, drawer, pill, thinking, usage
- [x] Model menu redesigned custom (non-M3 rows): full names wrap to 2 lines,
  provider/id mono line, check-circle select, thinking-effort chips in-sheet
- [x] Drawer icon is a custom 2-line mark; drawer redesigned (identity header,
  Chats/Workspace/System sections, keyed recents with icons)
- [x] Composer: robo icon replaced by a small model-name pill (variant control
  moved into the model sheet)
- [x] Thinking is an italic line with a secondary side bar + duration; tap
  expands the reasoning
- [x] Server-info screen removed; new Usage screen (confirm → scan ≤30
  sessions): week/month/all totals, per-model bars, server cost (Free shown),
  pro-rata per-model cost split

## V5.6 scope — honest usage + no old thinking + R8 release
- [x] Usage accounting fixed to match the chat ring: totals now include
  cache reads (input + cache + output), so ~65M-style sessions add up;
  per-model rows show new + cache split
- [x] By-model donut pie chart (Canvas, no new dep) with % legend
- [x] Old thinking UI deleted: ReasoningCard gone (inline + sheet), thought
  line is the only reasoning surface; auto-expand pref/settings/VM removed
- [x] R8 release only (debug ignored): minify + shrinkResources +
  proguard-rules.pro (serialization/DataStore), debug-signed installable
  APK, v0.2.0 (code 2), workflow builds `assembleRelease` → quark-release-apk

## V5.7 scope — launch-crash fix + tagged-model usage
- [x] Fixed instant-crash on open (R8 had stripped Room's WorkDatabase_Impl
  that WorkManager builds reflectively): keep RoomDatabase impls + entities
- [x] Usage attributes whole sessions to a tagged model: the session's own
  `model` tag first, else majority vote across its answers (no pro-rata math;
  totals match the chat ring exactly)

## V5.8 scope — think-then-reply order + rich text + uncapped stats
- [x] Thought line moved to right after the prompt it answered (think, then
  reply); phantom "Activity" boxes with no tools no longer render
- [x] RichText engine (own code, studied kai9000's block/inline pattern):
  headings, bullets, numbered lists, quotes, tables, rules, code + copy,
  bold/italic/strike/links; streaming-safe; blinking caret while streaming
- [x] Recents raised to 100 sorted by recency + drawer empty hint; usage scan
  counts every non-archived chat (no 30-cap)

## V5.9 scope — drawer truth + live meters + chat names + alive thinking
- [x] Empty drawer fixed: archived filter accepts 0/null, manual "tap to
  reload" recovery wired to a public refreshRecents()
- [x] Token count is live: streamed deltas bump `used`, poll refreshes server
  truth every 1.5s (was: estimate-only until turn end); model context limits
  already applied
- [x] Top pill shows the chat name (set on send/open/rename/recents-sync)
- [x] Thinking shows live elapsed seconds ("thinking… 5s"); thought lines only
  record ≥800ms phases and survive flicker (no more vanishes)
- [x] Audit: RichText parser termination + regex precedence verified, nested
  clickables safe, dead spacesSheet left dormant intentionally

## V6.5 scope — QoL batch (links, per-chat model, instant cache, simpler settings)
- [x] Links get primary colour (underlined + medium)
- [x] Per-chat model memory: opening a chat adopts its backend-tagged model
- [x] Instant illusion: `CacheStore` snapshot (chats + catalog) paints on
  launch, refreshed by every live load (also fresh on close)
- [x] Settings: Server tab → Stats tab (Usage only); About, diagnostics,
  server version, start command removed (code too)
- [x] Schedules UI removed (drawer entry + route; files stay dormant)
- [x] App starts at chat, auto-attaches saved backend; URL editable anytime
  in Settings → Connection → Test & save
- [x] v0.8.0 (code 8), same dummy key — `adb install -r` updates

## V6.4 scope — links, no-more-fake-idle, cooking bubble
- [x] Links tappable: `[text](url)` was annotated but never consumed; now
  `ClickableText` + `LocalUriHandler`, plus bare-URL auto-linking
- [x] Fake-idle killed: new `awaitingReply` survives early idle/thinking
  clears; stale idle ignored while the prompt has no reply; fresh-only
  answer detection (history no longer ends turn 2+ instantly)
- [x] Cooking bubble: rotating Cooking/Doodling/Crafting… assistant bubble
  fills the pre-reply gap, replaced by real content on arrival
- [x] v0.7.0 (code 7), same dummy key — `adb install -r` updates

## V6.3 scope — thought fallback, chat-name bar, plain tool words, flat todos
- [x] Thought line actually appears now: falls back to transcript reasoning
  (old chats never run the live timer) — `Thought` without time when no
  timing, `Thought · 16ms` when timed
- [x] Top bar: folder chip gone — plain chat-name title (tap opens drawer),
  "New chat" until the server generates a title (no more raw typed text)
- [x] Tool rows: terminal glyph in variant tone, plain "3 tool calls" /
  "Changed 2 files" wording (jargon summary kept as sheet title only)
- [x] Todos flat: one card, 17dp check dots, tight rows (checkboxes removed)
- [x] v0.6.0 (code 6), same dummy key — `adb install -r` updates

## V6.2 scope — thought-line revert + slim tools (user feedback)
- [x] "Thought N time(s)" card removed; old italic thought-line back, now
  tertiary with side bar, `Thought · 16ms` format just above the reply
- [x] Every think phase recorded (800ms gate dropped) — fast thoughts show too
- [x] Slim rows: activity row whole-row tap, 34dp buttons, 15dp icons;
  tool output cap 120dp/6 lines; transcript gaps 8→6dp, bubble padding down
- [x] v0.5.0 (code 5), same dummy key — `adb install -r` updates

## V6.1 scope — screenshot-driven fixes (drawer crash, real tokens, thinking UI)
- [x] Drawer crash fixed (screenshot error verbatim): tolerant `ModelRef`
  (`"model": {}` no longer kills `GET session`; multi-casing keys, `""` defaults)
- [x] Real token count (AndCode parity): `refreshCost` reads latest non-user
  MESSAGE tokens first (11k-class truth), session as fallback; never backwards
- [x] Thinking UI restored: reasoning-only turns render AndCode-style
  `Thought N time(s)` expandable rows (was skipped as "phantom noise")
- [x] v0.4.0 (code 4), same dummy key — `adb install -r` updates

## V6 scope — OPEN_BUGS fix batch + update-ready R8 (single push)
- [x] Variant pill restored in composer (`OrbitComposer.VariantPill`: auto + list, next to model pill; sheet section kept)
- [x] Drawer truth: tolerant `SessionTime` (archived bool/num/string), `recentsError` surfaced in drawer with retry (no more silent empty)
- [x] Live meter: `contextUsed` = input+cache+output+reasoning, deltas bump on text AND reasoning, `refreshCost` never runs backwards, ring min 1%, composer shows `used / limit · pct`
- [x] Thinking gap: poll no longer clears `thinking` on historical text; tail derives from `thinking || busy`, suppressed only by running activity
- [x] Thought persistence: thought row stays across turns (no `!thinking` hide)
- [x] Audit extras: permission URL used session id (Allow/Deny was 404), `deleteSession`/`summarize` use Unit (no Boolean decode crash), usage scan accepts archived=0, retry passes message id (was part id → no-op)
- [x] Update-ready R8: stable `keystore/dummy.jks` (quarkdemo/dummy) signed release, v0.3.0 (code 3) — `adb install -r` updates, no uninstall

## V5.3 scope — editor rework + crash + text size + audit
- [x] Crash on typing `/` fixed: slash list deduped by name (backend often
  redefines /help//new/…) + unique popup keys
- [x] Editor: mode side-strip (4dp color bar, tap = menu, long-press = swap),
  model pill → SmartToy icon button, mic hidden while typing, input grows to
  4 lines then inner-scrolls
- [x] Text size control in Settings → Chat (Small 0.85 / Medium 1.0 /
  Large 1.15, DataStore-persisted, applied to chat + composer live)
- [x] Subagent perf/UI audit applied: bounded model sheet list, no nested
  scroll traps (Review/ServerInfo), capped chat images, keyed drawer rows,
  progress semantics, 48dp sheet buttons, memoized schedule subtitles
