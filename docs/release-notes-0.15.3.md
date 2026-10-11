## RapfiDroid 0.15.3

### Changes

* **Analyse on an empty board plays H8 and stops.** Rapfi answers the empty
  board at once (H8) without a search; that move is now placed and the analysis
  ends there. Any other instant answer (a forced reply) is handled the same way.

### Under the hood

* Build toolchain and libraries brought up to date: Gradle 9.8, Android Gradle
  Plugin 9.4 with its built-in Kotlin, Kotlin 2.4, Compose BOM 2026.09
  (compileSdk 37), Hilt 2.60, coroutines 1.11, serialization 1.11, Navigation
  2.10, Okio 3.18 and others.
* Dependabot now proposes dependency and GitHub Actions updates every week.
