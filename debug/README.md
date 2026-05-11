# debug/

Standalone Python tool for poking the Cardi BLE protocol directly — useful when adding a new device family, verifying a capture, or sanity-checking a wire-level change before touching the web app.

`open-cardi.py` is a single-file [Bleak](https://github.com/hbldh/bleak) client. It scans for the vendor service `0xFFF0`, runs the same init handshake the web app does, and exposes either an interactive REPL or one-shot send mode.

## Requirements

- Python ≥ 3.10
- [`uv`](https://github.com/astral-sh/uv) (recommended — auto-installs deps), or `pip install bleak`
- A Bluetooth adapter the OS exposes to userland (macOS / Linux / Windows all fine)

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

## REPL cheatsheet

```
on, off                       master toggle
red | green | blue | …        preset colors (current zone, current brightness)
c R G B                       arbitrary RGB
b N                           brightness 0..100
z overall|front|doors|floor|skylight|grill|chassis|strobe   zone select
mode classic | mode starlight FA 01 AF / FA 02 AF
p N                           pattern 0..22
speed N                       pattern speed 1..31
mic on 0..3 | mic off         device-mic mode
raw HEX                       send arbitrary bytes (e.g. raw ed49ff0000e9)
ka on                         keepalive — re-emit last color every 8s
?                             show help
q                             disconnect and quit
```

## Adding a new device family

1. Run `scan` while the kit is in pairing mode and note the advertised name.
2. If the name prefix isn't in `DEVICE_NAME_PREFIXES`, add it there (and in `src/lib/protocol/constants.ts` once verified).
3. Open the `repl`, run `on`, `red`, `b 50`, `z floor` etc., and confirm the LEDs respond.
4. If a command is silent, capture HCI snoop logs (`adb bugreport` on Android) and diff against the existing op table in `../README.md`.
