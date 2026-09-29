# Quark Code — personal alpha to run opencode on your phone

Personal project in alpha stage, made to run [opencode](https://github.com/sst/opencode)
sandboxed and locally on your phone. No laptop needed.

![Chat](fastlane/metadata/android/en-US/images/phoneScreenshots/Screenshot_20260924-171016_Quark%20Code.png)

## Modes

- **On-device** — the official opencode server runs headless inside a Debian guest
  (proot, no root required) on loopback, behind a password.
- **Remote** — point it at any reachable opencode server URL (LAN / Tailscale).

Note: this app always runs online (phone or remote server). No offline mode.

## Workshop files (sandbox)

The agent only sees `~/workspace` plus the guest system. Nothing else on your
phone is visible.

- Bring files in: Workshop → Import (SAF picker).
- Take files out: select → Share / Save to phone.
- A Sandbox screen shows what the agent can see and how files enter and leave.

## Keys & cost

You bring your own provider keys (Settings → Providers → tap provider → save).
Quark only shows tokens and cost.

You can try opencode free models first, but don't share sensitive data —
free models can be used to train AI.

## Requirements

- arm64 phone, Android 9+ (API 28+)
- ~700 MB–1 GB RAM while running. Under 4 GB RAM is not recommended yet.
- Wi-Fi recommended for the first download (Debian ~150–250 MB + opencode ~60 MB).

## Tools tip

If an extra package direct download didn't work, just ask the agent to download it.

## Features

- Streaming chat with tool / patch / image / error cards, thinking tail
- Tool permission cards (Allow once / Always / Deny), todo cards, activity timeline
- Model & provider picker with stored selection + reconcile, favorites
- MCP servers over HTTP (list / add / toggle in Settings)
- Context meter + cost display, Void/Paper/Dynamic/AMOLED themes
- Workspace file manager with ZIP/text/image viewers, import/export via SAF
- Connection test, per-provider settings

## Build

```bash
./gradlew assembleDebug
```

Debug builds sign with the checked-in demo key (`keystore/dummy.jks`) so CI
builds share one signature. **Replace with a real keystore before any
Play / F-Droid release.**

## Credits

- Flow ideas studied from AndCode (MIT, https://github.com/yuga-hashimoto/and-code),
  the opencode TUI, and Kai9000's HTTP-only MCP approach. All Quark
  implementation is original — verified with clone + license scans
  (jscpd + ScanCode, no substantive copies, no proprietary blobs).
  Any opencode client necessarily speaks the same API: endpoints, SSE shapes,
  and field names are dictated by the server, not chosen.
- Agent backend: [opencode](https://github.com/sst/opencode).
- On-device Termux packaging: [Hope2333/opencode-termux](https://github.com/Hope2333/opencode-termux).

## License

Quark Code is free software under the **GNU General Public License v3.0**
(see [LICENSE](LICENSE)). Runtime components keep their own licenses
(MIT / GPL-2.0 / Apache-2.0) — see Settings → About in the app and
[RUNTIME_NOTICES.md](RUNTIME_NOTICES.md).

Bugs & stars: https://github.com/rgprince/Quark-Code — please report bugs
and star the repo so others can find it.
