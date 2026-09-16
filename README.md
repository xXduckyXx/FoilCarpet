# FoilCarpet

Folia bukkit plugin for 1.21.11 that ports some of the carpet mod rules to a stock folia server

-----------------------------

a port of gnembon's fabric carpet into a bukkit plugin

re-implements the carpet rules with events and folia region threading instead of fabric mixins

runs on stock folia, no fabric loader needed

rules are set the classic way with /carpet

implements: tntDoNotUpdate, mergeTNT, railPowerLimit, movableBlockEntities, persistentParrots, stackableShulkerBoxes, pushLimit, creativeNoClip, /log tnt, /profile

-----------------------------

**how to install:**

drop the jar in the plugins folder

restart server

done

**requires:**

folia 1.21.11 (needs folia's region threads, normal paper wont work)

java 21

**does it have all carpet rules?**

no, its a small subset, check the list above

**does it work on normal paper?**

no, it needs folia

**commands:**

/carpet <rule> <value> - sets a carpet rule (console or admin)

/carpet <rule> - shows the current value

/profile - the carpet profiler thing

/log tnt - logs tnt placements

**config:**

rules are not stored in config.yml

use /carpet, and if you want a rule to survive a restart answer yes to the "[Change permanently?]" prompt, otherwise every restart resets it to the default

**permissions:**

no permission nodes, /carpet is console/admin only

-----------------------------

**how it works (nerd stuff):**

folia splits the world into region threads, so anything touching the world has to run inside a region task

a central tick counter replaces the old server tick event

loaded chunks are tracked manually because folia hides the global chunk list

tntDoNotUpdate remembers where you placed the tnt and cancels redstone ignition from the same tick

railPowerLimit rescans every loaded chunk and rewrites the powered flag of each rail to match the limit, works up steps too

movableBlockEntities, parrots and shulkers hook into bukkit events plus a periodic scan

-----------------------------

**IMPORTANT NOTE**

LISTEN UP

THIS IS AN EXPERIMENTAL HOBBY PROJECT, NOT FOR PRODUCTION

ONLY TESTED ON FOLIA 1.21.11, EXPECT BUGS ON ANYTHING ELSE

BACKUP YOUR WORLD BEFORE USING THIS, YOU HAVE BEEN WARNED

IF YOU FIND A BUG REPORT IT, PREFERABLY WITH THE RULE, THE COMMANDS YOU RAN, AND YOUR SERVER LOG

IF ANYTHING BREAKS ITS MY BUG, NOT CARPET'S, DONT BLAME GNEMBON

IF YOU DONT LIKE IT FORK THE CODE, ITS MIT FOR A REASON

TO REPORT BUGS OPEN AN ISSUE ON THE GITHUB REPO

now go enjoy your carpet

**if you enjoy this mod**

Donate me: ko-fi.com/xxduckyxx

**license:**

MIT, same as the carpet mod this is built on

Carpet is copyright 2020 gnembon and carpet contributors, MIT licensed, and this fork keeps that notice in the LICENSE file as required