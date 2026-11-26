# Kihra Maker

Android launcher for RPG Maker VX Ace games (RGSS3). The engine is planned to be
[mkxp](https://github.com/Ancurio/mkxp) (GPL v2+).

## Status

- Library screen: add a game folder, search, cover cards
- Game scanner: reads `Game.ini`, detects VX Ace / VX / XP, shows RTP warning
- Per-game settings: scaling, FPS cap, FPS counter, on-screen control opacity
- Play button starts `GameActivity` (placeholder until the engine is linked)

## Build (no PC needed)

1. Push this folder to GitHub.
2. Open the repo > Actions > "Build APK" > latest run.
3. Download the `KihraMaker-debug-apk` artifact, unzip, install the APK.

## Push from Termux

```sh
pkg install git unzip
cd ~/storage/downloads
unzip KihraMaker.zip -d KihraMaker && cd KihraMaker
git init && git branch -M main
git remote add origin https://github.com/Kihra09/KihraMaker.git
git add . && git commit -m "Add launcher UI"
git push -u origin main
```

## Next steps

1. Add `app/src/main/cpp` with CMake and the mkxp sources, build with the NDK.
2. Port the renderer from desktop OpenGL to OpenGL ES 3.
3. Cross-compile Ruby (MRI), OpenAL Soft, PhysFS, pixman, SDL2 for arm64-v8a.
4. Games are picked through the Storage Access Framework (content URIs), but mkxp
   needs real file paths. Either copy/mount the game or request all-files access.
5. Touch overlay (D-pad, A/B/C, menu) using the opacity setting.
