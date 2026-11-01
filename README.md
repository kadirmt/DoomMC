# DoomMC

**Play a Doom-style shooter on a giant screen inside Minecraft.**

[Türkçe](README.tr.md) · [Downloads](https://github.com/kadirmt/DoomMC/releases) · [Report an issue](https://github.com/kadirmt/DoomMC/issues)

DoomMC adds an automatically assembled **25 × 16 block screen** and a paired **Doomplayer Remote**. Stand in front of the screen, take the controls, and play while nearby players watch the same game in the world.

The shooter uses a self-contained Java raycasting engine with a **320 × 200** framebuffer and a **35 Hz** simulation. Its textures and sprites are generated in code; no external DOOM installation or WAD file is required.

## Features

- One screen item builds the entire 400-block display, facing the player.
- Pair a remote with a screen, then right-click to play.
- Mouse aiming, keyboard controls, three weapons, health, armor, and ammunition pickups.
- Three built-in levels: **Hangar**, **Refinery**, and **Inferno**.
- Four enemy types: zombies, imps, demons, and barons.
- Server-controlled game sessions: one player controls each screen, while nearby players can watch.
- Releasing the controls pauses the shooter so another player can resume it.
- English, Turkish, German, and Spanish localization, including the shooter's own HUD.
- Separate Fabric and Forge builds for Minecraft 1.20.1 and 1.21.1.

## Compatibility

| Minecraft | Loader | Java for playing | Loader/API versions used by the build |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | 17+ | Fabric Loader 0.16.10, Fabric API 0.92.2+1.20.1 |
| 1.20.1 | Forge | 17+ | Forge 47.4.10 |
| 1.21.1 | Fabric | 21+ | Fabric Loader 0.16.10, Fabric API 0.116.2+1.21.1 |
| 1.21.1 | Forge | 21+ | Forge 52.1.14 |

Use the JAR that matches both your Minecraft version and your loader. In multiplayer, install the matching mod version on the server and on every participating client. Fabric installations also need Fabric API.

## Installation

1. Install the appropriate Fabric or Forge version for your Minecraft instance.
2. Download the matching `doommc-<loader>-<minecraft>-1.0.0.jar` from [Releases](https://github.com/kadirmt/DoomMC/releases), or build it from source.
3. Copy the mod JAR into the instance's `mods` folder.
4. For Fabric, install the matching Fabric API separately in the same folder.
5. Launch Minecraft and find the screen and remote in the **DoomMC** creative tab, or craft them in survival.

## How to play

1. Clear a **25-block-wide, 16-block-tall** area for the screen.
2. Use the **Giant Screen** item on a surface. The clicked position becomes the middle of the screen's bottom row, and the screen faces you.
3. Right-click any part of the screen with the **Doomplayer Remote** to pair it.
4. Stand where you can see the screen and right-click the paired remote in the air to take control.
5. Press **Enter** or fire to start the shooter.
6. Press **F10** to release the controls and pause the shooter. Sneak + right-click with the paired remote to shut down the session.

Minecraft keeps running while you play, including in singleplayer. The shooter session stays in memory while paused, expires after 30 minutes without a controller, and is cleared when the server stops.

### Controls

| Action | Input |
| --- | --- |
| Move forward / backward | W / S or Up / Down |
| Strafe left / right | A / D or Q / E |
| Turn | Mouse or Left / Right arrows |
| Fire | Left mouse button or Ctrl |
| Open a door | Space |
| Run | Shift |
| Select pistol / shotgun / chaingun | 1 / 2 / 3, after acquiring the weapon |
| Start / retry / continue | Enter or fire |
| Release controls and pause the shooter | F10 |

### Crafting

**Giant Screen** — 5 glass, 1 redstone dust, 3 iron ingots:

```text
G G G
G R G
I I I
```

**Doomplayer Remote** — 1 stone button, 1 redstone dust, 1 iron ingot, in one vertical column:

```text
B
R
I
```

`G` = glass, `R` = redstone dust, `I` = iron ingot, `B` = stone button.

## Build from source

Use **JDK 21** to build the complete workspace, including the Minecraft 1.21.1 modules. The 1.20.1 modules compile with Java 17 bytecode. Gradle **8.10.2** is provided through the wrapper; a separate Gradle installation is unnecessary. The first build requires access to the dependency repositories.

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```sh
./gradlew build
```

To build a single target, for example Fabric 1.20.1:

```sh
./gradlew :fabric-1.20.1:build
```

Each module writes its distributable JAR to `<module>/build/libs/`:

| Module | Output |
| --- | --- |
| `fabric-1.20.1` | `doommc-fabric-1.20.1-1.0.0.jar` |
| `fabric-1.21.1` | `doommc-fabric-1.21.1-1.0.0.jar` |
| `forge-1.20.1` | `doommc-forge-1.20.1-1.0.0.jar` |
| `forge-1.21.1` | `doommc-forge-1.21.1-1.0.0.jar` |

## Project layout

```text
common/src/main/java/       Shared shooter engine, maps, generated art, localization, snapshots
fabric-1.20.1/              Fabric integration for Minecraft 1.20.1
fabric-1.21.1/              Fabric integration for Minecraft 1.21.1
forge-1.20.1/               Forge integration for Minecraft 1.20.1
forge-1.21.1/               Forge integration for Minecraft 1.21.1
gradle/wrapper/             Gradle wrapper
```

The shared engine has no Minecraft dependency. Each loader module compiles it alongside its own screen placement, remote item, networking, and world rendering code. The server runs the simulation and sends state snapshots; clients render the screen locally.

Local Minecraft worlds, logs, Gradle caches, generated builds, and the original `kurulum/` installation bundles are excluded from version control. Downloadable mod binaries belong in Releases.

## License and credits

Created by **akadi**. The mod metadata declares the **MIT License**; see [LICENSE](LICENSE).

DoomMC is an independent fan project inspired by DOOM. The shooter generates its own artwork and uses Minecraft sound events. It is not affiliated with id Software, Mojang, or Microsoft.
