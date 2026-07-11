# Contributing

Please include the Minecraft, NeoForge, Create, and MineColonies versions in every compatibility report.

Every commit/build iteration must increment the mod version by +1 on the final numeric segment.

For Codex sandbox validation, do not run `.\gradlew.bat`; use `.\scripts\codex-gradle.ps1 compileJava` or `.\scripts\codex-gradle.ps1 build` so validation uses an existing Gradle 8.14.3 installation/cache and the project `.gradle` cache. See `docs/codex-build-validation.md`.

Performance issues are most useful with before/after Spark profiler links and a short description of the colony warehouse/rack size, number of Create stock links or factory gauges, and whether the affected chunks stayed loaded throughout the test.

Keep changes focused on preserving Create and MineColonies behavior. This project may cache read-only summaries, but it must not fake item movement or alter insertion/extraction behavior.
