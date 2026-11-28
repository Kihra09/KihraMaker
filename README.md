# Kihra Maker

Android player for RPG Maker XP / VX / **VX Ace** games. Compose launcher on top of
the open-source [mkxp-z](https://github.com/mkxp-z/mkxp-z) engine
(via the Android port by [thehatkid](https://github.com/thehatkid/mkxp-z-android)).
GPL v2+ (see COPYING).

## Features

- Add a game **folder** or a **.zip** file. Zips are played **straight from the zip** (no unpacking) when
  Game.ini is at the top level; otherwise they are unpacked into app storage. A zip game can be unpacked
  later from its page (faster loading for compressed zips); its saves are carried over.
- Library with search, per-game settings (scaling, FPS, frame skip, smoothing, control opacity)
- RTP folder setting for games that need the RPG Maker runtime package
- Library as a list with game icon, title and total play time; swipe right for the **Game assistant**
  (daily play-time chart per game, top games, achievements)
- Gallery (top-left icon) with every in-game screenshot (camera button over the game); export to Pictures
- Controls: button size and opacity, 8-way D-pad, vibration feedback, physical controller support
- Themes (Strawberry, Lime, ...), list / grid / card views, wallpaper in the app and behind the game screen
- In-game toolbar: screenshot, fast forward (up to 2x), cheats (gold, items, switches, variables, heal, walk through walls),
  and a controls editor (move, resize, reshape and re-key the on-screen buttons, saved per game)
- RTP import (zip or folder) in Settings
- Windows-only DLL calls (steam_api...) are skipped so games keep running; achievement-like calls are logged

## Where data lives

Everything the app saves is in `Android/data/kihra.kihramaker.app/files/`:

- `library.json` game list, per-game settings, RTP folder
- `saves/<game>/` saves, `Game.ini` copy and generated `mkxp.json` of games played from a zip
- `games/<game>/` games unpacked from a zip (their saves are inside)
- `playtime.log`, `screenshots/`, `icons/` play time, gallery pictures, cached game icons

Games added as a plain **folder** keep their saves in that folder (that is where the game expects them).

## Build (no PC needed)

Push to GitHub, open Actions > Build APK, download the `KihraMaker-apk` artifact.
The first build takes 20-40 minutes (it compiles Ruby, OpenAL, OpenSSL for arm64);
later builds reuse a cache and are much faster.

## Updating the app

Every build is signed with the same key (app/kihramaker.jks) and gets a higher version number, so a new APK
installs over the old one and keeps your library, saves and settings. (APKs from before this key was added
need one uninstall first.)

## Layout

- `app/src/main/java/com/kihra/maker` launcher UI and glue (Kotlin)
- `app/src/main/java/com/kihra/maker/engine/GameActivity.java` hosts the engine
- `app/src/main/java/org/libsdl`, `com/hatkid/mkxpz` SDL2 Java layer and gamepad (from the port)
- `scripts/build-engine.sh` fetches the pinned port and builds the native engine (one log per step in `build-logs/`)
- `native/Application.mk` arm64, optimised, 16 KB page support

## Notes

- Only arm64-v8a (almost all phones since 2017). 32-bit ARM can be added later.
- Folders and zips on shared storage need "All files access" (the app asks). If you decline, zips are
  unpacked instead, which needs no permission.
- Direct zip play mounts the zip as a PhysFS archive through the engine's `RTP` list: no engine code was
  changed. Zips with Windows-style `\` paths, a top-level folder, or odd compression are unpacked instead.

## Fonts
UI: Fira Sans. Logs/code: M PLUS 1 Code (both SIL OFL, licenses in assets/fonts). Per-game option can use them inside games.

## Gestures
Game > Swipe and tap gestures: tap and 4 swipe directions each press a key.

## Quick check
`quick-check.yml` compiles only Kotlin/Java (~5 min) on every push.

## Supported games
RPG Maker XP, VX, VX Ace (mkxp-z), MV and MZ (WebView, served from a private per-game address).
2000/2003 need EasyRPG (not included yet).

## In-game menu
One movable menu button: screenshot, fast forward (speed chosen in the game's setup), cheats (game-specific list read from the game's database, long-press to pin), quick save/load (VX Ace), edit layout (move/resize/re-key/delete buttons, D-pad <-> joystick).

## Engine patch
scripts/build-engine.sh raises Graphics.frame_rate's cap from 120 to 960 so 4x-16x fast forward is possible.

## Update 12 notes
- MV/MZ games use the same on-screen controls, layout editor, gestures, menu (Screenshot, Fast forward, Edit layout, Exit game), FPS counter and wallpaper as the other games.
- Games that stay on the loading screen show a dialog with the errors the game reported (also in the log).
- Achievements pop up top-left in the launcher and in games; MV/MZ icons and covers come from the game's title picture.
- Update 13: the start-up script is lean (cheat code loads only when used, DLL stubs only for games with their own DLLs); a game that never draws a frame switches to safe mode automatically; live "Loading Ns" status for every game; MV/MZ serve files with range support.
- Game assistant follows game_assistant_spec.pdf: hero card, daily/weekly goals, streak card (5 min/day, one missed day per week forgiven, restore, milestone claims, Sunday weeks), search and status filters, per-game streaks, recently played, favourite, last 12 weeks, not played yet. Game tags, metadata fetch and share-as-picture from the spec are not built yet.
