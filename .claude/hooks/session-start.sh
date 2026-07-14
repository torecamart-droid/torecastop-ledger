#!/bin/bash
# SessionStart hook — prepares this Android project so a Claude Code on the web
# session can build and lint it.
#
# It installs the Android SDK command-line tools plus the SDK packages the
# Gradle build needs (compileSdk 35 / build-tools 35.0.0), persists ANDROID_HOME
# for the session, points the project at the SDK via local.properties, and warms
# the Gradle dependency cache.
#
# NETWORK REQUIREMENT: the SDK and all Google-hosted Gradle artifacts (the
# Android Gradle Plugin, AndroidX, Compose, Room, CameraX, Material) are served
# from dl.google.com (maven.google.com redirects there). That host MUST be
# allowed by the environment's egress policy or every step below fails. Also
# reachable-by-default and required: services.gradle.org, plugins.gradle.org,
# repo.maven.apache.org.
set -euo pipefail

# Only do the heavy SDK install in the remote (web) environment; locally the
# developer already has Android Studio / an SDK.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"  # commandline-tools 11.0
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"

log() { echo "[session-start] $*"; }

# 1. Android command-line tools ------------------------------------------------
if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "Installing Android command-line tools into $SDK_ROOT"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  tmp_zip="$(mktemp)"
  curl -fsSL -o "$tmp_zip" \
    "https://dl.google.com/android/repository/commandline-tools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  rm -rf "$SDK_ROOT/cmdline-tools/tmp" "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$SDK_ROOT/cmdline-tools/tmp"
  unzip -q "$tmp_zip" -d "$SDK_ROOT/cmdline-tools/tmp"
  mv "$SDK_ROOT/cmdline-tools/tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$SDK_ROOT/cmdline-tools/tmp" "$tmp_zip"
else
  log "Android command-line tools already present"
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

# 2. SDK packages + licenses ---------------------------------------------------
# sdkmanager is idempotent: already-installed packages are skipped.
log "Accepting SDK licenses"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

log "Installing platform-tools, $PLATFORM, $BUILD_TOOLS"
yes | sdkmanager "platform-tools" "$PLATFORM" "$BUILD_TOOLS" >/dev/null

# 3. Point the project + future shells at the SDK ------------------------------
LOCAL_PROPS="$CLAUDE_PROJECT_DIR/local.properties"
if ! grep -qs "^sdk.dir=" "$LOCAL_PROPS" 2>/dev/null; then
  echo "sdk.dir=$SDK_ROOT" > "$LOCAL_PROPS"
  log "Wrote sdk.dir to local.properties"
fi

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_SDK_ROOT=\"$SDK_ROOT\""
    echo "export ANDROID_HOME=\"$SDK_ROOT\""
    echo "export PATH=\"$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:\$PATH\""
  } >> "$CLAUDE_ENV_FILE"
fi

# 4. Warm the Gradle dependency cache -----------------------------------------
# Downloads the Gradle distribution and all project dependencies now, so the
# first real build in the session is fast. Non-fatal if it hiccups.
log "Warming Gradle (downloading dependencies)"
( cd "$CLAUDE_PROJECT_DIR" && ./gradlew --no-daemon :app:dependencies >/dev/null 2>&1 ) \
  && log "Gradle dependencies resolved" \
  || log "Gradle warm-up skipped/failed (non-fatal)"

log "Done. Android SDK at $SDK_ROOT"
