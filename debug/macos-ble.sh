#!/usr/bin/env bash
# Run open-cardi.py on macOS despite CoreBluetooth's TCC requirement. See README.md
# ("macOS gotcha"). Usage: debug/macos-ble.sh scan --timeout 20
set -euo pipefail

# Newest Homebrew framework python available; brew removes old ones on upgrade,
# so pinning a version breaks the script a release later.
FRAMEWORK=$(ls -d /opt/homebrew/opt/python@3.*/Frameworks/Python.framework/Versions/3.* 2>/dev/null | sort -t. -k2 -n | tail -1 || true)
[ -n "${FRAMEWORK:-}" ] || { echo "no Homebrew framework python found — brew install python@3.13" >&2; exit 1; }
PYVER=$(basename "$FRAMEWORK")

STATE="${XDG_STATE_HOME:-$HOME/.local/state}/open-cardi/$PYVER"
APP="$STATE/OpenCardiBLE.app"
VENV="$STATE/venv"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$STATE"

# An app bundle we own, carrying the NSBluetoothAlwaysUsageDescription that the
# stock interpreter lacks.
if [ ! -d "$APP" ]; then
  cp -R "$FRAMEWORK/Resources/Python.app" "$APP"
  PL="$APP/Contents/Info.plist"
  /usr/libexec/PlistBuddy -c 'Add :NSBluetoothAlwaysUsageDescription string "open-cardi talks to the LED controller over BLE."' "$PL"
  /usr/libexec/PlistBuddy -c 'Set :CFBundleIdentifier ink.harsh.opencardi.bledebug' "$PL"
  /usr/libexec/PlistBuddy -c 'Set :CFBundleName OpenCardiBLE' "$PL"
  codesign --force --sign - "$APP"
fi

[ -d "$VENV" ] || { "$FRAMEWORK/bin/python$PYVER" -m venv "$VENV"; "$VENV/bin/pip" -q install bleak; }

LOG="$STATE/last-run.log"
: > "$LOG"
# `open -n` is load-bearing: launchd makes the bundle its own responsible
# process, so TCC reads its Info.plist rather than the terminal's.
PYTHONHOME="$FRAMEWORK" PYTHONPATH="$VENV/lib/python$PYVER/site-packages" \
  open -n -W --stdout "$LOG" --stderr "$LOG" "$APP" --args -u "$HERE/open-cardi.py" "$@"
cat "$LOG"
