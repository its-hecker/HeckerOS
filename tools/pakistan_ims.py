#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Prepare Pakistan-only IMS defaults in Floral's actual Google CarrierSettings.

The wire format is Google's platform/tools/carrier_settings/proto schema.
Unchanged fields, APNs, carrier records and unknown fields retain their exact bytes.
This changes Android carrier configuration; it neither changes modem NV/EFS nor
claims that the carrier has accepted IMS registration.
"""
import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path("proprietary/product/etc/CarrierSettings")
PROTOCOLS = {"IP": 0, "IPV6": 1, "IPV4V6": 2}


@dataclass(frozen=True)
class Field:
    number: int
    wire: int
    value: object
    raw: bytes


def read_varint(data, pos):
    value = 0
    for shift in range(0, 70, 7):
        if pos >= len(data):
            raise ValueError("Truncated CarrierSettings varint")
        byte = data[pos]
        pos += 1
        if shift == 63 and byte > 1:
            raise ValueError("Overflowing CarrierSettings varint")
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
    raise ValueError("Invalid CarrierSettings varint")


def varint(value):
    if not 0 <= value < 1 << 64:
        raise ValueError("CarrierSettings integer out of range")
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def fields(data):
    pos = 0
    result = []
    while pos < len(data):
        start = pos
        tag, pos = read_varint(data, pos)
        number, wire = tag >> 3, tag & 7
        if not 0 < number < 1 << 29:
            raise ValueError("Invalid CarrierSettings field number")
        if wire == 0:
            value, pos = read_varint(data, pos)
        elif wire in (1, 2, 5):
            if wire == 2:
                size, pos = read_varint(data, pos)
            else:
                size = 8 if wire == 1 else 4
            if size > len(data) - pos:
                raise ValueError("Truncated CarrierSettings field")
            value = data[pos:pos + size]
            pos += size
        else:
            raise ValueError("Unsupported CarrierSettings wire type")
        result.append(Field(number, wire, value, data[start:pos]))
    return result


def encode(number, wire, value):
    tag = varint(number << 3 | wire)
    if wire == 0:
        return tag + varint(value)
    if wire == 2:
        return tag + varint(len(value)) + value
    raise ValueError("Unsupported replacement field")


def one(data, number, wire, default=None):
    matches = [f for f in fields(data) if f.number == number]
    if len(matches) > 1 or matches and matches[0].wire != wire:
        raise ValueError("Ambiguous CarrierSettings singular field " + str(number))
    return matches[0].value if matches else default


def replace_one(data, number, wire, value):
    one(data, number, wire)  # Refuse duplicate or differently typed fields.
    target = encode(number, wire, value)
    found = False
    result = []
    for item in fields(data):
        if item.number == number:
            found = True
            result.append(item.raw if item.value == value else target)
        else:
            result.append(item.raw)
    if not found:
        result.append(target)
    return b"".join(result)


def text(data, number):
    value = one(data, number, 2)
    if value is None:
        raise ValueError("Missing CarrierSettings identity")
    return value.decode("utf-8", errors="strict")


def policy():
    value = json.loads((ROOT / "ims/pakistan.json").read_text())
    if set(value["carriers"]) != {"41001", "41003", "41004", "41006", "41007"}:
        raise ValueError("Review changes to the Pakistan SIM allowlist")
    for carrier in value["carriers"].values():
        if carrier["protocol"] not in PROTOCOLS or carrier["ims_apn"] != "ims":
            raise ValueError("Review the IMS APN policy")
    for key in value["bool_configs"]:
        if "provision" in key or "emergency" in key or "roaming" in key:
            raise ValueError("IMS policy must retain carrier activation and emergency policy")
    return value


def carrier_targets(carrier_list, wanted):
    mappings = {}
    for item in fields(carrier_list):
        if item.number != 1:
            continue
        if item.wire != 2:
            raise ValueError("Invalid carrier-list record")
        name = text(item.value, 1)
        identities = [f.value for f in fields(item.value) if f.number == 2 and f.wire == 2]
        if not identities:
            raise ValueError("Carrier-list record has no SIM identity")
        for identity in identities:
            mappings.setdefault(name, set()).add(text(identity, 1))
    targets = {}
    seen = set()
    for name, plmns in mappings.items():
        matched = plmns & set(wanted)
        if not matched:
            continue
        # A shared record must not enable IMS for SIMs outside this allowlist.
        if plmns - set(wanted):
            raise ValueError("Pakistan carrier record also serves other SIMs: " + name)
        profiles = [wanted[p] for p in sorted(matched)]
        if len({(p["ims_apn"], p["protocol"]) for p in profiles}) != 1:
            raise ValueError("Conflicting APN policies in shared carrier record: " + name)
        targets[name] = profiles[0]
        seen.update(matched)
    if seen != set(wanted):
        raise ValueError("Pakistan SIM mappings missing: " + ", ".join(sorted(set(wanted) - seen)))
    return targets


def patch_configs(data, settings):
    wanted = {key: (5, int(value)) for key, value in settings["bool_configs"].items()}
    wanted.update({key: (3, value) for key, value in settings["int_configs"].items()})
    seen = set()
    output = []
    for item in fields(data):
        if item.number != 2:
            output.append(item.raw)
            continue
        if item.wire != 2:
            raise ValueError("Invalid CarrierConfig record")
        key = text(item.value, 1)
        if key not in wanted:
            output.append(item.raw)
            continue
        if key in seen:
            raise ValueError("Duplicate IMS carrier key: " + key)
        seen.add(key)
        number, value = wanted[key]
        # Do not leave another member of the Config.value oneof in place.
        entry = b"".join(f.raw for f in fields(item.value) if f.number not in range(2, 10))
        entry += encode(number, 0, value)
        output.append(item.raw if entry == item.value else encode(2, 2, entry))
    for key, (number, value) in wanted.items():
        if key not in seen:
            entry = encode(1, 2, key.encode()) + encode(number, 0, value)
            output.append(encode(2, 2, entry))
    return b"".join(output)


def apn_types(data):
    result = []
    for item in fields(data):
        if item.number != 3:
            continue
        if item.wire == 0:
            result.append(item.value)
        elif item.wire == 2:  # Also accept protobuf packed repeated enums.
            pos = 0
            while pos < len(item.value):
                value, pos = read_varint(item.value, pos)
                result.append(value)
        else:
            raise ValueError("Invalid APN type")
    return result


def patch_apns(data, carrier):
    for item in fields(data):
        if item.number == 2:
            if item.wire != 2:
                raise ValueError("Invalid CarrierApns record")
            if 7 in apn_types(item.value):  # Preserve any existing carrier IMS APN.
                return data
    apn = encode(1, 2, (carrier["name"] + " IMS").encode())
    apn += encode(2, 2, carrier["ims_apn"].encode()) + encode(3, 0, 7)
    apn += encode(14, 0, PROTOCOLS[carrier["protocol"]])
    apn += encode(15, 0, PROTOCOLS[carrier["protocol"]])
    apn += encode(23, 0, 0) + encode(24, 0, 0)
    return data + encode(2, 2, apn)


def patch_settings(data, carrier, settings, inherited_version=0):
    result = replace_one(data, 4, 2, patch_configs(one(data, 4, 2, b""), settings))
    result = replace_one(result, 3, 2, patch_apns(one(data, 3, 2, b""), carrier))
    if result != data:
        # Most records in others.pb inherit the file version. Writing version=1
        # would downgrade them relative to a previously cached carrier record.
        result = replace_one(result, 2, 0,
                             one(data, 2, 0, inherited_version) + settings["revision"])
    return result


def patch_others(data, carrier_list, settings=None):
    settings = settings or policy()
    targets = carrier_targets(carrier_list, settings["carriers"])
    seen = set()
    output = []
    for item in fields(data):
        if item.number != 2:
            output.append(item.raw)
            continue
        if item.wire != 2:
            raise ValueError("Invalid MultiCarrierSettings record")
        name = text(item.value, 1)
        if name not in targets:
            output.append(item.raw)
            continue
        if name in seen:
            raise ValueError("Duplicate Pakistan carrier settings: " + name)
        seen.add(name)
        value = patch_settings(item.value, targets[name], settings, one(data, 1, 0, 0))
        output.append(item.raw if value == item.value else encode(2, 2, value))
    if seen != set(targets):
        raise ValueError("Pakistan settings moved out of others.pb; review vendor update: "
                         + ", ".join(sorted(set(targets) - seen)))
    result = b"".join(output)
    if result != data:
        result = replace_one(result, 1, 0, one(data, 1, 0, 0) + settings["revision"])
    return result


def pakistan_ims_plan(rom, revert=False):
    """Use each vendor checkout's pristine Git input for reversible preparation."""
    changes = {}
    found = False
    for device in ("coral", "flame"):
        vendor = rom / "vendor/google" / device
        assets = vendor / ASSETS
        mk = vendor / (device + "-vendor.mk")
        if not assets.is_dir():
            if mk.exists() and "CarrierSettings" in mk.read_text():
                raise ValueError("Missing CarrierSettings inputs: " + str(assets))
            continue  # Source-only fixtures may omit the proprietary vendor tree.
        found = True
        original = {}
        for filename in ("others.pb", "carrier_list.pb"):
            relative = (ASSETS / filename).as_posix()
            try:
                original[filename] = subprocess.run(
                    ["git", "show", "HEAD:" + relative], cwd=vendor,
                    check=True, capture_output=True).stdout
            except subprocess.CalledProcessError as error:
                raise ValueError("Cannot read pristine vendor CarrierSettings: " + str(vendor)) from error
        mapping = assets / "carrier_list.pb"
        if mapping.read_bytes() != original["carrier_list.pb"]:
            raise ValueError("Preserving an unfamiliar carrier-list edit: " + str(mapping))
        path = assets / "others.pb"
        expected = patch_others(original["others.pb"], original["carrier_list.pb"])
        current = path.read_bytes()
        if current not in (original["others.pb"], expected):
            raise ValueError("Preserving unfamiliar vendor carrier settings: " + str(path))
        target = original["others.pb"] if revert else expected
        if current != target:
            changes[path] = target
    if found:
        print("Pakistan IMS: Jazz, Jazz/Warid, Zong, Telenor and Ufone carrier defaults/APNs; "
              "modem profiles and carrier provisioning retained")
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom-root", type=Path, required=True)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if not any((args.rom_root / "vendor/google" / device / ASSETS).is_dir()
               for device in ("coral", "flame")):
        parser.error("No Floral vendor CarrierSettings checkout found")
    changes = pakistan_ims_plan(args.rom_root.resolve())
    if changes:
        parser.exit(1, "Pakistan IMS source preparation pending; run Suite prepare.py --apply\n")
    print("Pakistan IMS CarrierSettings: verified")


if __name__ == "__main__":
    main()
