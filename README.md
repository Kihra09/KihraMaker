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
- On-screen gamepad, physical controller support

## Where data lives

Everything the app saves is in `Android/data/kihra.kihramaker.app/files/`:

- `library.json` game list, per-game settings, RTP folder
- `saves/<game>/` saves, `Game.ini` copy and generated `mkxp.json` of games played from a zip
- `games/<game>/` games unpacked from a zip (their saves are inside)

Games added as a plain **folder** keep their saves in that folder (that is where the game expects them).

## Build (no PC needed)

Push to GitHub, open Actions > Build APK, download the `KihraMaker-apk` artifact.
The first build takes 20-40 minutes (it compiles Ruby, OpenAL, OpenSSL for arm64);
later builds reuse a cache and are much faster.

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
