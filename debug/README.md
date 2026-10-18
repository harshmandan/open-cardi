# debug/

Standalone Python tool for poking the Cardi BLE protocol directly — useful when adding a new device family, verifying a capture, or sanity-checking a wire-level change before touching the web app.

`open-cardi.py` is a single-file [Bleak](https://github.com/hbldh/bleak) client. It scans for the vendor service `0xFFF0`, runs the same init handshake the web app does, and exposes either an interactive REPL or one-shot send mode.

## Requirements

- Python ≥ 3.10
- [`uv`](https://github.com/astral-sh/uv) (recommended — auto-installs deps), or `pip install bleak`
- A Bluetooth adapter the OS exposes to userland (macOS / Linux / Windows all fine)

## macOS gotcha: python aborts the moment it touches Bluetooth

On macOS the process must declare `NSBluetoothAlwaysUsageDescription` in its
bundle `Info.plist` before CoreBluetooth will talk to it. A plain interpreter —
Homebrew's `python3`, a `uv run` python, the venv `python` — has no bundle, so
the first scan kills it:

```
Termination Reason: Namespace TCC, Code 0
This app has crashed because it attempted to access privacy-sensitive data
without a usage description.
```

Exit code 134 (SIGABRT) with no traceback is this, every time.

Granting Bluetooth permission to the terminal does not fix it. TCC checks the
_responsible process_, and a child launched from the shell inherits the
terminal as responsible — so it is the terminal's (absent) plist that gets read.

`debug/macos-ble.sh` works around it:

```sh
debug/macos-ble.sh scan --timeout 20
debug/macos-ble.sh repl
debug/macos-ble.sh monitor --duration 120
```

It copies the framework `Python.app` to `~/.local/state/open-cardi/`, adds the
usage-description key, ad-hoc re-signs it, builds a venv with `bleak`, and
launches via `open -n`. Both halves matter:

- our own bundle, because editing Homebrew's plist breaks its signature and is
  clobbered on upgrade;
- `open -n`, because launchd starts the bundle detached, making it responsible
  for itself so TCC finally reads the plist we wrote.

First run raises the usual system Bluetooth prompt. Approve it and everything
after works. Output is teed to `~/.local/state/open-cardi/last-run.log` — `open
-W` gives no useful exit status of its own.

Other gotchas:

- A controller already connected to something else (the phone app, nRF Connect)
  stops advertising, so it will not appear in a scan. Disconnect first.
- `scan` takes `--timeout`; the connecting commands take `--scan-timeout`.

## Commands

```sh
# list nearby BLE devices (no connect)
uv run debug/open-cardi.py scan

# interactive prompt — recommended for exploration
uv run debug/open-cardi.py repl

# connect + log notifications for 30s
uv run debug/open-cardi.py monitor --duration 30

# one-shot sequence
uv run debug/open-cardi.py send red blue 'c 128 64 32' 'z floor' --delay 1.0
```

All connecting commands accept `--mac <addr>`, `--name <prefix>`, and `--scan-timeout <sec>`.

`--legacy-handshake` sends the old Cardi Tech handshake (`3C AA AA 3D`, `0E AA E0`, `18 00 81`, `17 00 71`, `16 00 61`) instead of the current one (`3C AA AA 3D`, `0E AA E0`). The extra three are fragrance and air-purifier queries; use the flag only to compare the two.

## REPL cheatsheet

```
on, off                       master toggle
red | green | blue | …        preset colors (current zone, current brightness)
c R G B                       arbitrary RGB
b N                           brightness 0..100
z overall|front|doors|floor|skylight|grill|chassis|strobe   zone select
mode classic | mode starlight FA 01 AF / FA 02 AF
p N                           pattern 0..22
breathe N                     breathing pattern 0..8 (ED 60..68) — untested on K3
speed N                       pattern speed 1..31
mic on 0..3 | mic off         device-mic mode
lock | decode                 lock code / decode knock
version                       firmware version query (1E 00 E1) — K3 doesn't answer
reset confirm                 factory reset (F1 F2 F3 F4 F5 F6) — untested
raw HEX                       send arbitrary bytes (e.g. raw ed49ff0000e9)
ka on                         keepalive — re-emit last color every 8s
?                             show help
q                             disconnect and quit
```

## Checks still to run on a K3

These came from the Cardi-RGBW 1.2.2 decompile and haven't been tried on the
hardware yet. Run them from the `repl`.

1. ~~**Firmware version.**~~ Done 2026-10-18: the K3 doesn't answer `1E 00 E1`.
2. **Breathing patterns.** `z overall`, then `breathe 0` through `breathe 8`.
   Note which ones animate. If they all do, add them to the app's pattern list.
3. **Pattern labels.** `p 1`, `p 2`, `p 21` (opcodes `36`, `37`, `4B`). The
   vendor calls them Red Gradient, Green Gradient and RGB Gradient; our labels
   say otherwise. Rename whichever is wrong.
4. **Factory reset, last, on a healthy kit.** Note the colour, zone, and mode
   first. `reset confirm`. Wait 5 s, reconnect, and record what changed. If
   it's clean, it can go in the app behind a confirm, as a recovery option for
   the stuck mode.

## Adding a new device family

1. Run `scan` while the kit is in pairing mode and note the advertised name.
2. If the name prefix isn't in `DEVICE_NAME_PREFIXES`, add it there (and in `src/lib/protocol/constants.ts` once verified).
3. Open the `repl`, run `on`, `red`, `b 50`, `z floor` etc., and confirm the LEDs respond.
4. If a command is silent, capture HCI snoop logs (`adb bugreport` on Android) and diff against the existing op table in `../README.md`.
