# Carpet-Folia

A hobby port of [gnembon's Fabric Carpet](https://github.com/gnembon/fabric-carpet) to a
**Bukkit plugin for [Folia](https://github.com/PaperMC/Folia)** servers (`1.21.11`),
re-implementing a subset of Carpet's rules using Bukkit/Folia APIs instead of the Fabric
mod loader.

> ### ⚠️ Experimental – use at your own risk
>
> This project is **experimental** and made **for fun / as a hobby**. It is **not intended
> for production use**. It has only been tested against a specific Minecraft version and a
> specific Folia build, and it hooks into internals that can change between versions.
>
> * **Back up your worlds** before running it.
> * Expect bugs, rough edges and missing features.
> * If you find a bug, please open an issue and describe what you did (world/rule/commands,
>   server log, expected vs. actual behaviour).
> * Do **not** blame the original Carpet mod for anything broken here — the bugs are ours.
> * You have been warned. 😄

## What is this?

Carpet is a mod for vanilla Minecraft that lets technical players take control of game
mechanics (rules, debugging tools, redstone behaviour, etc.). The original project lives at
[gnembon/fabric-carpet](https://github.com/gnembon/fabric-carpet) and is written for the
Fabric mod loader.

This repository contains **two source trees**:

| Path          | What it is                                                        |
|---------------|-------------------------------------------------------------------|
| `Fabric/`     | The upstream **gnembon/fabric-carpet** source (Fabric mod).       |
| `Folia-fork/` | **Carpet-Folia** — the Bukkit plugin built from that Carpet code. |

`Folia-fork` reuses Carpet's rule definitions and mixin infrastructure, but the actual
gameplay rules are re-implemented with Bukkit events and Folia's region-threading model so
the plugin can run on stock Folia (which does **not** support Fabric mods or mixins the way
the single-threaded server does).

## Why Folia?

Folia shards the world into region threads instead of one server thread. Anything that
touches the world has to run on the correct region thread. Carpet was written for a
single-threaded server, so every rule had to be rewritten with that in mind:

* world mutations happen inside `Bukkit.getRegionScheduler().execute(...)` region tasks,
* a central tick counter (`CarpetFoliaPlugin.getTick()`) replaces `ServerTickEvent`,
* loaded chunks are tracked via `ChunkLoadEvent`/`ChunkUnloadEvent` (`ChunkRegistry`),
  because Folia does not expose a global `getLoadedChunks()`.

## Implemented rules & features

| Rule                       | Status |
|----------------------------|--------|
| `tntDoNotUpdate`           | Implemented (block is not ignited by redstone at *placement* time; it still ignites if powered later). |
| `mergeTNT`                 | Implemented (merging nearby primed TNT entities). |
| `railPowerLimit`           | Implemented (custom powered-rail limit, including rails that climb steps). |
| `movableBlockEntities`     | Implemented (pistons can push block entities such as chests). |
| `persistentParrots`        | Implemented (parrots no longer pop off shoulders). |
| `stackableShulkerBoxes` / `shulkerBoxStackSize` | Implemented (shulker boxes stack). |
| `pushLimit`                | Implemented (custom piston push limit). |
| `creativeNoClip`           | Implemented. |
| `/log tnt`                 | Implemented (TNT placement logging). |
| `/profile`                 | Implemented. |

Enabled rules are configured with Carpet's usual `/carpet <rule> <value>` command as a
console/admin command on the Folia server.

## Requirements

* A **Folia** 1.21.11 server (the `folia-api` and a `folia-server-1.21.11.jar` for the
  internal classes the plugin compiles against — the latter goes into `Folia-fork/libs/`).
* Java 21.

## Building

```sh
cd Folia-fork
./gradlew shadowJar
```

The plugin jar is produced at:

```
Folia-fork/build/libs/carpet-folia-1.4.194+folia1.21.11.jar
```

The version string mirrors the upstream Carpet version it is based on
(`1.4.194`), suffixed with the Folia target (`folia1.21.11`).

## Installing

1. Stop the server.
2. Put `carpet-folia-1.4.194+folia1.21.11.jar` in the server's `plugins/` folder.
3. Start the server.
4. Enable/configure rules with `/carpet <rule> <value>` as needed.

## License

This project is distributed under the **MIT License** (see `LICENSE`).

Carpet-Folia is a **fork/downport of [gnembon's Fabric Carpet](https://github.com/gnembon/fabric-carpet)**,
which is also MIT-licensed. The original copyright notice must remain intact in all copies
and substantial portions of the software, as required by the MIT license:

> **Carpet Mod** — Copyright (c) 2020 **gnembon** (and Carpet contributors).
> Licensed under the MIT License.

Thank you gnembon for making Carpet free and open-source — this project would not exist
without it. 🧶

**No warranty.** The software is provided "as is", without warranty of any kind. See
`LICENSE` for the full text.