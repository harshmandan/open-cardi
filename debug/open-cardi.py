#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["bleak>=0.22"]
# ///
"""
Cardi BLE replay tool.

Connect to a CarDiLits device, run the init handshake, subscribe to FFE1
notifications, and send commands either one-shot or through an interactive REPL.

Usage examples (with uv — auto-installs deps):
  uv run debug/open-cardi.py scan
  uv run debug/open-cardi.py repl
  uv run debug/open-cardi.py monitor --duration 60
  uv run debug/open-cardi.py send red green blue --delay 1.0

Or with plain pip:
  pip install bleak
  python debug/open-cardi.py repl
"""

from __future__ import annotations

import argparse
import asyncio
import sys
from datetime import datetime

from bleak import BleakClient, BleakScanner
from bleak.backends.characteristic import BleakGATTCharacteristic
from bleak.backends.device import BLEDevice

# ----------------------------------------------------------------------------
# Constants — derived from APK decompile (see notes/APK_DECOMPILE_FINDINGS.md)
# ----------------------------------------------------------------------------

DEVICE_NAME = "CarDiLits"  # default scan target / display label
SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"  # vendor service — best discovery filter
WRITE_UUID = "0000ffe2-0000-1000-8000-00805f9b34fb"   # WRITE NO RESPONSE
NOTIFY_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"  # NOTIFY/READ

# Lifted from the official app's BluetoothLeService.COMPANY_NAME regex plus
# Dream/Flash variants seen in MyBluetoothGatt — covers all known OEM rebrands
# of the ConSmart firmware.
DEVICE_NAME_PREFIXES = (
    "iLits",
    "CarDiLits",
    "BaoYin",
    "CaChuang",
    "CarL",
    "MTF-LIGHT",
    "AIKON-LIT",
    "Dream",
    "Flash",
)

# Init handshake observed in HCI captures (matches Java getLightData / readisnewmod)
HANDSHAKE_WRITES: list[bytes] = [
    bytes.fromhex("3caaaa3d"),  # getLightData()
    bytes.fromhex("0eaae0"),    # readisnewmod() — query current zone
    bytes.fromhex("180081"),    # session lifecycle (purpose unmapped)
    bytes.fromhex("170071"),
    bytes.fromhex("160061"),
]

# Common preset colors
PRESETS: dict[str, tuple[int, int, int]] = {
    "red":     (255, 0, 0),
    "green":   (0, 255, 0),
    "blue":    (0, 0, 255),
    "white":   (255, 255, 255),
    "yellow":  (255, 255, 0),
    "cyan":    (0, 255, 255),
    "magenta": (255, 0, 255),
    "orange":  (255, 128, 0),
    "purple":  (128, 0, 255),
    "black":   (0, 0, 0),
}

# Zone names → setnewmod argument value (FE <value> EF)
# Source: strings.xml new_mod1..9 + Java setnewmod(i) sends FE (i+1) EF
# Most kits only support a subset; the K3 Active Ultra has Overall/Light-Guide/Doors/Floor.
ZONES: dict[str, int] = {
    "overall":   1,
    "front":     2,  # "Light Guide" in the official app
    "guide":     2,
    "doors":     3,
    "floor":     4,  # a.k.a. footwell
    "footwell":  4,
    "skylight":  5,
    "chassis-strobe": 6,
    "grill":     7,
    "chassis":   8,
    "strobe":    9,
}

# Pattern opcodes — Java Mods[] = {0x35..0x4C} skipping 0x49 (which is set-color)
PATTERN_OPCODES: list[int] = [
    0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E,
    0x3F, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
    0x4A, 0x4B, 0x4C,
]

# ----------------------------------------------------------------------------
# Pure command builders — port these to TypeScript verbatim for the web app.
# ----------------------------------------------------------------------------

def cmd_set_color(r: int, g: int, b: int, brightness_pct: int = 100) -> bytes:
    """ED 49 R G B E9 — solid color. The official app pre-scales R/G/B by
    (progress/100) before sending, so brightness is baked into the bytes."""
    pct = max(0, min(100, brightness_pct))
    rs = (r * pct) // 100
    gs = (g * pct) // 100
    bs = (b * pct) // 100
    return bytes([0xED, 0x49, rs & 0xFF, gs & 0xFF, bs & 0xFF, 0xE9])


def cmd_master_on() -> bytes:
    """ED F0 00 00 00 E9 — master ON. Confirmed via APK + live test."""
    return bytes.fromhex("edf0000000e9")


def cmd_master_off() -> bytes:
    """ED 0F 00 00 00 E9 — master OFF. Confirmed via APK + live test."""
    return bytes.fromhex("ed0f000000e9")


def cmd_zone_select(zone_value: int) -> bytes:
    """FE <zone_value> EF — selects active zone for subsequent color writes.
    zone_value: 1=Overall, 2=Light Guide (front), 3=Doors, 4=Floor (footwell)."""
    return bytes([0xFE, zone_value & 0xFF, 0xEF])


def cmd_pattern(index: int) -> bytes:
    """ED <Mods[index]> 00 00 00 E9 — built-in pattern. index is 0..22."""
    if not 0 <= index < len(PATTERN_OPCODES):
        raise ValueError(f"pattern index {index} out of range (0..{len(PATTERN_OPCODES) - 1})")
    return bytes([0xED, PATTERN_OPCODES[index], 0x00, 0x00, 0x00, 0xE9])


def cmd_pattern_speed(speed: int) -> bytes:
    """30 <speed> 03 — pattern playback speed.
    Valid range 1..100 per the app's slider (seekbar max=99, code sends progress+1).
    Higher = faster. Verified live; values >100 cause unexpected behavior."""
    speed = max(1, min(100, speed))
    return bytes([0x30, speed, 0x03])


def cmd_pattern_brightness(pct: int) -> bytes:
    """31 <pct> 13 — pattern-mode brightness (1..100). For solid color, scale RGB
    in cmd_set_color() instead."""
    pct = max(1, min(100, pct))
    return bytes([0x31, pct, 0x13])


def cmd_warm_white_brightness(pct: int) -> bytes:
    """C3 <pct> 3C — warm-white channel brightness (the W in WRGB)."""
    pct = max(0, min(100, pct))
    return bytes([0xC3, pct, 0x3C])


def cmd_mic_on(mode: int) -> bytes:
    """ED 34 (m+1) (m+1) (m+1) E9 — device-onboard microphone mode ON.
    The 'mode' arg is the effect style: 0=Classic, 1=Soft, 2=Jump, 3=Dance.
    The Java app sends (mode+1) on the wire."""
    b = (mode + 1) & 0xFF
    return bytes([0xED, 0x34, b, b, b, 0xE9])


def cmd_mic_off() -> bytes:
    """ED 33 01 01 01 E9 — microphone sync OFF."""
    return bytes.fromhex("ed33010101e9")


def cmd_music_color(r: int, g: int, b: int) -> bytes:
    """ED 51 R G B E9 — color used during music playback (not solid-color mode)."""
    if r == 0 and g == 0 and b == 0:
        # Java setMusicColor() forces a non-zero color when all zero (sentinel)
        r, g, b = 16, 5, 5
    return bytes([0xED, 0x51, r & 0xFF, g & 0xFF, b & 0xFF, 0xE9])


def cmd_fragrance(on: bool) -> bytes:
    """F8 F0 8F (on) / F8 0F 8F (off) — fragrance dispenser toggle (kits with one)."""
    return bytes([0xF8, 0xF0 if on else 0x0F, 0x8F])


def cmd_mode_classic() -> bytes:
    """FA 01 AF — switch to Classic mode (Java setMTX(0))."""
    return bytes.fromhex("fa01af")


def cmd_mode_starlight() -> bytes:
    """FA 02 AF — switch to Starlight mode (Java setMTX(1)). Heads-up:
    seems to leave Doors zone stuck off on at least the K3 Active Ultra —
    needs a controller power-cycle to recover."""
    return bytes.fromhex("fa02af")


# ----------------------------------------------------------------------------
# Logging helpers
# ----------------------------------------------------------------------------

def ts() -> str:
    return datetime.now().strftime("%H:%M:%S.%f")[:-3]


def log_tx(data: bytes, label: str = "") -> None:
    suffix = f"  {label}" if label else ""
    print(f"[{ts()}] tx → {data.hex(' ')}{suffix}", flush=True)


def log_rx(data: bytes) -> None:
    print(f"[{ts()}] rx ← {bytes(data).hex(' ')}", flush=True)


def log_info(msg: str) -> None:
    print(f"[{ts()}] {msg}", flush=True)


# ----------------------------------------------------------------------------
# Connection lifecycle
# ----------------------------------------------------------------------------

def _is_match(device: BLEDevice, advertisement: object | None, explicit_name: str | None) -> bool:
    """Match a device by service UUID (preferred) or by any known name prefix.
    `explicit_name` overrides the prefix list when the user passes --name."""
    if advertisement is not None:
        uuids = getattr(advertisement, "service_uuids", None) or []
        if any(u.lower() == SERVICE_UUID.lower() for u in uuids):
            return True
    name = (device.name or "").strip()
    if not name:
        return False
    if explicit_name:
        return explicit_name.lower() in name.lower()
    return any(name.startswith(p) for p in DEVICE_NAME_PREFIXES)


async def discover(timeout: float = 6.0, name: str | None = None) -> BLEDevice | None:
    target = name or "ConSmart-family device (any known prefix or service 0xFFF0)"
    log_info(f"scanning for {target} (timeout {timeout}s)…")
    found: dict[str, tuple[BLEDevice, object]] = {}

    def detection_callback(device: BLEDevice, advertisement_data: object) -> None:
        if _is_match(device, advertisement_data, name):
            found[device.address] = (device, advertisement_data)

    scanner = BleakScanner(detection_callback=detection_callback)
    await scanner.start()
    try:
        await asyncio.sleep(timeout)
    finally:
        await scanner.stop()

    if not found:
        log_info("no matching device found")
        return None
    device, _ = next(iter(found.values()))
    log_info(f"found {device.name or '<unnamed>'} @ {device.address}")
    return device


def make_notify_handler():
    def handler(_: BleakGATTCharacteristic, data: bytearray) -> None:
        log_rx(data)
    return handler


async def run_handshake(client: BleakClient, gap: float = 0.1) -> None:
    log_info("running init handshake…")
    for i, payload in enumerate(HANDSHAKE_WRITES, start=1):
        log_tx(payload, label=f"handshake {i}/{len(HANDSHAKE_WRITES)}")
        await client.write_gatt_char(WRITE_UUID, payload, response=False)
        await asyncio.sleep(gap)
    log_info("handshake complete")


async def connect(mac: str | None, name: str | None, scan_timeout: float) -> BleakClient:
    if mac:
        log_info(f"connecting to {mac}…")
        client = BleakClient(mac)
    else:
        device = await discover(timeout=scan_timeout, name=name)
        if device is None:
            raise SystemExit(2)
        client = BleakClient(device)

    await client.connect()
    log_info("connected (services discovered)")

    await client.start_notify(NOTIFY_UUID, make_notify_handler())
    log_info("subscribed to FFE1 notifications")

    await run_handshake(client)
    return client


# ----------------------------------------------------------------------------
# REPL / send dispatch — shared parser for textual commands
# ----------------------------------------------------------------------------

class State:
    """Track last issued color/zone so we can keepalive-replay or query."""
    def __init__(self) -> None:
        self.last_color: tuple[int, int, int] = (255, 0, 0)
        self.last_brightness: int = 100
        self.last_zone: int = 1


async def write(client: BleakClient, payload: bytes, label: str) -> None:
    log_tx(payload, label=label)
    await client.write_gatt_char(WRITE_UUID, payload, response=False)


async def send_command(client: BleakClient, token: str, state: State) -> None:
    """Parse and execute a single textual command token. Returns silently on success."""
    parts = token.strip().split()
    if not parts:
        return
    head, rest = parts[0].lower(), parts[1:]

    # Color presets
    if head in PRESETS:
        r, g, b = PRESETS[head]
        state.last_color = (r, g, b)
        await write(client, cmd_set_color(r, g, b, state.last_brightness),
                    f"color {head} @ {state.last_brightness}%")
        return

    # Master toggle
    if head == "on":
        await write(client, cmd_master_on(), "master ON")
        return
    if head == "off":
        await write(client, cmd_master_off(), "master OFF")
        return

    # RGB
    if head in ("c", "color") and len(rest) == 3:
        r, g, b = (int(x, 0) for x in rest)
        state.last_color = (r, g, b)
        await write(client, cmd_set_color(r, g, b, state.last_brightness),
                    f"color rgb({r},{g},{b}) @ {state.last_brightness}%")
        return

    # Brightness — re-emits last color with new scaling (matches setColorProportion)
    if head in ("b", "bright", "brightness") and len(rest) == 1:
        state.last_brightness = max(0, min(100, int(rest[0], 0)))
        r, g, b = state.last_color
        await write(client, cmd_set_color(r, g, b, state.last_brightness),
                    f"brightness {state.last_brightness}% (replay last color)")
        return

    # Zone select — accept name or number
    if head in ("z", "zone") and len(rest) == 1:
        arg = rest[0].lower()
        if arg in ZONES:
            zid = ZONES[arg]
            label = f"zone {arg} (FE {zid:02x} EF)"
        else:
            zid = int(arg, 0)
            label = f"zone 0x{zid:02x} (raw)"
        state.last_zone = zid
        await write(client, cmd_zone_select(zid), label)
        return

    # Patterns
    if head in ("p", "pattern") and len(rest) == 1:
        idx = int(rest[0], 0)
        await write(client, cmd_pattern(idx),
                    f"pattern {idx} (opcode 0x{PATTERN_OPCODES[idx]:02x})")
        return
    if head == "speed" and len(rest) == 1:
        sp = max(1, min(100, int(rest[0], 0)))
        await write(client, cmd_pattern_speed(sp), f"pattern speed {sp}")
        return
    if head == "pbright" and len(rest) == 1:
        await write(client, cmd_pattern_brightness(int(rest[0], 0)),
                    f"pattern brightness {rest[0]}%")
        return

    # Warm-white channel
    if head in ("w", "ww") and len(rest) == 1:
        await write(client, cmd_warm_white_brightness(int(rest[0], 0)),
                    f"warm-white {rest[0]}%")
        return

    # Mic
    if head == "mic" and len(rest) >= 1:
        if rest[0].lower() == "off":
            await write(client, cmd_mic_off(), "mic OFF")
        elif rest[0].lower() == "on" and len(rest) == 2:
            await write(client, cmd_mic_on(int(rest[1], 0)), f"mic ON sens={rest[1]}")
        else:
            log_info("usage: mic on <sensitivity> | mic off")
        return

    # Fragrance
    if head == "fragrance" and len(rest) == 1:
        on = rest[0].lower() == "on"
        await write(client, cmd_fragrance(on), f"fragrance {'ON' if on else 'OFF'}")
        return

    # Mode toggle (Classic / Starlight)
    if head == "mode" and len(rest) == 1:
        which = rest[0].lower()
        if which == "classic":
            await write(client, cmd_mode_classic(), "mode Classic (FA 01 AF)")
        elif which == "starlight":
            await write(client, cmd_mode_starlight(), "mode Starlight (FA 02 AF) — may break Doors")
        else:
            log_info("usage: mode classic | mode starlight")
        return

    # Raw
    if head == "raw" and len(rest) == 1:
        payload = bytes.fromhex(rest[0])
        await write(client, payload, "raw")
        return

    if head == "sleep" and len(rest) == 1:
        await asyncio.sleep(float(rest[0]))
        return

    log_info(f"unknown command: {token!r}  (try 'help')")


# ----------------------------------------------------------------------------
# Subcommands
# ----------------------------------------------------------------------------

async def cmd_scan(args: argparse.Namespace) -> None:
    log_info(f"scanning for {args.timeout}s…")
    seen: dict[str, tuple[BLEDevice, object]] = {}

    def detection_callback(device: BLEDevice, advertisement_data: object) -> None:
        seen[device.address] = (device, advertisement_data)

    scanner = BleakScanner(detection_callback=detection_callback)
    await scanner.start()
    try:
        await asyncio.sleep(args.timeout)
    finally:
        await scanner.stop()

    if not seen:
        log_info("no BLE devices seen")
        return
    for device, adv in seen.values():
        match = _is_match(device, adv, None)
        marker = " ← cardi-family match" if match else ""
        uuids = getattr(adv, "service_uuids", None) or []
        svc_marker = " [adv:fff0]" if any(u.lower() == SERVICE_UUID.lower() for u in uuids) else ""
        print(f"  {device.address}  {device.name or '<unnamed>'}{svc_marker}{marker}")


async def cmd_monitor(args: argparse.Namespace) -> None:
    client = await connect(args.mac, args.name, args.scan_timeout)
    try:
        log_info(f"monitoring for {args.duration}s — press ctrl-c to stop early")
        await asyncio.sleep(args.duration)
    finally:
        await client.disconnect()
        log_info("disconnected")


async def cmd_send(args: argparse.Namespace) -> None:
    client = await connect(args.mac, args.name, args.scan_timeout)
    state = State()
    try:
        for i, token in enumerate(args.commands):
            if i > 0 and args.delay > 0:
                await asyncio.sleep(args.delay)
            await send_command(client, token, state)
        await asyncio.sleep(0.3)  # give the device a moment to react/notify
    finally:
        await client.disconnect()
        log_info("disconnected")


# ----------------------------------------------------------------------------
# Subcommand: repl — interactive prompt with optional keepalive
# ----------------------------------------------------------------------------

REPL_HELP = """\
commands:
  on, off                          master toggle
  red, green, blue, white,
  yellow, cyan, magenta,
  orange, purple, black            preset colors (current zone, current brightness)
  c R G B  /  color R G B          arbitrary RGB (0..255 decimal or 0x..)
  b N      /  bright N             brightness 0..100, re-emits last color
  z NAME   /  zone NAME            zone: overall | front | doors | floor |
                                          skylight | grill | chassis | strobe
  z N      /  zone N               zone select by raw value (1..9)
  mode classic | mode starlight    classic ↔ starlight (FA 01 AF / FA 02 AF)
  p N      /  pattern N            built-in pattern, index 0..22
  speed N                          pattern speed 1..31 (1=fast, 31=slow)
  pbright N                        pattern-mode brightness 1..100
  ww N     /  w N                  warm-white channel brightness 0..100
  mic on 0..3 | mic off            device-mic mode (0=Classic, 1=Soft, 2=Jump, 3=Dance)
  fragrance on | fragrance off     fragrance dispenser toggle
  raw HEX                          arbitrary bytes (e.g. raw ed49ff0000e9)
  sleep S                          wait S seconds
  ka on | ka off                   keepalive — re-emit last color every 8s to fight idle drop
  ?, help                          show this help
  q, quit, exit                    disconnect and exit
"""


async def keepalive_loop(client: BleakClient, state: State, stop: asyncio.Event,
                         interval: float = 8.0) -> None:
    while not stop.is_set():
        try:
            await asyncio.wait_for(stop.wait(), timeout=interval)
            return
        except asyncio.TimeoutError:
            pass
        try:
            r, g, b = state.last_color
            payload = cmd_set_color(r, g, b, state.last_brightness)
            log_tx(payload, label="keepalive")
            await client.write_gatt_char(WRITE_UUID, payload, response=False)
        except Exception as e:
            log_info(f"keepalive error: {e}")
            return


async def cmd_repl(args: argparse.Namespace) -> None:
    client = await connect(args.mac, args.name, args.scan_timeout)
    state = State()
    ka_stop: asyncio.Event | None = None
    ka_task: asyncio.Task | None = None

    def stop_keepalive() -> None:
        nonlocal ka_stop, ka_task
        if ka_stop is not None:
            ka_stop.set()
        ka_stop = None
        ka_task = None

    try:
        print(REPL_HELP)
        loop = asyncio.get_running_loop()
        while True:
            try:
                line = await loop.run_in_executor(None, input, "cardi> ")
            except (EOFError, KeyboardInterrupt):
                break
            line = line.strip()
            if not line:
                continue
            if line in ("q", "quit", "exit"):
                break
            if line in ("?", "help"):
                print(REPL_HELP)
                continue
            if line.startswith("ka "):
                arg = line[3:].strip().lower()
                if arg == "on":
                    if ka_task is None:
                        ka_stop = asyncio.Event()
                        ka_task = asyncio.create_task(keepalive_loop(client, state, ka_stop))
                        log_info("keepalive ON (8s interval)")
                    else:
                        log_info("keepalive already on")
                elif arg == "off":
                    if ka_task is not None:
                        stop_keepalive()
                        log_info("keepalive OFF")
                    else:
                        log_info("keepalive already off")
                else:
                    log_info("usage: ka on | ka off")
                continue
            try:
                await send_command(client, line, state)
            except Exception as e:
                log_info(f"error: {e}")
    finally:
        stop_keepalive()
        try:
            await client.disconnect()
        except Exception:
            pass
        log_info("disconnected")


# ----------------------------------------------------------------------------
# Argparse
# ----------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="replay", description=__doc__.strip().splitlines()[0])
    sub = p.add_subparsers(dest="cmd", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--mac", help="connect by MAC/UUID instead of scanning")
    common.add_argument(
        "--name",
        default=None,
        help="restrict scan to a specific device name substring "
             "(default: any ConSmart-family prefix or service 0xFFF0)",
    )
    common.add_argument("--scan-timeout", type=float, default=6.0, help="scan timeout in seconds (default: 6.0)")

    s_scan = sub.add_parser("scan", help="list nearby BLE devices")
    s_scan.add_argument("--timeout", type=float, default=6.0)

    sub.add_parser("repl", parents=[common], help="interactive command prompt")

    s_mon = sub.add_parser("monitor", parents=[common], help="connect and log notifications for N seconds")
    s_mon.add_argument("--duration", type=float, default=30.0, help="seconds to monitor (default: 30)")

    s_send = sub.add_parser("send", parents=[common], help="run a one-shot sequence of commands")
    s_send.add_argument("commands", nargs="+", help="command tokens, e.g. red blue 'c 128 64 32' 'z floor'")
    s_send.add_argument("--delay", type=float, default=0.5, help="delay between commands (default: 0.5s)")

    return p


def main() -> int:
    args = build_parser().parse_args()
    handlers = {
        "scan": cmd_scan,
        "repl": cmd_repl,
        "monitor": cmd_monitor,
        "send": cmd_send,
    }
    try:
        asyncio.run(handlers[args.cmd](args))
    except KeyboardInterrupt:
        print()
        return 130
    return 0


if __name__ == "__main__":
    sys.exit(main())
