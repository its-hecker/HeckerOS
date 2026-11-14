#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Stage and verify the Coral recovery-as-boot payload."""

import argparse
import gzip
import hashlib
import io
import os
from pathlib import Path
import stat
import struct
import subprocess
import tempfile
import urllib.request
import zipfile

from recovery2_boot_compare import inspect, align

PAYLOAD = "hecker-recovery.cpio.zst"
FOX_SHA256 = "4905cf65045bfd3d73e6ae9d42e141f6706d0ff4bd1557b556fbb1b1db69eb05"
FOX_URL = ("https://github.com/its-hecker/orangefox_device_google_coral/releases/download/"
           "hecker-recovery2-coral-ramdisk-353ac57/hecker-recovery2-coral.cpio.gz")
FOX_SIZE = 31139092
ARCHIVE_LIMIT = 96 * 1024 * 1024
FOX_INIT = "system/bin/init"
FOX_RECOVERY = "system/bin/recovery"
FOX_THEME = "twres/ui.xml"
FOX_INSTALL_PAGE = "twres/pages/advanced.xml"
FOX_MAIN_PAGE = "twres/pages/main.xml"
FOX_SH = "system/bin/sh"
RECOVERY_SEPOLICY = "sepolicy"
BOOT_PROP = "system/etc/ramdisk/build.prop"
ROM_FSTAB = "first_stage_ramdisk/fstab.sm8150"
ROM_MARKER = b"Starting OrangeFox-based Hecker Recovery"
SOURCE_CATALOGS = {"ramdisk-files.sha256sum", "ramdisk-files.txt"}
MUTATION_PAGES = {FOX_INSTALL_PAGE, "twres/pages/install.xml", "twres/pages/settings.xml"}
REFLASH_ITEMS = (b'<listitem name="{@auto_reflashtwrp_chk}">',
                 b'<listitem name="{@reflash_twrp}">')
FLASH_ZIP = b'<action function="flash">flash_zip</action>'
FASTBOOT = b'<action function="enablefastboot"/>'
NATIVE_FASTBOOT = b'<action function="cmd">/system/bin/reboot fastboot</action>'
SIDELOAD = (b'<action function="set">tw_back=advanced</action>\n'
            b'\t\t\t\t<action function="set">tw_action=adbsideload</action>')
NATIVE_SIDELOAD = b'<action function="cmd">/system/bin/reboot sideload</action>'
REGULAR_FILE_HEADER = (b"070701" + f"{0:08x}{stat.S_IFREG | 0o644:08x}".encode("ascii") +
                       b"00000000" * 2 + b"00000001" + b"00000000" * 8)


def entries(archive):
    """Yield regular newc entries with their original metadata and alignment."""
    position = 0
    names = set()
    symlinks = set()
    while position + 110 <= len(archive):
        if archive[position:position + 6] != b"070701":
            break
        header = archive[position:position + 110]
        mode = int(header[14:22], 16)
        size = int(header[54:62], 16)
        name_size = int(header[94:102], 16)
        name_end = position + 110 + name_size
        if not 1 <= name_size <= 4096 or archive[name_end - 1:name_end] != b"\0":
            break
        name = archive[position + 110:name_end - 1].decode("ascii")
        start = align(name_end, 4)
        end = align(start + size, 4)
        if end > len(archive):
            break
        if name == "TRAILER!!!":
            if size != 0 or any(archive[end:]):
                break
            yield name, mode, header, b""
            return
        parts = name.split("/")
        if any(part in {"", ".", ".."} for part in parts) or name in names:
            raise ValueError(f"Invalid or duplicate recovery path: {name}")
        if any("/".join(parts[:index]) in symlinks for index in range(1, len(parts))):
            raise ValueError(f"Recovery entry traverses a symlink: {name}")
        if stat.S_ISLNK(mode):
            symlinks.add(name)
        elif not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):
            raise ValueError(f"Unsupported recovery entry type: {name}")
        names.add(name)
        yield name, mode, header, archive[start:start + size]
        position = end
    raise ValueError("Invalid or truncated recovery cpio archive")


def load_gzip(source):
    with gzip.GzipFile(fileobj=source) as stream:
        archive = stream.read(ARCHIVE_LIMIT + 1)
    if len(archive) > ARCHIVE_LIMIT:
        raise ValueError("Recovery cpio exceeds 96 MiB")
    return archive


def add_entry(output, name, header, data):
    name_data = name.encode("ascii") + b"\0"
    output.write(header[:54] + f"{len(data):08x}".encode("ascii") + header[62:94] +
                 f"{len(name_data):08x}".encode("ascii") + header[102:])
    output.write(name_data)
    output.write(b"\0" * (-(110 + len(name_data)) % 4))
    output.write(data)
    output.write(b"\0" * (-len(data) % 4))


def patch_payload(archive, output, bootprop, fstab):
    required = MUTATION_PAGES | {FOX_MAIN_PAGE, ROM_FSTAB, FOX_INIT,
                                FOX_RECOVERY, FOX_THEME, FOX_SH, RECOVERY_SEPOLICY}
    found = set()
    for name, _, header, data in entries(archive):
        if name == BOOT_PROP or name in SOURCE_CATALOGS:
            continue
        if name == FOX_INSTALL_PAGE:
            if data.count(SIDELOAD) != 1:
                raise ValueError("Unfamiliar OrangeFox sideload action")
            # Replace the entire action chain so it cannot enter the legacy
            # A/B installer after scheduling an A17 sideload reboot.
            start = data.index(SIDELOAD)
            end = data.index(b'<action function="page">action_page</action>', start)
            end += len(b'<action function="page">action_page</action>')
            data = data[:start] + NATIVE_SIDELOAD + data[end:]
        if name == FOX_MAIN_PAGE:
            if data.count(FASTBOOT) != 1:
                raise ValueError("Unfamiliar OrangeFox fastboot action")
            data = data.replace(FASTBOOT, NATIVE_FASTBOOT)
        if name in MUTATION_PAGES:
            # The combined image is already part of every OTA. OrangeFox's
            # optional self-reflash would overwrite the updated boot slot.
            for tag in REFLASH_ITEMS:
                while tag in data:
                    start = data.index(tag)
                    end = data.index(b"</listitem>", start) + len(b"</listitem>")
                    data = data[:start] + data[end:]
        if name == "twres/pages/install.xml":
            if data.count(FLASH_ZIP) != 2:
                raise ValueError("Unfamiliar OrangeFox ZIP install action")
            data = data.replace(FLASH_ZIP,
                                b'<action function="set">tw_auto_reflashtwrp=0</action>\n'
                                b'\t\t\t\t' + FLASH_ZIP)
        if name == ROM_FSTAB:
            data = fstab
        if name == "TRAILER!!!":
            # A17 init stages the boot build properties for its second stage.
            # OrangeFox still needs its own Android 12.1 system properties.
            add_entry(output, BOOT_PROP, REGULAR_FILE_HEADER, bootprop)
        if name in required:
            found.add(name)
        add_entry(output, name, header, data)
    if found != required:
        raise ValueError("OrangeFox archive is missing: " + ", ".join(sorted(required - found)))


def download(cache):
    if (cache.is_file() and cache.stat().st_size == FOX_SIZE and
            hashlib.sha256(cache.read_bytes()).hexdigest() == FOX_SHA256):
        return
    cache.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(FOX_URL, timeout=90) as response, tempfile.NamedTemporaryFile(
            dir=cache.parent, delete=False) as stream:
        temp = Path(stream.name)
        try:
            data = response.read(FOX_SIZE + 1)
            if len(data) != FOX_SIZE or hashlib.sha256(data).hexdigest() != FOX_SHA256:
                raise ValueError("OrangeFox release ramdisk checksum mismatch")
            stream.write(data)
            stream.flush()
            temp.replace(cache)
        finally:
            temp.unlink(missing_ok=True)


def install(zstd, source, root):
    with source.open("rb") as stream:
        compressed = stream.read(FOX_SIZE + 1)
    if len(compressed) != FOX_SIZE or hashlib.sha256(compressed).hexdigest() != FOX_SHA256:
        raise ValueError("OrangeFox release ramdisk checksum mismatch")
    archive = load_gzip(io.BytesIO(compressed))
    fstab = root / ROM_FSTAB
    bootprop = fstab.parent / BOOT_PROP
    with tempfile.NamedTemporaryFile(dir=root) as cpio, tempfile.NamedTemporaryFile(
            dir=root, delete=False) as packed:
        destination = Path(packed.name)
        try:
            patch_payload(archive, cpio, bootprop.read_bytes(), fstab.read_bytes())
            cpio.flush()
            cpio.seek(0)
            subprocess.run([zstd, "-q", "-19", "-c", "--stream-size=" + str(os.fstat(cpio.fileno()).st_size)],
                           stdin=cpio, stdout=packed, check=True)
            destination.chmod(0o644)
            destination.replace(root / PAYLOAD)
        finally:
            destination.unlink(missing_ok=True)


def embedded(image):
    info = inspect(image)
    if (info["os_version"].split(".")[0] != "17" or not info["avb_footer_present"] or
            info["ramdisk_compression"] != "gzip"):
        raise ValueError("Expected Coral Android 17 boot image with its AVB footer")
    with image.open("rb") as stream:
        stream.seek(info["page_size"] + align(info["kernel_size"], info["page_size"]))
        archive = load_gzip(io.BytesIO(stream.read(info["ramdisk_size"])))
    files = {name: data for name, _, _, data in entries(archive)}
    if (ROM_MARKER not in files.get(FOX_INIT, b"") or ROM_FSTAB not in files or
            "first_stage_ramdisk/" + BOOT_PROP not in files or
            FOX_RECOVERY not in files or not files.get(PAYLOAD, b"").startswith(b"\x28\xb5\x2f\xfd")):
        raise ValueError("Boot ramdisk is missing Android 17 init, Coral first-stage fstab or OrangeFox")
    return files


def verify(zstd, avbtool, extractor, image, ota):
    outer = embedded(image)
    subprocess.run([avbtool, "verify_image", "--image", str(image)], check=True)
    fox = subprocess.check_output([zstd, "-q", "-d", "-c"], input=outer[PAYLOAD])
    files = {name: data for name, _, _, data in entries(fox)}
    if (not files.get(FOX_INIT, b"").startswith(b"\x7fELF") or
            not files.get(FOX_RECOVERY, b"").startswith(b"\x7fELF") or
            FOX_THEME not in files or NATIVE_SIDELOAD not in files.get(FOX_INSTALL_PAGE, b"") or
            NATIVE_FASTBOOT not in files.get(FOX_MAIN_PAGE, b"") or
            files.get(ROM_FSTAB) != outer[ROM_FSTAB] or
            files.get(BOOT_PROP) != outer["first_stage_ramdisk/" + BOOT_PROP]):
        raise ValueError("Invalid OrangeFox content in boot.img")

    with tempfile.TemporaryDirectory() as directory, ota.open("rb") as raw, zipfile.ZipFile(raw) as package:
        item = package.getinfo("payload.bin")
        if item.compress_type != zipfile.ZIP_STORED:
            raise ValueError("Expected a stored A/B OTA payload")
        raw.seek(item.header_offset)
        header = raw.read(30)
        if not header.startswith(b"PK\x03\x04"):
            raise ValueError("Invalid OTA payload header")
        name, extra = struct.unpack_from("<HH", header, 26)
        offset = item.header_offset + 30 + name + extra
        subprocess.run([extractor, "--payload=" + str(ota), "--payload_offset=" + str(offset),
                        "--partitions=boot", "--output_dir=" + directory], check=True)
        expected = hashlib.sha256(image.read_bytes()).digest()
        actual = hashlib.sha256((Path(directory) / "boot.img").read_bytes()).digest()
        if actual != expected:
            raise ValueError("The OTA boot partition differs from the verified dual-mode boot image")
    print("Coral boot image and A/B OTA contain the same verified OrangeFox payload")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest="action", required=True)
    fetch = actions.add_parser("download")
    fetch.add_argument("cache", type=Path)
    stage = actions.add_parser("install")
    stage.add_argument("zstd")
    stage.add_argument("source", type=Path)
    stage.add_argument("root", type=Path)
    check = actions.add_parser("verify")
    check.add_argument("zstd")
    check.add_argument("avbtool")
    check.add_argument("extractor")
    check.add_argument("image", type=Path)
    check.add_argument("ota", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "download":
            download(args.cache)
        elif args.action == "install":
            install(args.zstd, args.source, args.root)
        else:
            verify(args.zstd, args.avbtool, args.extractor, args.image, args.ota)
    except (OSError, ValueError, zipfile.BadZipFile, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Hecker Recovery 2.0: {error}\n")


if __name__ == "__main__":
    main()
