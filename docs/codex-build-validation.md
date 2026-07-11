# Codex Build Validation

Codex sandbox passes must not run `.\gradlew.bat` for validation. The Gradle wrapper can try to download its configured distribution, which fails in restricted sandboxes and makes the result depend on network access.

Use the repository validation helper instead:

```powershell
.\scripts\codex-gradle.ps1 compileJava
```

For a full build, pass the normal Gradle task:

```powershell
.\scripts\codex-gradle.ps1 build
```

The helper resolves Gradle 8.14.3 without invoking the wrapper. It checks, in order:

- `CCL_GRADLE_HOME`, when set to a local Gradle 8.14.3 installation.
- The existing user Gradle wrapper cache at `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.14.3-bin`.
- `gradle.bat` on `PATH`, but only when it is exactly Gradle 8.14.3.

The helper always runs Gradle with `--offline --gradle-user-home .gradle`, which is the project-approved cache path for sandbox validation. If dependencies are missing from that cache, the build needs a one-time cache warmup outside the restricted sandbox, but it must not fall back to `.\gradlew.bat` during Codex validation.
