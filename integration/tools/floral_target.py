# SPDX-License-Identifier: Apache-2.0
"""Resolve the Floral build target without changing the common device-tree path."""

import os
from pathlib import Path
import xml.etree.ElementTree as ET


VENDOR_REPOSITORIES = {
    "coral": "its-hecker/heckeros-coral-vendor",
    "flame": "its-hecker/HeckerOs-Flame-vendor",
}


def manifest_device(root):
    xml = ET.parse(Path(root) / "floral.xml").getroot()
    devices = [device for device in VENDOR_REPOSITORIES
               if xml.find("project[@path='vendor/google/" + device + "']") is not None]
    if len(devices) != 1:
        raise ValueError("Select exactly one Coral or Flame vendor in floral.xml")
    return devices[0]


def selected_device(root=None):
    root = Path(root) if root is not None else Path(__file__).resolve().parents[1]
    device = os.environ.get("HECKER_DEVICE") or manifest_device(root)
    if device not in VENDOR_REPOSITORIES:
        raise ValueError("HECKER_DEVICE must be coral or flame")
    return device


DEVICE = selected_device()
VENDOR = "vendor/google/" + DEVICE
PRODUCT = "vendor/hecker/floral/suite/rom/products/hecker_" + DEVICE + ".mk"
