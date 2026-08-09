# Contributing to Conflux

Thank you for improving Conflux. Keep changes focused on cross-server player ghost synchronization.

## Development setup

1. Install JDK 25.
2. Publish Relay API 1.0.0 to Maven Local, or set `GITHUB_ACTOR` and a classic `GITHUB_TOKEN` with `read:packages`.
3. Use the checked-in Gradle wrapper.
4. Run `./gradlew lint`, then `./gradlew check jar` (`.\gradlew.bat` on Windows).
5. Use multiple disposable Paper servers and one disposable Redis instance for end-to-end testing.

## Making changes

- Add tests for wire-contract, filtering, lifecycle, and regression changes.
- Keep transport records in `conflux-api` and Paper/NMS behavior in `conflux-platform-paper`.
- Treat Relay topic names and ghost record fields as compatibility surfaces.
- Keep `relay-api` provided/`compileOnly`; never shade it into Conflux.
- Perform Bukkit and NMS work on the server thread. Relay handlers run on Relay workers.
- Preserve distance limits, per-viewer caps, stale-frame cleanup, payload limits, and packet-only behavior.
- Do not create real server entities for ghosts or make remote players combat-interactive.
- Do not log skin payloads, serialized equipment, Relay credentials, or private player data.
- Update the README and example configuration when behavior changes.
- Do not commit credentials, runtime worlds, server files, build output, Gradle caches, or IDE metadata.

Use `./gradlew lintFix` to format source and project files. Avoid unrelated formatting or refactoring in the same pull request.

## Testing

Unit verification is:

```shell
./gradlew lint
./gradlew check jar
```

Packet changes should also be tested between at least two Paper nodes for spawning, despawning, movement, teleportation, world changes, skin loading, equipment changes, poses, animations, viewer reconnects, Relay reconnects, and remote crashes.

## Pull requests

- Explain the problem and chosen solution.
- Call out Relay topic or Minecraft protocol compatibility effects.
- Include reproduction steps for bug fixes.
- Include observed packet/Redis load for performance-sensitive changes.
- Confirm that no secrets, player data, or production endpoints are present.

By contributing, you agree that your contribution is licensed under the same license as Conflux.
