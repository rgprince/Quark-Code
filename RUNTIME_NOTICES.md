# Quark on-device runtime — notices & relicense plan

The APK stays lean (~4 MB): the server runtime downloads on first opt-in
(Settings → Device) into app-private storage. Components below are **not**
Quark code — they are downloaded at runtime (mere aggregation: spawned as
separate processes, never linked).

## Components

| Component | License | Source |
|---|---|---|
| opencode (official AI coding agent) | MIT | https://github.com/sst/opencode |
| opencode native Android build (zero-glibc Bionic ELF) | MIT | https://github.com/Hope2333/opencode-termux |
| Bun JS runtime (inside the ELF) | MIT | https://github.com/oven-sh/bun |
| Android toybox/shell (system, used via PATH — not shipped) | 0BSD/BSD | AOSP |
| Quark's downloader/supervisor/UI | GPL-3.0 (for now, see below) | this repo |

## GPL-3 acceptance (temporary)

Quark's own runtime-supervision code is accepted as GPL-3 **for now** because
the guest stack touches GPL tooling. Relicense plan (sole-contributor rule):

- No outside commits are merged — the author remains the sole copyright
  holder of all Quark-original code and may relicense it at any time.
- GPL/third-party pieces live isolated under
  `app/src/main/java/com/rg/quarkcode/backend/Runtime*.kt`,
  `LocalBackend.kt`, `QuarkBackendService.kt`, `BackendViewModel.kt`,
  `settings/DeviceBackendPanel.kt` — removing the on-device backend later is
  a `git rm` of those files, and the copyleft leaves with them.
- If/when those files go, the remaining tree can be relicensed freely
  (fresh export recommended on relicense day for a spotless tree).
