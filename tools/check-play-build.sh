#!/usr/bin/env bash
# Checks that the Google Play build leaves out what Play's policy rules out, or would need a
# declaration and a video for. A library can add a permission without anyone
# noticing; this catches it before Play does. What it leaves out, and why:
# docs/release/google-play.org.
#
#   ./gradlew :app:assemblePlay && tools/check-play-build.sh [apk]
#
# The APK defaults to app/build/outputs/apk/play/app-play-unsigned.apk (unsigned without an upload
# key; the manifest is the same).
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
apk=${1:-$root/app/build/outputs/apk/play/app-play-unsigned.apk}
if [ ! -f "$apk" ]; then for f in "$root"/app/build/outputs/apk/play/*.apk; do apk=$f; break; done; fi
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk\.dir=//p' "$root/local.properties" 2>/dev/null)}}
# shellcheck disable=SC2012  # build-tools versions are plain numbers
aapt2=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)/aapt2

permissions=$("$aapt2" dump permissions "$apk")
manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")
problems=0

# Permissions Play keeps for default phone, SMS or assistant apps, or for a few declared uses
# (contacts: where Android's contact picker won't do; the local network: where a device picker won't).
for p in READ_CALL_LOG WRITE_CALL_LOG PROCESS_OUTGOING_CALLS READ_SMS SEND_SMS RECEIVE_SMS RECEIVE_MMS \
         READ_CONTACTS WRITE_CONTACTS QUERY_ALL_PACKAGES USE_EXACT_ALARM REQUEST_INSTALL_PACKAGES MANAGE_EXTERNAL_STORAGE \
         ACCESS_BACKGROUND_LOCATION ACCESS_LOCAL_NETWORK; do
    if grep -q "name='android.permission.$p'" <<<"$permissions"; then
        echo "$(basename "$apk") asks for $p."
        problems=1
    fi
done
# A service Play wants a declaration, consent screens and a video for: an accessibility service (a
# launcher is not an accessibility tool). The notification listener may stay: Kolahal asks first.
for s in BIND_ACCESSIBILITY_SERVICE; do
    if grep -q "\"android.permission.$s\"" <<<"$manifest"; then
        echo "$(basename "$apk") has a service bound by $s."
        problems=1
    fi
done

# An app on Play may update itself only through Play: the updater's code must be gone, not just switched off.
for c in androidx.core.content.FileProvider com.mtwabpin.kolahal.terminal.UpdateCleanupReceiver; do
    if grep -qF "\"$c\"" <<<"$manifest"; then
        echo "$(basename "$apk") declares $c, part of the self-updater."
        problems=1
    fi
done
dex=$(mktemp)
trap 'rm -f "$dex"' EXIT
unzip -p "$apk" 'classes*.dex' > "$dex"
if [ ! -s "$dex" ]; then
    echo "$(basename "$apk") has no code to check."
    problems=1
elif grep -aq 'repos/mtwabpin/kolahal/releases' "$dex"; then
    echo "$(basename "$apk") has the self-updater's code in it."
    problems=1
fi
# The Assistant (BuildConfig.ASSISTANT, false in play) sends what you type to AI services: its code and A2UI must
# be gone, not just switched off. Its services' hosts, the models of their lists (read from assistant/, so a new
# one is checked too) and A2UI's packages.
if [ -s "$dex" ]; then
    models=$(sed -n 's/.*Model("[^"]*", "\([^"]*\)".*/\1/p' "$root"/app/src/main/java/com/mtwabpin/kolahal/assistant/*.kt 2>/dev/null || true)
    found=""
    for s in api.anthropic.com generativelanguage.googleapis.com api.openai.com $models androidx/a2ui material3/a2ui; do
        if grep -aqF "$s" "$dex"; then found="$found $s"; fi
    done
    if [ -n "$found" ]; then
        echo "$(basename "$apk") has the Assistant's code in it:$found."
        problems=1
    else
        echo "$(basename "$apk") leaves out the Assistant."
    fi
fi
# A one-off shader over other apps: SYSTEM_ALERT_WINDOW and the window that uses it (ui/shaders/ShaderOverlay.kt,
# found by its name) come together or not at all. The Play build has both; SHADER_OVER_APPS in app/build.gradle.kts
# says how to take both out if Google refuses it. One without the other means the switch and the manifest disagree.
if [ -s "$dex" ]; then
    asks=0; window=0
    if grep -q "name='android.permission.SYSTEM_ALERT_WINDOW'" <<<"$permissions"; then asks=1; fi
    if grep -aq 'Kolahal one-off shader' "$dex"; then window=1; fi
    if [ "$asks" != "$window" ]; then
        if [ "$asks" = 1 ]; then echo "$(basename "$apk") asks for SYSTEM_ALERT_WINDOW but has no window for shaders over other apps.";
        else echo "$(basename "$apk") has the window for shaders over other apps but doesn't ask for SYSTEM_ALERT_WINDOW."; fi
        problems=1
    elif [ "$asks" = 1 ]; then
        echo "$(basename "$apk") asks for SYSTEM_ALERT_WINDOW, for one-off shaders over other apps (off until turned on)."
    fi
fi

if [ "$problems" = 0 ]; then echo "$(basename "$apk") leaves out every restricted permission and service."; fi
exit "$problems"
