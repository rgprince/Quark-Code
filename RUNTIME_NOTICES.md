# Quark on-device runtime — notices & relicense plan

The APK stays lean (~4–6 MB): the guest system downloads on first opt-in
(Settings → Device) into app-private storage. Components below are **not**
Quark code — build-time tools stay in CI, guest pieces download at runtime
(mere aggregation: spawned as separate processes, never linked).

## Components

| Component | License | Source |
|---|---|---|
| opencode (official AI coding agent) | MIT | https://github.com/opencode-ai/opencode |
| Debian slim rootfs (proot-distro builds) | mixed (Debian) | https://github.com/termux/proot-distro (fallback: theworkjoy/proot-distro) |
| PRoot user-space chroot | GPL-2.0 | https://github.com/proot-me/proot (via Termux packages) |
| libtalloc, libandroid-shmem (proot support libs) | BSD/ISC | Termux packages |
| Bun JS runtime (inside opencode) | MIT | https://github.com/oven-sh/bun |
| Apache Commons Compress (archive extraction) | Apache-2.0 | https://commons.apache.org/proper/commons-compress/ |
| Quark's downloader/supervisor/UI | GPL-3.0 (for now, see below) | this repo |

## Inspiration credit

Flow ideas studied from AndCode (MIT, https://github.com/yuga-hashimoto/and-code).
All Quark code is rewritten — no AndCode source is copied.

## GPL-3 acceptance (temporary)

Quark's own runtime-supervision code is accepted as GPL-3 **for now** because
the guest stack touches GPL tooling. Relicense plan (sole-contributor rule):

- No outside commits are merged — the author remains the sole copyright
  holder of all Quark-original code and may relicense it at any time.
- GPL/third-party pieces live isolated under
  `app/src/main/java/com/rg/quarkcode/backend/ProotSuite.kt`,
  `Guest*.kt`, `DebianInstaller.kt`, `LocalBackend.kt`,
  `QuarkBackendService.kt`, `BackendViewModel.kt`,
  `settings/DeviceBackendPanel.kt`, `scripts/fetch_proot_assets.py` —
  removing the on-device backend later is a `git rm` of those files, and
  the copyleft leaves with them.
- If/when those files go, the remaining tree can be relicensed freely
  (fresh export recommended on relicense day for a spotless tree).
