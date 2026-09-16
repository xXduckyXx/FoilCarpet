# Carpet-Folia

heyo, welcome to my silly little project :3

this is a hobby port of [gnembon's Fabric Carpet](https://github.com/gnembon/fabric-carpet) into a **Bukkit plugin that runs on [Folia](https://github.com/PaperMC/Folia)** servers (`1.21.11`). instead of using the Fabric mod loader, it re-implements a bunch of carpet's rules using Bukkit/Folia APIs. made by a duck who really likes minecraft machines, quack :3

> ### ⚠️ experimental, made for fun, NOT for production!!
>
> okay real talk: this thing is **experimental** and i built it **as a hobby**, just for the fun of it. it is **not meant for production use**. it has only been tested on one specific minecraft version and one specific build of folia, and it pokes around game internals that change between versions. things might explode. :3
>
> * **back up your worlds!!** seriously, make backups before you run this on anything you care about.
> * expect bugs, weird behaviour, and missing features. if something breaks, that's on me, not on carpet <3
> * if you find a bug, please open an issue and tell me what you did (rule, commands, server log, what you expected vs what actually happened). screenshots help a lot too!
> * please don't blame gnembon for anything broken here, the bugs are all mine :3 o7

## what is this?

so, carpet is a mod that lets technical minecraft people take control of game mechanics: rules, debug tools, redstone behaviour, all that jazz :3 the original lives at [gnembon/fabric-carpet](https://github.com/gnembon/fabric-carpet) and is built for the Fabric mod loader.

this repo has **two source trees**:

| path          | what it is                                                        |
|---------------|-------------------------------------------------------------------|
| `Fabric/`     | the upstream **gnembon/fabric-carpet** source (the fabric mod).   |
| `Folia-fork/` | **Carpet-Folia** :3 the bukkit plugin built from that carpet code |

`Folia-fork` reuses carpet's rule definitions and mixin setup, but the actual gameplay rules are re-implemented with bukkit events and folia's region threading, so the plugin can run on stock folia (which can't run fabric mods the way a normal single-threaded server can).

## why folia??

folia splits the world into region threads instead of using one single server thread. that means anything touching the world has to run on the correct region thread, which is why carpet (written for a single thread) had to be rewritten:

* world mutations happen inside `Bukkit.getRegionScheduler().execute(...)` region tasks,
* a central tick counter (`CarpetFoliaPlugin.getTick()`) replaces `ServerTickEvent`,
* loaded chunks are tracked via `ChunkLoadEvent` / `ChunkUnloadEvent` (`ChunkRegistry`), because folia doesn't give you a global `getLoadedChunks()`.

## implemented rules & features

| rule                        | status |
|-----------------------------|--------|
| `tntDoNotUpdate`            | implemented (block is not ignited by redstone at *placement* time, but still ignites if it gets powered later) |
| `mergeTNT`                  | implemented (nearby primed TNT merges together) |
| `railPowerLimit`            | implemented (custom powered-rail limit, even across steps! took forever to figure that one out :3) |
| `movableBlockEntities`      | implemented (pistons can push block entities like chests) |
| `persistentParrots`         | implemented (parrots stop hopping off your shoulder) |
| `stackableShulkerBoxes` / `shulkerBoxStackSize` | implemented (shulker boxes can stack) |
| `pushLimit`                 | implemented (custom piston push limit) |
| `creativeNoClip`            | implemented |
| `/log tnt`                  | implemented (TNT placement logging) |
| `/profile`                  | implemented |

you configure rules the usual carpet way, with `/carpet <rule> <value>` from console or as an admin on the server :3

## requirements

* a **Folia** 1.21.11 server (the plugin compiles against `folia-api` plus a `folia-server-1.21.11.jar` that goes in `Folia-fork/libs/`).
* java 21.

## building

```sh
cd Folia-fork
./gradlew shadowJar
```

the jar lands at:

```
Folia-fork/build/libs/carpet-folia-1.4.194+folia1.21.11.jar
```

the version is the upstream carpet version it's based on (`1.4.194`) with the folia target tacked on (`folia1.21.11`).

## installing

1. stop the server.
2. drop `carpet-folia-1.4.194+folia1.21.11.jar` into the server's `plugins/` folder.
3. start the server.
4. configure rules with `/carpet <rule> <value>` as needed :3

## support me :3

if you like this silly plugin and wanna say thanks, ko-fi me a coffee maybe? :3

https://ko-fi.com/xxduckyxx

any support means a lot to me, but no pressure. this is a hobby after all <3

## license

this project is under the **MIT License** (see `LICENSE`).

Carpet-Folia is a **fork/downport of [gnembon's Fabric Carpet](https://github.com/gnembon/fabric-carpet)**, which is also MIT licensed. the MIT license requires that the original copyright notice stays intact in all copies and substantial portions of the software:

> **Carpet Mod** Copyright (c) 2020 **gnembon** (and Carpet contributors).
> Licensed under the MIT License.

thank you gnembon for making carpet free and open source, this project literally wouldn't exist without you :3 🧶

**no warranty.** the software is provided "as is", without warranty of any kind. check `LICENSE` for the full text :3