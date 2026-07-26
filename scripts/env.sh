#!/usr/bin/env bash
# Source this before running Gradle: `source scripts/env.sh`
# Both the JDK and the Android SDK are Homebrew installs that are not on PATH by default.

export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "ERROR: JDK not found at $JAVA_HOME — run: brew install openjdk@21" >&2
  return 1 2>/dev/null || exit 1
fi
if [ ! -d "$ANDROID_HOME/platforms" ]; then
  echo "WARN: no Android platforms installed — run: sdkmanager 'platforms;android-36'" >&2
fi

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
