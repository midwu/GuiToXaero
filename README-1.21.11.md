# GuiToXaero (Minecraft 1.21.11, Fabric)

## Setup
1. Put BOTH Xaero jars (Fabric, Minecraft 1.21.11) in `libs/`: Xaero's Minimap and Xaero's World Map
   (any file names; copy them from your real mods folder). They are compile/dev-run only and git-ignored.
2. Run `runClient`. Bind keys in Options > Controls > Key Binds > GuiToXaero.
3. Build the distributable jar: `gradlew.bat build` -> `build/libs/guitoxaero-1.0-SNAPSHOT.jar`
   (put it in your real `mods` folder next to the real Xaero jars).

## Use
- Open a homes GUI, press the SCAN key (starts recording), page through it yourself, press SCAN again.
- `/gtx sync` -> creates/updates waypoints in the "Generated Homes" set (per dimension, no duplicates by name).
- `/gtx menu` (or the menu key) -> search/filter list with a Go button that runs `/home <name>`.
- `/gtx list`, `/gtx clear`.

The mod never clicks or sends anything by itself. The only command it sends is `/home <name>`, when you click Go.
Log lines are prefixed `[GuiToXaero]`.
