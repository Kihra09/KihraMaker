# Kihra Maker

Android player for RPG Maker XP / VX / **VX Ace** games. Compose launcher on top of
the open-source [mkxp-z](https://github.com/mkxp-z/mkxp-z) engine
(via the Android port by [thehatkid](https://github.com/thehatkid/mkxp-z-android)).
GPL v2+ (see COPYING).

## Features

- Add a game **folder** or a **.zip** file (zips are unpacked into app storage)
- Library with search, per-game settings (scaling, FPS, frame skip, smoothing, control opacity)
- RTP folder setting for games that need the RPG Maker runtime package
- On-screen gamepad, physical controller support

## Build (no PC needed)

Push to GitHub, open Actions > Build APK, download the `KihraMaker-apk` artifact.
The first build takes 20-40 minutes (it compiles Ruby, OpenAL, OpenSSL for arm64);
later builds reuse a cache and are much faster.

## Layout

- `app/src/main/java/com/kihra/maker` launcher UI and glue (Kotlin)
- `app/src/main/java/com/kihra/maker/engine/GameActivity.java` hosts the engine
- `app/src/main/java/org/libsdl`, `com/hatkid/mkxpz` SDL2 Java layer and gamepad (from the port)
- `scripts/build-engine.sh` fetches the pinned port and builds the native engine
- `native/Application.mk` arm64, optimised, 16 KB page support

## Notes

- Only arm64-v8a (almost all phones since 2017). 32-bit ARM can be added later.
- Games from shared storage need "All files access" (the app asks). Zip games don't.
