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
    echo "=============== $name.log: error lines ==============="
    grep -n -i -E "error|\*\*\*|undefined reference|cannot|fatal|not found" "$log" | tail -n 40 || true
    echo "=============== $name.log (tail) ==============="
    tail -n 80 "$log" || true
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
# Every finished stage leaves a marker file, so a re-run (or a restored cache) resumes where
# the last run stopped instead of starting from zero.
if [ ! -f "$JNI/.fetched" ]; then
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
  run_step fetch-deps retry 3 ./get_deps.sh
  # Two upstream sources moved away from GitHub (freedesktop.org projects), so get_deps.sh cannot
  # fetch them any more and does not stop on failure. Download release tarballs instead, trying
  # several mirrors, and verify every dependency is really there.
  fetch_tarball() { # fetch_tarball <dir> <check file> <url>...
    local dir="$1" check="$2"; shift 2
    [ -f "$dir/$check" ] && return 0
    rm -rf "$dir"
    local url tmp
    for url in "$@"; do
      echo "Downloading $dir from $url"
      tmp="$(mktemp -d)"
      if wget -q -t 3 -O "$tmp/src.tar" "$url" && tar -xf "$tmp/src.tar" -C "$tmp" \
         && [ -d "$(ls -d "$tmp"/*/ | head -n 1)" ]; then
        mv "$(ls -d "$tmp"/*/ | head -n 1)" "$dir"
        rm -rf "$tmp"
        [ -f "$dir/$check" ] && return 0
      fi
      rm -rf "$tmp" "$dir"
    done
    return 1
  }
  run_step fetch-extra bash -c "
    set -e
    $(declare -f fetch_tarball)
    fetch_tarball pixman pixman/pixman.c \
      https://www.cairographics.org/releases/pixman-0.42.2.tar.gz \
      https://gitlab.freedesktop.org/pixman/pixman/-/archive/pixman-0.42.2/pixman-pixman-0.42.2.tar.gz \
      https://deb.debian.org/debian/pool/main/p/pixman/pixman_0.42.2.orig.tar.gz
    fetch_tarball uchardet src/CharDistribution.cpp \
      https://www.freedesktop.org/software/uchardet/releases/uchardet-0.0.8.tar.xz \
      https://gitlab.freedesktop.org/uchardet/uchardet/-/archive/v0.0.8/uchardet-v0.0.8.tar.gz \
      https://deb.debian.org/debian/pool/main/u/uchardet/uchardet_0.0.8.orig.tar.bz2
  "
  for need in libogg/include libvorbis/include libtheora libiconv pixman/pixman/pixman.c uchardet/src/CharDistribution.cpp \
              physfs openal SDL2/include SDL2_image SDL2_ttf SDL2_sound openssl/Configure ruby/configure.ac; do
    [ -e "$need" ] || fail "Dependency source missing after download: $JNI/$need (see build-logs/fetch-deps.log)"
  done
  run_step make-xxd bash -c "cd mkxp-z && chmod +x make_xxd.sh && ./make_xxd.sh"

  # Upstream builds the dependencies with -O0 (debug). Ruby at -O0 is very slow: use -O2.
  sed -i 's/CFLAGS      := -O0/CFLAGS      := -O2/' Makefile
  grep -q -- 'CFLAGS      := -O2' Makefile || fail "Makefile -O2 patch did not apply."
  # 16 KB page support for the shared libraries built here (Ruby, OpenAL).
  sed -i 's/^\(LDFLAGS *:= .*\)$/\1 -Wl,-z,max-page-size=16384/' Makefile

  # Ruby's "make" also builds hundreds of C-API test extensions (spec/ruby/optional/capi/ext/*.c)
  # when Ruby is shared. We don't need them and one fails when cross-compiling: remove the sources.
  rm -f ruby/spec/ruby/optional/capi/ext/*.c

  # "make install" would also install Ruby's default gems into the BUILD machine's own gem folder
  # (/var/lib/gems on the CI runner: permission denied). They are not used here (rubygems is
  # disabled), so skip them.
  sed -i 's|--install=all --exclude=doc$|--install=all --exclude=doc --exclude=default-gems --exclude=bundled-gems|' ruby/common.mk
  grep -q -- '--exclude=default-gems' ruby/common.mk || fail "Ruby common.mk install patch did not apply."
  touch .fetched
fi

cd "$JNI"
build_once() { # build_once <name> <make target>
  if [ -f ".done-$1" ]; then echo "$1: already built (cached)"; return; fi
  run_step "build-$1" make "$2"
  touch ".done-$1"
}
# One dependency at a time (each already builds with all cores): easy-to-read errors.
build_once iconv   iconv
build_once openal  openal
build_once openssl openssl
build_once ruby    ruby

# Sanity check: the libraries the Android build links against must exist. The unversioned
# .so name is just a symlink; recreate it if the install only left the versioned file.
for f in libruby.so libopenal.so; do
  if [ ! -e "build-arm64-v8a/lib/$f" ]; then
    versioned="$(ls build-arm64-v8a/lib/$f.* 2>/dev/null | sort | tail -n 1 || true)"
    [ -n "$versioned" ] && ln -sf "$(basename "$versioned")" "build-arm64-v8a/lib/$f"
  fi
done
for f in libiconv.a libopenal.so libcrypto.a libssl.a libruby.so; do
  [ -e "build-arm64-v8a/lib/$f" ] || {
    echo "Contents of build-arm64-v8a/lib:"; ls -la build-arm64-v8a/lib | head -40
    fail "Expected build output is missing: build-arm64-v8a/lib/$f"
  }
done
touch .engine-ready
ls -la build-arm64-v8a/lib | grep -E "libiconv|libopenal|libcrypto|libssl|libruby" | tee "$LOGS/engine-libs.txt"

# Our own Application.mk: arm64 only, optimised release build.
cp "$ROOT/native/Application.mk" "$JNI/Application.mk"
echo "Engine ready in $JNI"
