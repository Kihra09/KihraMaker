#!/usr/bin/env bash
# Fetches the open-source Android port of mkxp-z, downloads its dependencies and
# cross-compiles them (OpenSSL, Ruby, OpenAL, ...) for arm64 with the Android NDK.
#
# Needs ANDROID_NDK_HOME (NDK 23.2.8568313) and: build-essential cmake xxd autoconf
# automake libtool libtool-bin pkg-config ruby bison wget git
#
# Every step writes its own log into build-logs/ and the failing step's log tail is
# printed, so the real error is always visible (not buried under parallel output).
set -euo pipefail

PORT_REPO="https://github.com/thehatkid/mkxp-z-android.git"
PORT_COMMIT="468fe8128a40cc7d2cba4ac7fbe21a82787de255"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JNI="$ROOT/app/jni"
LOGS="$ROOT/build-logs"
mkdir -p "$LOGS"

# ---------- pre-flight checks ----------
fail() { echo "::error::$*"; exit 1; }

[ -n "${ANDROID_NDK_HOME:-}" ] || fail "ANDROID_NDK_HOME is not set (need NDK 23.2.8568313)."
[ -f "$ANDROID_NDK_HOME/source.properties" ] || fail "ANDROID_NDK_HOME=$ANDROID_NDK_HOME is not an NDK folder."

ARCH=linux-x86_64
TOOLCHAIN_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$ARCH/bin"
[ -x "$TOOLCHAIN_BIN/aarch64-linux-android21-clang" ] || fail "NDK clang not found in $TOOLCHAIN_BIN"

missing=""
# (Ubuntu's "libtool" package ships libtoolize; the "libtool" command itself is in libtool-bin.)
for tool in git wget make cmake xxd autoconf automake libtoolize libtool pkg-config ruby bison; do
  command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"
done
[ -z "$missing" ] || fail "Missing build tools:$missing"

# OpenSSL (and others) look for the NDK compiler on PATH and for these variables.
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export ANDROID_NDK="$ANDROID_NDK_HOME"
export PATH="$TOOLCHAIN_BIN:$PATH"
export ARCH HOST=aarch64-linux-android TARGET=aarch64-linux-android ABI=arm64-v8a

# ---------- step runner ----------
run_step() {
  local name="$1"; shift
  local log="$LOGS/$name.log"
  echo "::group::$name"
  echo "Running: $*"
  if "$@" >"$log" 2>&1; then
    echo "$name: ok"
    echo "::endgroup::"
  else
    local code=$?
    echo "::endgroup::"
    echo "::error::Step '$name' failed (exit $code). Last lines of its log:"
    echo "=============== $name.log (tail) ==============="
    tail -n 120 "$log" || true
    echo "================================================"
    # configure scripts keep the real reason in config.log
    for cfg in $(find "$JNI" -maxdepth 3 -name config.log -newer "$LOGS/.start" 2>/dev/null | head -3); do
      echo "--- tail of $cfg ---"
      tail -n 40 "$cfg" || true
    done
    exit "$code"
  fi
}

retry() { # retry <times> <command...>
  local n="$1"; shift
  local i
  for i in $(seq 1 "$n"); do
    "$@" && return 0
    echo "Attempt $i/$n failed: $*"
    sleep 5
  done
  return 1
}

touch "$LOGS/.start"

# ---------- fetch sources + dependencies ----------
if [ ! -f "$JNI/.engine-ready" ]; then
  rm -rf "$JNI"
  TMP="$(mktemp -d)"
  run_step fetch-port bash -c "
    set -e
    git clone -q '$PORT_REPO' '$TMP/port'
    git -C '$TMP/port' -c advice.detachedHead=false checkout -q '$PORT_COMMIT'
    cp -r '$TMP/port/app/jni' '$JNI'
  "
  rm -rf "$TMP"

  cd "$JNI"
  chmod +x get_deps.sh
  # get_deps.sh skips folders that already exist, so a retry only fetches what is missing.
  export -f retry
  run_step fetch-deps retry 3 ./get_deps.sh
  run_step make-xxd bash -c "cd mkxp-z && chmod +x make_xxd.sh && ./make_xxd.sh"

  # Upstream builds the dependencies with -O0 (debug). Ruby at -O0 is very slow: use -O2.
  sed -i 's/CFLAGS      := -O0/CFLAGS      := -O2/' Makefile
  grep -q -- 'CFLAGS      := -O2' Makefile || fail "Makefile -O2 patch did not apply."
  # 16 KB page support for the shared libraries built here (Ruby, OpenAL).
  sed -i 's/^\(LDFLAGS *:= .*\)$/\1 -Wl,-z,max-page-size=16384/' Makefile

  # One dependency at a time (each already builds with all cores): easy-to-read errors.
  run_step build-iconv   make iconv
  run_step build-openal  make openal
  run_step build-openssl make openssl
  run_step build-ruby    make ruby

  # Sanity check: the libraries the Android build links against must exist.
  for f in lib/libruby.so lib/libopenal.so lib/libssl.a lib/libiconv.a; do
    [ -e "build-arm64-v8a/$f" ] || [ -e "build-arm64-v8a/${f%.so}.so.3.1.0" ] \
      || fail "Expected build output is missing: build-arm64-v8a/$f"
  done

  touch .engine-ready
fi

# Our own Application.mk: arm64 only, optimised release build.
cp "$ROOT/native/Application.mk" "$JNI/Application.mk"
echo "Engine ready in $JNI"
