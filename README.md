# Quark Code

Native Android client for [opencode](https://github.com/anomalyco/opencode) — a coding agent that runs **on your phone**. No laptop needed.

Two ways to connect: **on-device mode** (official opencode `serve` inside a Debian guest via proot, loopback `127.0.0.1:4096`, no root needed) or **remote mode** (same API over LAN/Tailscale).

## Features

- Chat with reasoning, tool, patch, and permission cards (Build does, Plan thinks first)
- Workspace file manager — sandboxed: the agent only sees `/workspace`. Files enter via Import, leave via Share/Save, always by you
- HTTP MCP servers, model/provider picker, per-chat model memory
- Token usage screen, slash commands, `@` file mentions, voice input
- Material 3 Expressive, edge-to-edge, light/dark/AMOLED

## Requirements

- Android 9+ (API 28+), arm64
- **4 GB+ RAM recommended** — this build averages ~700 MB–1 GB. RAM gets optimized in future builds
- Wi-Fi for first setup (Debian + opencode download ~150–250 MB into app-private storage)

## Install

1. Download the APK from the latest [GitHub Actions run](../../actions) (`quark-release-apk` artifact)
2. `adb install -r app-gplay-release.apk` (dummy-signed, updates in place)

On first launch: Settings → Device → download Debian + opencode, or point at a remote URL.

## Build

```bash
gradle :app:assembleRelease --no-daemon
# APK: app/build/outputs/apk/*/release/*.apk
```

Requires JDK 17 + Android SDK (compileSdk 37). `scripts/fetch_proot_assets.py` runs automatically at build time to embed the proot launcher suite. Release builds are signed with the checked-in dummy key (`keystore/dummy.jks`, password `quarkdemo`) — replace it before any Play release.

## Known issues

- **Muse-family models can silently crash on every opencode version** when swapping Build ↔ Plan, swapping models mid-chat, or on network drops. That's upstream, not this app — retry the turn or start a new chat.
- Some bugs remain; this is pre-release software.

## Bugs & stars

Found a bug? [Open an issue](../../issues) — and star the repo so others can find it.

## Licenses

Quark Code is **GPL-3.0** (sole author, no outside commits). On-device pieces run as separate processes (mere aggregation):

| Component | License |
|---|---|
| opencode | MIT |
| Debian rootfs | mixed (Debian) |
| proot | GPL-2.0 |
| Bun (inside opencode) | MIT |
| Apache Commons Compress | Apache-2.0 |
