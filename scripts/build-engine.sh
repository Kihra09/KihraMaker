#!/usr/bin/env bash
# Fetches the open-source Android port of mkxp-z, downloads its dependencies and
# cross-compiles them (Ruby, OpenAL, OpenSSL, ...) for arm64 with the Android NDK.
# Needs: ANDROID_NDK_HOME set, plus build-essential cmake xxd autoconf automake libtool
#        pkg-config ruby bison wget git.
set -euo pipefail

PORT_REPO="https://github.com/thehatkid/mkxp-z-android.git"
PORT_COMMIT="468fe8128a40cc7d2cba4ac7fbe21a82787de255"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNI="$ROOT/app/jni"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point to NDK 23.2.8568313}"

if [ ! -f "$JNI/.engine-ready" ]; then
  rm -rf "$JNI"
  TMP="$(mktemp -d)"
  git clone -q "$PORT_REPO" "$TMP/port"
  git -C "$TMP/port" -c advice.detachedHead=false checkout -q "$PORT_COMMIT"
  cp -r "$TMP/port/app/jni" "$JNI"
  rm -rf "$TMP"

  cd "$JNI"
  chmod +x get_deps.sh
  ./get_deps.sh
  (cd mkxp-z && chmod +x make_xxd.sh && ./make_xxd.sh)

  # Dependencies are built with -O0 upstream (debug). Ruby at -O0 is very slow, use -O2.
  sed -i 's/CFLAGS      := -O0/CFLAGS      := -O2/' Makefile
  grep -q -- '-O2' Makefile || { echo "Makefile patch failed"; exit 1; }
  # 16 KB page support for the shared libraries built here (Ruby, OpenAL).
  sed -i 's/^\(LDFLAGS *:= .*\)$/\1 -Wl,-z,max-page-size=16384/' Makefile

  HOST=aarch64-linux-android TARGET=aarch64-linux-android ABI=arm64-v8a \
  ARCH=linux-x86_64 \
  make -j"$(nproc)"

  touch .engine-ready
fi

# Our own Application.mk: arm64 only, optimised release build.
cp "$ROOT/native/Application.mk" "$JNI/Application.mk"
echo "Engine ready in $JNI"
