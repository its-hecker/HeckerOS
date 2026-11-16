#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Source checks for the optional HeckerOS 2.0 kernel/device trial.

Checks wiring and bounded policies; does not prove boot, FPS or battery gains.
Run from any directory:
python3 vendor/hecker/floral/suite/tools/check_kernel_device_phase2.py --rom-root "$PWD"
"""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

SUITE = Path(__file__).resolve().parents[1]


def require(condition, description):
    if not condition:
        raise AssertionError(description)


def check_about():
    common = (SUITE / "rom/common.mk").read_text()
    require("ro.heckeros.version=2.0" in common, "ROM version is not 2.0")
    for subdirectory in ("values", "values-night"):
        colors = SUITE / "settings/res" / subdirectory / "hecker_about_colors.xml"
        palette = {node.attrib["name"]: (node.text or "") for node in ET.parse(colors).getroot()}
        for key in ("background", "surface", "hero_start", "hero_end", "text", "secondary", "icon", "divider"):
            name = "hecker_about_" + key
            require(palette.get(name, "").startswith("@android:color/system_"),
                    f"{subdirectory}: {name} is not a Monet color")
    root = ET.parse(SUITE / "settings/res/layout/hecker_about_phone.xml").getroot()
    ns = "{http://schemas.android.com/apk/res/android}"
    actions = [view for view in root.iter()
               if view.attrib.get(ns + "id", "").endswith("/hecker_about_name_action")]
    require(len(actions) == 1, "About Phone name action missing or duplicated")
    require(actions[0].attrib.get(ns + "background") == "@drawable/hecker_about_surface",
            "About Phone name card must use a tonal surface")
    print("PASS: ROM 2.0 property and wallpaper-derived MD3 About colors/cards")


def check_device_kernel(root):
    device = root / "device/google/coral"
    kernel = root / "kernel/google/msm-4.14"
    if not device.is_dir() or not kernel.is_dir():
        print("SKIP: full device/kernel checkout unavailable")
        return
    cfg = json.loads((device / "configs/power/powerhint.json").read_text())
    nodes = {node["Name"]: set(node["Values"]) for node in cfg["Nodes"]}
    for action in cfg["Actions"]:
        require(action["Node"] in nodes, f"missing PowerHAL node {action['Node']}")
        require(action["Value"] in nodes[action["Node"]],
                f"unsupported PowerHAL value {action['Node']}={action['Value']}")
    launch = [action for action in cfg["Actions"] if action["PowerHint"] == "LAUNCH"]
    require(len(launch) == 16, "unexpected LAUNCH hint count")
    require(not any(a["Node"] == "EnergyAware" for a in launch),
            "LAUNCH should not disable energy-aware scheduling")
    require(all(0 < a["Duration"] <= 850 for a in launch), "unbounded LAUNCH hint")
    camera = [action for action in cfg["Actions"] if action["PowerHint"] == "CAMERA_LAUNCH"]
    require(len(camera) == 9 and all(a["Duration"] == 1000 for a in camera),
            "camera launch baseline must remain unchanged")
    init = (device / "init/init.hardware.rc").read_text()
    require("write /dev/cpuset/foreground/cpus 0-6" in init, "foreground cpuset mismatch")
    require("write /dev/cpuset/top-app/cpus 0-7" in init, "top-app cpuset changed")
    require("write /sys/block/zram0/comp_algorithm lz4" in init, "zRAM configuration changed")
    governor = (kernel / "kernel/sched/cpufreq_schedutil.c").read_text()
    for text in ("previous_next_freq", "previous_update_time",
                 "sg_policy->next_freq = previous_next_freq;",
                 "sg_policy->last_freq_update_time = previous_update_time;",
                 "sg_policy->cached_raw_freq = 0;"):
        require(text in governor, f"schedutil retry state missing {text}")
    # Incremental efficiency trial: static checks only, not a performance claim.
    for cpu in (0, 4, 7):
        path = f"/sys/devices/system/cpu/cpu{cpu}/cpufreq/schedutil/down_rate_limit_us"
        require(f"write {path} 10000" in init, f"CPU{cpu} downscale is not 10ms")
    for lun in ("sda", "sde", "sdf"):
        require(f"write /sys/block/{lun}/queue/scheduler deadline" in init,
                f"deadline trial missing on {lun}")
        require(f"write /sys/block/{lun}/queue/scheduler cfq" not in init,
                f"CFQ still enabled on {lun}")
    require("write /sys/block/sda/queue/read_ahead_kb 128" in init,
            "UFS read-ahead trial missing")
    fstab = (device / "init/fstab.hardware").read_text()
    require("zramsize=3221225472,max_comp_streams=8,zram_backingdev_size=512M" in fstab,
            "3 GiB zRAM trial or existing writeback parameters changed")
    defconfig = (kernel / "arch/arm64/configs/floral_defconfig").read_text()
    required = (
        "CONFIG_WQ_POWER_EFFICIENT_DEFAULT=y",
        "CONFIG_TCP_CONG_ADVANCED=y", "CONFIG_TCP_CONG_BBR=y",
        "CONFIG_TCP_CONG_CUBIC=y", "CONFIG_DEFAULT_BBR=y",
        'CONFIG_DEFAULT_TCP_CONG="bbr"',
        "CONFIG_NET_SCH_FQ=y", "CONFIG_NET_SCH_FQ_CODEL=y",
        "CONFIG_NET_SCH_DEFAULT=y", "CONFIG_DEFAULT_FQ=y",
        'CONFIG_DEFAULT_NET_SCH="fq"',
        "# CONFIG_KGDB is not set", "# CONFIG_KGDB_KDB is not set",
        "CONFIG_SCHEDSTATS=y", "CONFIG_DYNAMIC_DEBUG=y",
        "CONFIG_PSI=y", "CONFIG_CFI_CLANG=y",
    )
    for item in required:
        require(item in defconfig, f"Missing kernel trial option: {item}")
    print("PASS: 10ms downscale, deadline UFS, 3GiB LZ4 zRAM, workqueues and BBR/fq kernel config")
    print("PASS: bounded Power HAL, preserved camera/zRAM/cpusets and failed-DVFS recovery")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rom-root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    check_about()
    check_device_kernel(args.rom_root.resolve())
    print("PASS: static checks only; Perfetto/ROM build and on-device A/B tests are required")


if __name__ == "__main__":
    main()
