# OPEN BUGS — handoff for a fresh session

> RESOLVED 2026-09-12 (V6, v0.3.0 code 3): all 5 items fixed in one push —
> variant pill restored, drawer tolerant + error state, meter counts
> text+reasoning with full accounting, thinking derived from busy, thought
> persists across turns — plus audit extras (permission URL, delete/summarize
> Unit, retry id). Stable dummy-key R8 release enables `adb install -r`
> updates. Verify on-device before closing.
>
> Original report kept below for history.

Date: 2026-09-11 · Branch: `main` · Last commit at time of writing: `5b5ff5d`
(V5.9). User reports: attempted fixes changed nothing on-device, plus one
regression introduced by the redesign. Verify everything below on a real
device before claiming fixed.

## 1. REGRESSION (my fault): thinking/normal variant selector gone from composer

- What I did: in V5.5 I replaced the model pill with a robo icon, then in a
  later pass replaced the robo icon with a small model-name pill and **deleted
  `VariantMini`** (the "auto"/thinking-effort pill, ex-brain icon) from
  `app/src/main/java/com/rg/quarkcode/chat/OrbitComposer.kt`.
- I moved variant selection into the model sheet ("Thinking effort" chips in
  `ModelSheet.kt`), but the user wants it back **in the composer** — one tap,
  next to the model pill, as before.
- Fix: restore a compact variant pill in the composer action row
  (`OrbitComposer.kt`, next to the model pill + meter text). Keep the sheet
  section too (harmless duplicate) or remove it once the pill is back.
  Params `variants`, `selectedVariant`, `onVariantChange` still exist on
  `OrbitComposer` and are still wired through `ChatScreen.kt` → `QuarkApp.kt`
  → `ChatViewModel.onVariantChange` — only the UI was deleted, so this is a
  pure UI restore, no VM work needed.

## 2. NOT FIXED: drawer shows "No chats yet" despite chats existing

- Attempted (V5.8/V5.9, commits `f616509`, `5b5ff5d`): raised recents cap
  20 → 100 sorted by recency (`ChatViewModel.loadRecents`), accepted
  `archived == 0L` as well as null, added a "tap to reload" button wired to
  a new public `refreshRecents()`, plus a drawer empty hint.
- User reports: still empty on-device.
- Prime suspect (unverified): the server's `GET session` list shape doesn't
  match `List<SessionInfo>` (e.g. wrapped object, different time/archived
  encoding), so `getList` throws and `getOrNull() ?: return` silently keeps an
  empty list. Note the usage-scan screen uses the SAME endpoint and once
  showed 30 sessions — so either the shape changed, the connection differs,
  or the failure is elsewhere (e.g. an exception in the mapping lambda).
- Next step: reproduce with logging or paste one raw `GET /session`
  response; add a visible error state to the drawer instead of failing
  silently. Relevant files: `ChatViewModel.loadRecents()` (~line 488),
  `backend/ServeApi.getList()`, `drawer/QuarkDrawer.kt`.

## 3. NOT FIXED: token meter feels dead ("only counts what I type")

- Attempted (V5.9): bump `stats.used` on every streamed text delta
  (`handleEvent` → `PartDelta`), refresh server truth every poll cycle,
  poll interval 3000ms → 1500ms.
- User reports: meter still stuck (e.g. `86(0%)`), no live climb.
- Suspects: (a) deltas arrive as `reasoning` not `text` on this server
  (the bump only handles `field == "text"`); (b) `refreshCost` GET fails
  silently (`getOrNull() ?: return`); (c) `stats.limit` is 0 or stale so the
  % never moves — check what renders `86(0%)`: `meterLabel`/`ringLabel` in
  `ChatScreen.kt` and the compact text in `OrbitComposer.kt`.
- Next step: log which delta fields actually arrive; extend the bump to
  reasoning deltas; surface refresh failures instead of swallowing them.

## 4. NOT FIXED: dead gap between thinking and first response

- Attempted (V5.9): live `thinking… Ns` counter (`ChatScreen` 1s timer →
  `MessageList.ThinkingTail`), faster poll, meter climb.
- User reports: still feels like the model stopped working.
- Suspects: the thinking tail is suppressed whenever a running activity row
  exists (`hasRunningActivity`), and activity rows only appear once tool
  parts arrive — so during a long pre-tool reasoning phase there may be NO
  visible indicator at all if `thinking` itself got cleared early (e.g. by
  the poll's `thinking = merged.none { … }` recompute or a stale idle
  event beating the 4s `ignoreIdleUntil` guard).
- Next step: trace the actual `thinking` flag transitions on-device during a
  slow turn; consider deriving the tail from `busy` (like the stop button)
  instead of the `thinking` flag.

## 5. NOT FIXED (possibly): thought lines disappearing

- Attempted (V5.9): only record ≥800ms phases, keep the old line across
  flicker, render after the last user message.
- If still vanishing: the placement (`index == lastUserIndex`) unmounts the
  row as soon as a newer user message exists, and any `thinking=true` edge
  hides it. Reconsider: attach the thought to its assistant message instead
  of tracking one global "last thought".

## 6. Verify on-device (claimed fixed, user hasn't confirmed)

- Thought-after-prompt ordering (V5.8): `itemsIndexed` + `lastUserIndex`
  in `MessageList.kt`; phantom reasoning-only activity rows skipped.
- RichText engine (`chat/RichText.kt`): headings, lists, quotes, tables,
  code+copy, streaming caret. Watch for mis-rendered fences mid-stream.
- Usage scan counts all chats (30-cap removed); totals include cache reads.
- Top pill shows chat name (set on send/open/rename/recents-sync).
- R8 release build (`quark-release-apk`, v0.2.0): launches OK after the
  Room keep-rules fix — but this crash class (R8 stripping) can recur with
  any new reflection-based dependency. Any new release needs an on-device
  open test, not just a green CI.

## Regression checklist (do these first in the new session)

1. [x] Restore variant pill in composer (see §1).
2. [x] Fix drawer recents (§2) — highest user pain.
3. [x] Fix live meter (§3).
4. [x] Fix dead thinking gap (§4).
5. [x] Re-test thought persistence (§5).
