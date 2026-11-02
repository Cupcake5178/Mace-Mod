# MacePilot — implementation in progress

**This checkout is not yet an installable Fabric mod. No usable mod JAR has been produced.**

The requested target is **Minecraft Java 26.2**, Fabric, client-only. The original repository was empty. The decision engine and configuration now exist; the Minecraft integration, settings screen, HUD, and in-game validation still need implementation after the build dependencies become accessible.

## Toolchain

The official [Fabric 26.2 example](https://github.com/FabricMC/fabric-example-mod/tree/26.2) specifies:

| Component | Version |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.161.0+26.2 |
| Fabric Loom | 1.19-SNAPSHOT |
| Gradle | 9.8.2 |
| Mappings | No separate Yarn dependency in the official 26.2 template |

Java 25 and Gradle have been installed and run in this cloud workspace. The JDK archive and Gradle distribution were checked against their official SHA256 values. Loom remains the version from the official template; its exact resolved snapshot cannot yet be verified against Fabric Maven.

## Layout

```text
build.gradle, settings.gradle, gradle.properties
gradlew, gradlew.bat, gradle/wrapper/  Official wrapper + distribution checksum
src/main/java/dev/macepilot/
  config/                           Settings schema, validation, atomic persistence
  core/                             Pure Java decision and resource controllers
src/test/java/dev/macepilot/core/    Executable regression scenarios
tools/test-core.sh                  Dependency-free decision-layer tests
tools/cloud-build.sh                Gradle with cloud proxy + system CA trust
```

## Implemented and tested independently

- Observable-player-only threat scoring, bounded clustering, escape direction, and expiring target memory.
- Stable 1v1 targeting; live 1v1/3v1/Auto mode changes; cooldown and hysteresis; manual lock and target cycling decisions.
- Friend/blacklist exclusion by case-insensitive name or UUID, including changes during a fight.
- State machine for acquisition, preparation, climb, alignment, dive, strike request, recovery, escape, and retargeting.
- Continue After Kill defaults OFF. Confirmed observed death stops attacks immediately; when enabled the engine reassesses current entities. Disappearance is treated separately from death.
- Bounded intercept prediction, smooth yaw wrapping and combined rotation limits.
- Rocket and wind-charge request cooldowns/reserves; hotbar detection and stable item selection; discrete elytra jump-edge scheduling.
- Low-health priority, world/session checks, null-player handling, resource gating, and synchronous panic cleanup for owned inputs.
- Versioned atomic properties persistence, numeric validation, category reset and reset all.

These are real Java implementations with test coverage. They have **not** been connected to a running Minecraft client. An action request in the engine is not evidence that a server accepted an attack, boost, flight change, or item selection.

## Validation

Run the standalone tests using Java 25:

```sh
JAVA_HOME=/workspace/.tools/java25 ./tools/test-core.sh
```

Full target build (requires the network access below):

```sh
./tools/cloud-build.sh build --no-daemon
```

On a normal development machine with Java 25 and Internet access, the intended standard build command is `./gradlew build`. The project is unfinished: dependency access alone does not complete the missing client integration.

## Current external blocker

The running cloud network policy returns `403 Forbidden` for the official Fabric Maven and Minecraft metadata hosts. The initial Gradle build failed resolving Loom before any Minecraft compilation could start. Required domain additions have been saved in the environment configuration draft:

```text
meta.fabricmc.net
maven.fabricmc.net
fabricmc.net
piston-meta.mojang.com
piston-data.mojang.com
libraries.minecraft.net
resources.download.minecraft.net
```

Review and save the network changes in environment settings. The draft-save tool also reports that publication is required for the saved environment configuration. Recheck runtime access before resuming the build. No credentials are needed for these public dependency downloads.

## Work still required before release

1. Resolve the official 26.2 dependencies and inspect their current classes/signatures. Do not substitute old 1.21 APIs or disable certificate/checksum verification.
2. Implement the client initializer, tick adapter, real inventory/input/game-interaction bridge, lifecycle cleanup, and configurable key mappings.
3. Implement the Minecraft settings screen, keybind controls, friend/blacklist editors, compact combat/debug HUD, target indicators, localization, assets, and client-only mod metadata.
4. Revalidate real entity interaction reach and line of sight immediately before attacks. Enforce inventory settlement and cooldown checks at the game boundary.
5. Raycast the predicted terrain/landing corridor, validate recovery clearance, and implement server-correction detection from actual client observations. The pure engine currently accepts `corrected` as an input; it does not detect corrections itself.
6. Run `./gradlew build`, fix integration errors, then launch a client and test GUI persistence, all panic/lifecycle paths, flight/dive/recovery, and multiplayer timing.
7. Produce the installable JAR and report actual installation and control bindings. Proposed defaults are R (toggle), Right Shift (GUI), G (lock), Tab (next), and End (panic); **none are registered yet**.

## Approximations to validate in game

Prediction assumes short bounded motion and approximate tick/ping timing. Client-received health may lag or be concealed by a server. A local swing cannot confirm a hit or damage; the engine deliberately reports an attempted hit. Rocket and wind physics depend on terrain, item state, and server timing. The engine never modifies velocity, grants reach, teleports, or extrapolates missing players as live targets.

Do not put a core-test class or sources archive in your Minecraft mods folder. There is currently no finished mod artifact or in-game control UI to install or use.

