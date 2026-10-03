# AI Coding Guardrails (Rules) — Smusic Development

## 1. Core Architectural Constraints
- **Unidirectional Data Flow (UDF):** Every screen must observe state via Kotlin `StateFlow` from a ViewModel. Actions are sent via intent/event functions. No business logic inside Composable functions.
- **Layer Separation:**
  - `domain`: Pure Kotlin logic only (no Android framework dependencies except basic context/URI abstractions when strictly necessary).
  - `data`: Implementations of database, network workers, and platform services.
  - `ui`: Jetpack Compose screens, components, and theme styling.
- **Provider Pattern:** All new media sources must implement the `MediaProvider` interface and be registered in `ProviderRegistry`. Never hardcode provider-specific branching into UI screens.

---

## 2. Coding Style & Language Standards
- **Kotlin Idioms:**
  - Prefer immutable `val` over mutable `var`.
  - Use sealed interfaces / classes for state modeling (`AnalysisResult`, `JobState`, `UiState`).
  - Use Kotlin Coroutines and `Flow` for asynchronous streams. Never use raw threads or legacy `AsyncTask`.
  - Avoid callbacks; use suspending functions returning standard Kotlin `Result<T>` or custom sealed models.
- **Jetpack Compose Best Practices:**
  - Always pass modifier as a parameter defaulting to `Modifier` (`modifier: Modifier = Modifier`).
  - Hoist state to caller where possible to maximize component reusability and previewability.
  - Never run side-effects directly inside the composition pass; use `LaunchedEffect`, `rememberUpdatedState`, or `DisposableEffect`.
  - Use semantic Material 3 tokens (`MaterialTheme.colorScheme`, `MaterialTheme.typography`).

---

## 3. Error Handling & Resilience
- **No Silent Failures:** Catch specific exceptions (`IOException`, `SocketTimeoutException`, `MalformedURLException`). Never suppress exceptions with empty catch blocks.
- **Real Progress Only:** Never fabricate or simulate download speed, byte progress, or ETA. If total bytes are unknown (e.g. chunked transfer), report indeterminate progress (`-1L`).
- **Resilient I/O:** Always close streams in `finally` blocks or via `.use { }`.
- **Atomic File Operations:** Always write to temporary staging files (`.part`) and atomically rename upon complete verification.

---

## 4. Legal & Licensing Guardrails
- **Zero GPL Contamination:**
  - Study architectural concepts (e.g. YTDLnis provider architecture), but **NEVER copy/paste GPL source code** into this repository.
  - All implementations must be clean-room written from scratch under Apache-2.0 / MIT compatible terms.
- **No DRM Circumvention:**
  - Do not implement proprietary DRM decryptors or extract protected streams.
  - Spotify integration is strictly restricted to public oEmbed metadata.

---

## 5. Environment & Secret Management
- **No Hardcoded Credentials:** Never commit API keys, personal tokens, or passwords to git.
- **Local Configurations:** `local.properties` and local build outputs must remain ignored by git at all times.
