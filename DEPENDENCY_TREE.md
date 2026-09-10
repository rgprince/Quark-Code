# Quark dependency tree (from `gradle/libs.versions.toml`, verified vs `Android/`)

- Kotlin 2.3.0 (parent)
  - Compose compiler plugin (`compose`) — compiler managed by Kotlin plugin
  - kotlin-serialization plugin — JSON models for Serve API + MCP
  - KSP 2.3.4 (declared, unused in V1 — Room/Hilt later)
  - kotlinx-coroutines 1.10.1 (core + android)
  - kotlinx-serialization-json 1.8.0
- AGP 9.3.2 (needs Gradle 9.5.0+, compileSdk 37, JDK 17, Jetifier OFF)
  - android-application
- Compose BOM 2025.09.01 (needs AGP 9.0+, SDK 36+, Kotlin 2.3.0+)
  - material3, foundation, material-icons-extended, runtime
  - material3-adaptive-navigation-suite (Spaces sheet + adaptive later)
  - ui-tooling-preview (ui-tooling is debug-only, added in app module)
- Activity 1.9.3 → activity-compose
- Core 1.15.0 → core-ktx
- Lifecycle 2.10.0 → runtime-compose, viewmodel-compose
- Navigation3 1.0.0 (Compose-only, needs AGP 8.9.1+, SDK 36+)
  - navigation3-runtime, navigation3-ui
  - lifecycle-viewmodel-navigation3 2.10.0
- SavedStateCompose 1.3.1 → savedstate-compose
- Retrofit 2.11.0 → retrofit-core, converter-kotlinx-serialization
- OkHttp 4.12.0 → logging-interceptor
- DataStore 1.2.0 → datastore, datastore-preferences (preferences API
  lives in the `-preferences` sibling; the bare `datastore` aggregator
  does not expose it — same group, same approved version)
- Coil 2.7.0 → coil-compose (image cards)
