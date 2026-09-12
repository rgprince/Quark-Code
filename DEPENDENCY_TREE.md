# Quark dependency tree (from `gradle/libs.versions.toml`, verified vs `Android/`)

- Kotlin 2.3.0 (parent)
  - Compose compiler plugin (`compose`) — compiler managed by Kotlin plugin
  - kotlin-serialization plugin — JSON models for Serve API
  - KSP 2.3.4 (declared, unused — Room/Hilt later)
  - kotlinx-coroutines 1.10.1 (core + android)
  - kotlinx-serialization-json 1.8.0
  - commons-compress 1.26.0 (Apache-2.0, user-approved 2026-09-12 —
    guest tar.xz/tar.gz extraction; same lib and-code uses)
- AGP 9.3.2 (needs Gradle 9.5.0+, compileSdk 37, JDK 17, Jetifier OFF)
  - android-application
- Compose BOM 2025.09.01 (needs AGP 9.0+, SDK 36+, Kotlin 2.3.0+)
  - material3, foundation, runtime (all used)
  - ui-tooling-preview is debug-only (`debugImplementation`)
   - REMOVED 2026-09-11 (zero imports): material-icons-extended
     (only core `Icons.Filled`/`AutoMirrored` used)
   - RE-ADDED 2026-09-11 (V5 expressive redesign, approved coordinates verbatim
     from `Android/libs.versions.toml.txt`, aliases avoid reserved word `class`):
     material3-window-size-class (compact/medium/expanded chat max-width),
     material3-adaptive-navigation-suite (drawer/rail hybrid shell)
- Activity 1.9.3 → activity-compose
- Core 1.15.0 → core-ktx (only plain-core APIs used today)
- Lifecycle 2.10.0 → runtime-compose, viewmodel-compose
- Navigation3 1.0.0 (Compose-only, needs AGP 8.9.1+, SDK 36+)
  - navigation3-runtime, navigation3-ui (NavEntry/NavDisplay in QuarkApp)
  - REMOVED 2026-09-11: lifecycle-viewmodel-navigation3 (ViewModels are
    plain activity-scoped `viewModel()`, no Nav3 scoping APIs used),
    savedstate-compose (no rememberSerializable/savers used)
- OkHttp 4.12.0 → okhttp (direct Serve client, AndCode-style; no Retrofit)
- DataStore 1.2.0 → datastore-preferences only (all 5 stores use the
  preferences API; bare `datastore` aggregator REMOVED 2026-09-11)
- Coil 2.7.0 → coil-compose (single AsyncImage in MessageList)
- WorkManager 2.10.0 → work-runtime-ktx (schedule reminders only)
- MCP is server-side: Settings talks to the opencode server's `/mcp`
  endpoint via ServeApi. The unused in-process `McpClient.kt`
  (Streamable-HTTP) was REMOVED 2026-09-11 — restore from git if
  direct MCP execution is ever needed.
