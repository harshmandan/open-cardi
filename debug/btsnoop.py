#!/usr/bin/env python3
"""Decode an Android btsnoop_hci.log into a readable Cardi wire timeline.

Prints ATT writes and notifications on the kit's characteristics, plus LE
connection events and disconnect reasons, with wall-clock timestamps so the
output lines up with open-cardi's wire log and with notes taken in the car.

    python3 debug/btsnoop.py captures/btsnoop_hci.log
    python3 debug/btsnoop.py btsnoop_hci.log --since 15:10 --until 15:30

Standard library only. Format reference: btsnoop (RFC 1761-style) with
datalink 1002 (HCI UART H4), as written by Android's snoop logger.
"""

from __future__ import annotations

import argparse
import struct
import sys
from datetime import datetime, timedelta, timezone

# btsnoop timestamps are microseconds since 0000-01-01 00:00 AD.
_EPOCH_DELTA_US = 0x00DCDDB30F2F8000

ATT_OPS = {
    0x01: "error",
    0x02: "mtu_req",
    0x03: "mtu_rsp",
    0x0A: "read_req",
    0x0B: "read_rsp",
    0x12: "write_req",
    0x13: "write_rsp",
    0x1B: "notify",
    0x1D: "indicate",
    0x52: "write_cmd",
}

# Disconnect reasons worth naming (Bluetooth Core spec, Vol 1 Part F).
HCI_REASONS = {
    0x08: "supervision timeout (kit went silent)",
    0x13: "remote user terminated (kit closed it)",
    0x16: "local host terminated (phone closed it)",
    0x22: "LL response timeout",
    0x3B: "unacceptable connection parameters",
    0x3D: "MIC failure",
    0x3E: "failed to establish",
}

# Labels for the frames open-cardi knows. Matched on leading bytes.
def label(payload: bytes) -> str:
    if not payload:
        return ""
    a = payload[0]
    b = payload[1] if len(payload) > 1 else None
    if a == 0xED and len(payload) == 6:
        if b == 0x49:
            return f"color {payload[2]:3d} {payload[3]:3d} {payload[4]:3d}"
        if b == 0xF0:
            return "master on"
        if b == 0x0F:
            return "master off"
        if b == 0x51:
            return "music color"
        if b == 0x34:
            return "mic on"
        if b == 0x33:
            return "mic off"
        if b is not None and (0x35 <= b <= 0x4C or 0x60 <= b <= 0x68):
            return f"pattern 0x{b:02x}"
    if a == 0xFE and len(payload) == 3:
        return f"zone {payload[1]}"
    if a == 0x3C and b == 0xAA:
        return "query state"
    if a == 0x0E and b == 0xAA:
        return "query zone"
    if a == 0x30 and len(payload) == 3:
        return f"speed {payload[1]}"
    if a == 0x31 and len(payload) == 3:
        return f"pattern brightness {payload[1]}"
    if a == 0xFA and len(payload) == 3:
        return f"app mode {payload[1]}"
    if a == 0xC3 and len(payload) == 3:
        return f"warm white {payload[1]}"
    if a in (0x16, 0x17, 0x18, 0x1E) and len(payload) in (3, 4):
        return f"query {a:02x}"
    if a == 0x33 and len(payload) == 4:
        return f"← master {'on' if payload[2] == 0xF0 else 'off'}"
    if a == 0x0B and len(payload) == 4:
        return f"← zone {payload[1]}"
    if a == 0xFB and payload[-1:] == b"\xfa":
        return "AES challenge"
    if a == 0xF9 and payload[-1:] == b"\xf8":
        return "← AES reply"
    if a == 0xF1 and payload == bytes.fromhex("f1f2f3f4f5f6"):
        return "FACTORY RESET"
    if len(payload) >= 20 and all(x == 0xFF for x in payload):
        return "← ready marker"
    return ""


def read_records(path: str):
    with open(path, "rb") as f:
        header = f.read(16)
        if header[:8] != b"btsnoop\0":
            sys.exit(f"{path}: not a btsnoop file")
        version, datalink = struct.unpack(">II", header[8:16])
        if datalink not in (1001, 1002):
            sys.exit(f"{path}: unsupported datalink {datalink}")
        while True:
            rec = f.read(24)
            if len(rec) < 24:
                return
            orig_len, incl_len, flags, _drops, ts = struct.unpack(">IIIIq", rec)
            data = f.read(incl_len)
            if len(data) < incl_len:
                return
            received = bool(flags & 0x1)
            when = datetime(1970, 1, 1, tzinfo=timezone.utc) + timedelta(
                microseconds=ts - _EPOCH_DELTA_US
            )
            yield when, received, data, datalink


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    ap.add_argument("path")
    ap.add_argument("--since", help="HH:MM[:SS] local time, inclusive")
    ap.add_argument("--until", help="HH:MM[:SS] local time, inclusive")
    ap.add_argument("--handle", type=lambda v: int(v, 0),
                    help="only this LE connection handle, e.g. 0x040 (see LINK lines)")
    ap.add_argument("--all-att", action="store_true",
                    help="show ATT traffic on every handle, not just data writes/notifies")
    args = ap.parse_args()

    def hms(s: str | None):
        if not s:
            return None
        parts = [int(p) for p in s.split(":")] + [0, 0]
        return parts[0] * 3600 + parts[1] * 60 + parts[2]

    since, until = hms(args.since), hms(args.until)
    local_tz = datetime.now().astimezone().tzinfo

    # ACL fragments are reassembled per connection handle and direction.
    partial: dict[tuple[int, bool], bytearray] = {}
    expected: dict[tuple[int, bool], int] = {}
    conn_since: dict[int, datetime] = {}

    for when, received, data, datalink in read_records(args.path):
        local = when.astimezone(local_tz)
        secs = local.hour * 3600 + local.minute * 60 + local.second
        in_window = (since is None or secs >= since) and (until is None or secs <= until)
        stamp = local.strftime("%H:%M:%S.%f")[:-3]

        if datalink == 1002:
            ptype, body = data[0], data[1:]
        else:
            ptype, body = (0x04 if received else 0x02), data

        if ptype == 0x04 and len(body) >= 2:  # HCI event
            code = body[0]
            params = body[2:]
            if code == 0x3E and params:  # LE meta
                sub = params[0]
                if sub in (0x01, 0x0A) and len(params) >= 5:
                    status = params[1]
                    handle = struct.unpack("<H", params[2:4])[0] & 0x0FFF
                    if status == 0:
                        conn_since[handle] = local
                    if in_window:
                        print(f"{stamp}  LINK  connected handle=0x{handle:03x} status=0x{status:02x}")
                elif sub == 0x03 and len(params) >= 10 and in_window:
                    status = params[1]
                    handle = struct.unpack("<H", params[2:4])[0] & 0x0FFF
                    interval, latency, timeout = struct.unpack("<HHH", params[4:10])
                    print(f"{stamp}  LINK  params handle=0x{handle:03x} interval={interval * 1.25:.2f}ms "
                          f"latency={latency} timeout={timeout * 10}ms status=0x{status:02x}")
            elif code == 0x05 and len(params) >= 4:  # disconnection complete
                handle = struct.unpack("<H", params[1:3])[0] & 0x0FFF
                reason = params[3]
                up = conn_since.pop(handle, None)
                lasted = f" after {(local - up).total_seconds():.1f}s" if up else ""
                if in_window:
                    print(f"{stamp}  LINK  DISCONNECTED handle=0x{handle:03x}{lasted} — "
                          f"0x{reason:02x} {HCI_REASONS.get(reason, '')}")
            continue

        if ptype != 0x02 or len(body) < 4:  # ACL data only from here
            continue
        hdr, length = struct.unpack("<HH", body[:4])
        handle = hdr & 0x0FFF
        pb = (hdr >> 12) & 0x3
        key = (handle, received)
        chunk = body[4:4 + length]
        if pb in (0x0, 0x2):  # start of an L2CAP frame
            if len(chunk) < 4:
                continue
            l2_len = struct.unpack("<H", chunk[:2])[0]
            partial[key] = bytearray(chunk)
            expected[key] = l2_len + 4
        elif key in partial:
            partial[key].extend(chunk)
        else:
            continue
        if len(partial[key]) < expected[key]:
            continue
        frame = bytes(partial.pop(key))
        expected.pop(key, None)

        cid = struct.unpack("<H", frame[2:4])[0]
        if cid != 0x0004:  # ATT only
            continue
        att = frame[4:]
        if not att or not in_window:
            continue
        if args.handle is not None and handle != args.handle:
            continue
        op = att[0]
        name = ATT_OPS.get(op)
        direction = "RECV" if received else "SEND"
        if op in (0x12, 0x52, 0x1B, 0x1D, 0x0B) or (args.all_att and name):
            if op in (0x12, 0x52, 0x1B, 0x1D) and len(att) >= 3:
                att_handle = struct.unpack("<H", att[1:3])[0]
                value = att[3:]
            else:
                att_handle, value = None, att[1:]
            where = f"h=0x{att_handle:04x}" if att_handle is not None else ""
            text = label(value)
            print(f"{stamp}  {direction}  [{handle:03x}] {name:<9} {where:<9} {value.hex(' ')}"
                  f"{'  ' + text if text else ''}")
        elif op == 0x01 and len(att) >= 5:
            print(f"{stamp}  {direction}  [{handle:03x}] ATT ERROR req=0x{att[1]:02x} "
                  f"h=0x{struct.unpack('<H', att[2:4])[0]:04x} code=0x{att[4]:02x}")


if __name__ == "__main__":
    main()
