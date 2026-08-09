# Conflux

Conflux synchronizes client-only player ghosts between separate Paper servers. A player on one copy of a world can see nearby players from other copies moving around with their skin, equipment, pose, and basic animations.

The remote players are packets, not Bukkit entities. They have no collision, hitbox, combat, inventory, persistence, or effect on the local world. Conflux contains no character storage, quests, parties, economy, world switching, or other MMO systems.

Relay provides the typed Redis Pub/Sub transport. Conflux does not open its own Redis connection, and Redis is the only external service required.

## Features

- cross-server position, rotation, and on-ground updates;
- two-tick movement interpolation at the default 10 Hz publication rate;
- signed skin texture synchronization;
- main hand, off hand, and armor synchronization;
- sneaking, sprinting, swimming, and gliding state;
- main-hand swing, off-hand swing, and hurt animations;
- configurable distance filtering and per-viewer limits;
- `/ghosts off|low|medium|high` density controls; and
- automatic removal after a remote node stops publishing.

## Requirements

- Java 25
- Paper 26.2 on every participating backend
- Relay 1.0.0 installed on every participating backend
- one Redis deployment configured through Relay

Every Relay node must have a unique ID and use the same Redis namespace. World names must match across servers for players in those worlds to see one another.

Conflux is Paper-only. Relay may also be installed on Velocity for other applications, but Conflux itself does not need or include a Velocity plugin.

## Build

Relay API is published through GitHub Packages. GitHub Packages requires credentials even for public Maven packages. Set `GITHUB_ACTOR` to your GitHub username and `GITHUB_TOKEN` to a personal access token (classic) with `read:packages`, then use the wrapper:

```shell
./gradlew lint
./gradlew check jar
```

On Windows, use `.\gradlew.bat`. The plugin is written to `build/libs/Conflux-<version>.jar`.

The build also checks Maven Local before GitHub Packages, which is useful while developing Relay:

```shell
# From the Relay repository
./gradlew :relay-api:publishToMavenLocal
```

The Gradle dependency is intentionally `compileOnly`:

```kotlin
repositories {
    maven("https://maven.pkg.github.com/IanTapply22/Relay") {
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly("com.iantapply:relay-api:1.0.0")
}
```

The equivalent Maven declaration is:

```xml
<dependency>
  <groupId>com.iantapply</groupId>
  <artifactId>relay-api</artifactId>
  <version>1.0.0</version>
  <scope>provided</scope>
</dependency>
```

Do not shade `relay-api` into Conflux. Relay supplies the API classes at runtime, and Conflux declares Relay as a required Paper dependency with classpath joining enabled.

## Installation

1. Install `Relay-1.0.0.jar` and `Conflux-<version>.jar` in every Paper server's `plugins` directory.
2. Start each server once.
3. Configure Relay with a unique Paper node ID and the same Redis namespace on every server.
4. Configure Conflux's display limits if desired.
5. Restart the servers and run `/relay status` followed by `/conflux`.

Conflux obtains its node ID from Relay, so it has no Redis URI or duplicate node configuration.

## Configuration

Paper creates `plugins/Conflux/config.yml`:

```yaml
ghosts:
  update-rate-hz: 10
  view-radius-blocks: 96
  maximum-per-viewer: 30
  stale-after-milliseconds: 1500
  show-equipment: true
```

`update-rate-hz` accepts 1–20. Higher rates are smoother but increase Redis and client packet traffic. `maximum-per-viewer` accepts 0–200, and the radius accepts 1–512 blocks.

Players can choose:

```text
/ghosts off
/ghosts low
/ghosts medium
/ghosts high
```

These preferences last until the player disconnects. `high` uses the configured maximum. Administrators with `conflux.admin` can use `/conflux` to see the Relay node ID, known remote players, and currently rendered ghost count.

## How it works

```text
Paper A                         Redis / Relay                         Paper B

real players -- 10 Hz frame --> conflux.ghost.frame.v1 --> remote snapshot cache
animations ---- event packet -> conflux.ghost.animation.v1 -> packet-only players
                                                                    |
                                                           nearby local viewers
```

Each Paper node publishes a complete transient snapshot of its online players through `Destination.paperServers()`. Receiving nodes replace the previous snapshot from that node, discard stale nodes, select the nearest players in the same named world, and create client-only player entities for each local viewer.

Frames are deliberately transient. A missed frame is replaced by the next one, so no durable queue or database is needed. Empty shutdown frames remove ghosts immediately; local staleness removal handles crashes and network partitions.

The packet renderer targets Paper 26.2's Mojang-mapped internals. Minecraft protocol changes can require corresponding Conflux updates.

## Modules

| Module | Responsibility |
| --- | --- |
| `conflux-api` | Ghost frame, equipment, and animation wire contracts |
| `conflux-platform-paper` | Relay integration, state capture, filtering, interpolation, and packet rendering |
| `conflux-distribution` | Paper plugin JAR without Relay API classes |

Contributions are welcome under [CONTRIBUTING.md](CONTRIBUTING.md). Report suspected vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

Conflux is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE).
