# QA Client Workflow

Run the manual QA client from this project directory:

```bash
gradlew runQaClient
```

The task launches Minecraft 1.21.1 with NeoForge 21.1.226 from `runs/qa-client/` and loads the current local source output of `undead_horse_compat`. It does not copy jars into a live modpack or server folder.

Required runtime dependencies are resolved through Gradle. Optional QA-only jars can be placed in:

```text
runs/qa-client/mods/
```

That folder is isolated to this project and is not used by the release jar.
