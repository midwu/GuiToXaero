# GuiToXaero

A client-side Fabric mod for Minecraft 1.21.11 that reads a server's
home-listing GUI (the typical Essentials-style `/homes` chest menu), turns
it into a searchable in-game menu, and creates/updates matching waypoints
in **Xaero's World Map / Minimap**, in a dedicated **"Generated Homes"**
waypoint set, split by dimension, without creating duplicates.

Built against:
- Minecraft 1.21.11
- Xaero's Minimap 26.5.0
- Xaero's World Map 1.46.0

## No automated server communication

This mod **never sends anything to the server on its own**. It only does
two things:

- **Reads** whatever is already being shown to you in an open GUI - the
  same slot data Minecraft already gave your client to render the screen.
  It does not click, does not send packets, does not do anything the
  server can see happening.
- **Sends a command**, and only when *you* click the "Go" button in the
  homes menu (or type `/gtx sync` etc. yourself) - i.e. exactly the same
  as if you'd typed `/home name` at the keyboard yourself.

Turning pages in the homes GUI is always done by **your own mouse clicks**,
exactly like without the mod installed - the mod just watches what's
already on screen and remembers it. It does not auto-click "next page",
does not auto-open GUIs, and does not poll or interact with the server in
any way on its own.

Writing/updating Xaero waypoints is pure local file I/O (Xaero stores
waypoints under `.minecraft/XaeroWaypoints/` on your own computer) - it
never touches the network either.

## How it works

1. Open the server's homes GUI as normal (e.g. `/homes`).
2. Press your **scan key** (bind it in *Options > Controls > GuiToXaero*,
   unbound by default so it can't collide with anything) to start
   recording.
3. Manually click through the pages **yourself**, exactly as you normally
   would. Every time the page's contents change because of your click, the
   mod silently notices and captures that page's items too, merging them
   into what it already has.
4. Press the scan key again (or just close the GUI) to stop recording.
   While recording, the mod looks for lore lines shaped like:
   ```
   World: world_the_end
   Location: 779, 57, -779
   ```
   (the standard Essentials/EssentialsX home-menu format - any item
   without both a `World:` and a `Location:` line, like glass-pane filler,
   the page-turn arrows, or the info sign, is ignored automatically).
5. When recording stops, the mod:
   - Saves the full captured home list to disk, per-server, so different
     servers never mix data.
   - Guesses which vanilla dimension each home belongs to from its raw
     world name (`nether` -> Nether, `end`/`the_end` -> End, otherwise
     Overworld - see `DimensionGuesser.java` if your server uses unusual
     world names).
6. Run **`/gtx sync`** when you're ready to create/update the Xaero
   waypoints for everything captured so far. Matching is by name: if a
   waypoint with that name already exists in the "Generated Homes" set for
   that dimension, it's only touched if the coordinates changed (home
   moved = updated in place); otherwise nothing is duplicated. This step
   only writes local Xaero files - still no server communication.
7. Press your **menu key** (or run `/gtx menu`) any time to open a
   searchable, filterable list of all captured homes, with a **Go** button
   per home that runs `/home <name>` for you when you click it.
   `/gtx list` prints the same list to chat. `/gtx clear` forgets the
   captured data for the current server (does not delete any Xaero
   waypoints already created).

## Setup (required before this builds)

Xaero's mods are "All Rights Reserved" and aren't on a public Maven
repository with reliably pinned versions, so this project depends on the
jars directly rather than downloading them automatically.

1. Download, for **Fabric**, Minecraft **1.21.11**:
   - Xaero's Minimap **26.5.0**
   - Xaero's World Map **1.46.0**

   from [Modrinth](https://modrinth.com/mod/xaeros-minimap) /
   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap)
   (or just copy them straight out of your `.minecraft/mods` folder if
   you're already running those exact versions).

2. Put both jars in the `libs/` folder at the project root. The expected
   file names are set in `gradle.properties`:
   ```properties
   xaero_minimap_jar=xaerominimap-fabric-1.21.11-26.5.0.jar
   xaero_worldmap_jar=xaeroworldmap-fabric-1.21.11-1.46.0.jar
   ```
   If the file you downloaded has a different exact name, either rename it
   to match, or edit those two properties.

3. `./gradlew build` (or just let IntelliJ sync - it picks up `libs/*.jar`
   automatically via the `modCompileOnly files(...)` dependency).

4. Run the `Fabric Client` run configuration IntelliJ generates, with
   Xaero's Minimap + World Map also installed in that dev run's mods
   folder (Loom's dev run does not always auto-include
   `modCompileOnly` dependencies at runtime - if the dev client doesn't
   pick them up, copy the same two jars into `run/client/mods/` as well).

## Project layout

```
src/main/java/...Guitoxaero.java                  empty common/server entrypoint (client-only mod)
src/client/java/...client/GuitoxaeroClient.java   entrypoint: keybinds, commands, wiring
src/client/java/...client/home/HomeEntry.java     one parsed home (name/world/dim/coords)
src/client/java/...client/home/DimensionGuesser.java  world-name -> dimension heuristic
src/client/java/...client/home/HomeGuiScanner.java    passive GUI reader (see "No automated server communication")
src/client/java/...client/home/HomeStorage.java       JSON persistence, per server
src/client/java/...client/gui/HomesMenuScreen.java    the standalone homes menu screen
src/client/java/...client/xaero/XaeroWaypointBridge.java  talks to Xaero, dedupes/creates waypoints
src/client/java/...client/mixin/AccessorWaypointSet.java  exposes WaypointSet's internal list
src/client/java/...client/command/GtxCommand.java     /gtx menu|list|sync|clear
```

## Known risk areas / things to double-check in IntelliJ

I don't have a way to compile this against the real Minecraft/Xaero jars in
the environment I wrote it in, so everything below is written from a
verified, current, real reference (Xaero's own open-source addon ecosystem
- see below), not guessed - but since Xaero doesn't publish an official
public API, it's worth a quick look once you've got it building:

- **`XaeroWaypointBridge.java`** - the call chain
  (`BuiltInHudModules.MINIMAP.getCurrentSession()` ->
  `MinimapSession.getWorldManager()` -> `MinimapWorld` ->
  `WaypointSet.add/remove`, plus the `getMinimapWorld(dimension)` lazy
  resolve logic for dimensions you haven't visited yet) was copied from
  real, currently-maintained source: the `WaypointAPI` helper class in
  [rfresh2/XaeroPlus](https://github.com/rfresh2/XaeroPlus) and the
  waypoint-creation pattern in
  [Digital-Twilight/PinPoint](https://github.com/Digital-Twilight/PinPoint).
  Both are addons that hook the exact same Xaero version family this mod
  targets. Everything Xaero-related is wrapped in try/catch and logged
  under `[GuiToXaero]`, so a future Xaero update breaking a method
  signature shows up as a log warning + "skipped" count, not a crash. Note
  this file only ever writes to local Xaero waypoint files - it has no
  network code at all.
- **`AccessorWaypointSet.java`** - assumes the backing field is literally
  named `list`. If Xaero ever renames it, this mixin fails to apply at
  startup with a clear error naming the field - a one-line fix.
- **`HomesMenuScreen.java`** - uses `Screen.remove(Widget)` to rebuild the
  visible "Go" buttons on scroll/filter/search. If your exact mappings
  name this differently, IntelliJ will flag the one call site immediately.
- **`ServerInfo.address`** in `HomeStorage.currentServerKey` - assumed to
  be a public field per current Yarn mappings; flag/fix the same way if
  not.

None of the above affects the actual GUI-reading/parsing logic
(`HomeGuiScanner.java`), which only reads slot/lore data Minecraft already
handed the client (`DataComponentTypes.LORE`, `Slot.getStack()`) and
contains **no click/packet-sending code whatsoever**.

## If your server's GUI format is different

If your server's home GUI isn't Essentials/EssentialsX-formatted (i.e. it
doesn't have `World:`/`Location:` lore lines), nothing will be parsed;
adjust the two regexes at the top of `HomeGuiScanner.java` to match your
server's actual lore format.
