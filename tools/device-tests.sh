#!/usr/bin/env bash
# Runs the device tests (app/src/androidTest) on one or two emulators at once. With two, each runs
# half the tests (AndroidJUnitRunner's sharding), so the whole suite takes about half as long.
#
#   tools/device-tests.sh                    # the whole suite
#   tools/device-tests.sh --class com.mtwabpin.kolahal.ui.ScriptSheetTest,com.mtwabpin.kolahal.data.LogMarksTest
#   tools/device-tests.sh --emulators 1      # one emulator, when memory is short
#   tools/device-tests.sh --keep             # leave the emulators running: the next run starts sooner
#
# - Emulators only, never a phone: the tests install a debug build and change its settings.
# - How many: --emulators 1|2, or by default 2 when 12 GB or more is free (each needs about 4 GB),
#   else 1. An emulator already running (emulator-5554, emulator-5556) is used. For two, one that
#   runs without -read-only is restarted read-only first: two copies of an AVD need it.
# - It starts the missing ones from the kolahal_test AVD, read-only, so two can run from one AVD and
#   nothing a test does is kept. It stops the ones it started, unless --keep.
# - Window animations are off, as in Gradle's runs (testOptions.animationsDisabled).
# - Exit status 0 only when every test passed. Each emulator's output is in app/build/device-tests/.
set -euo pipefail
cd "$(dirname "$0")/.."

SDK=${ANDROID_HOME:-$HOME/.apps/Android/SDK}
ADB=${ADB:-adb}
AVD=kolahal_test
TEST_PACKAGE=com.mtwabpin.kolahal.debug.test
RUNNER=androidx.test.runner.AndroidJUnitRunner
OUT=app/build/device-tests

emulators=""
classes=""
keep=false
while [ $# -gt 0 ]; do
  case "$1" in
    --emulators) emulators=$2; shift 2 ;;
    --class) classes=$2; shift 2 ;;
    --keep) keep=true; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "Unknown option $1 (see --help)" >&2; exit 2 ;;
  esac
done
if [ -z "$emulators" ]; then
  free_gb=$(awk '/^MemAvailable:/ { print int($2 / 1048576) }' /proc/meminfo)
  emulators=$([ "$free_gb" -ge 12 ] && echo 2 || echo 1)
  echo "Free memory: ${free_gb} GB, so $emulators emulator(s)."
fi
case "$emulators" in 1|2) ;; *) echo "--emulators takes 1 or 2" >&2; exit 2 ;; esac

serials=()
for i in $(seq 0 $((emulators - 1))); do serials+=("emulator-$((5554 + 2 * i))"); done
mkdir -p "$OUT"
start=$(date +%s)

booted() { [ "$("$ADB" -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; }

# Two copies of one AVD run only when both are read-only. A copy started without -read-only (by hand,
# or by Gradle) is stopped here and started again below, read-only. It's a test AVD: nothing is lost.
if [ "$emulators" -gt 1 ]; then
  for pid in $(pgrep -f "qemu-system.*-avd $AVD" || true); do
    if ! tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q -- ' -read-only'; then
      echo "An emulator of $AVD runs without -read-only: restarting it read-only, so two can run."
      kill "$pid" 2>/dev/null || true
      for _ in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      sleep 3
    fi
  done
fi

# Start the emulators that aren't running, then build while they boot.
started=()
for s in "${serials[@]}"; do
  if booted "$s"; then continue; fi
  port=${s#emulator-}
  echo "Starting $s…"
  nohup "$SDK/emulator/emulator" -avd "$AVD" -port "$port" -read-only -no-window -no-audio \
    -no-boot-anim -no-snapshot-load > "$OUT/$s.log" 2>&1 &
  started+=("$s")
done
stop_started() {
  $keep && return 0
  for s in "${started[@]}"; do "$ADB" -s "$s" emu kill > /dev/null 2>&1 || true; done
}
trap stop_started EXIT

echo "Building the debug app and its tests…"
./gradlew --no-daemon -q :app:assembleDebug :app:assembleDebugAndroidTest
app_apk=app/build/outputs/apk/debug/app-debug.apk
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

for s in "${serials[@]}"; do
  for _ in $(seq 1 90); do booted "$s" && break; sleep 4; done
  booted "$s" || { echo "$s didn't start; see $OUT/$s.log" >&2; exit 1; }
done
# A freshly booted emulator is busy for a while (package scans, dexopt): let it settle.
[ ${#started[@]} -gt 0 ] && sleep 30

echo "Installing on ${serials[*]}…"
for s in "${serials[@]}"; do
  ( "$ADB" -s "$s" install -r -t "$app_apk" > /dev/null && "$ADB" -s "$s" install -r -t "$test_apk" > /dev/null ) &
done
wait

echo "Running the tests on $emulators emulator(s)…"
pids=()
for i in "${!serials[@]}"; do
  s=${serials[$i]}
  args=(-w --no-window-animation -e numShards "$emulators" -e shardIndex "$i")
  [ -n "$classes" ] && args+=(-e class "$classes")
  ( t0=$(date +%s)
    "$ADB" -s "$s" shell am instrument "${args[@]}" "$TEST_PACKAGE/$RUNNER" > "$OUT/shard-$i.txt" 2>&1 || true
    echo "$(( $(date +%s) - t0 ))" > "$OUT/shard-$i.seconds" ) &
  pids+=($!)
done
for p in "${pids[@]}"; do wait "$p"; done

# Each shard ends with "OK (n tests)" or "Tests run: n,  Failures: m"; anything else is a crash.
total=0; failed=0; broken=false
for i in "${!serials[@]}"; do
  f="$OUT/shard-$i.txt"
  ran=$(sed -n 's/^OK (\([0-9]*\) tests\?)\r\?$/\1/p; s/^Tests run: \([0-9]*\),.*/\1/p' "$f" | tail -1)
  fails=$(sed -n 's/^Tests run: [0-9]*,  Failures: \([0-9]*\).*/\1/p' "$f" | tail -1)
  echo "${serials[$i]}: ${ran:-?} tests, ${fails:-0} failed, $(cat "$OUT/shard-$i.seconds")s"
  if [ -z "$ran" ]; then broken=true; echo "  It didn't finish. The end of its output:"; tail -15 "$f" | sed 's/^/    /'; fi
  total=$((total + ${ran:-0})); failed=$((failed + ${fails:-0}))
  [ "${fails:-0}" -gt 0 ] && sed -n '/^There w[a-z]* [0-9]* failure/,/^FAILURES!!!/p' "$f" | grep -v '^\s*at androidx\|^\s*at java\|^\s*at org.junit\|^\s*at android\.' | head -60
done
echo "In all: $total tests, $failed failed, $(( $(date +%s) - start ))s from start to end."
if $broken || [ "$failed" -gt 0 ]; then exit 1; fi
