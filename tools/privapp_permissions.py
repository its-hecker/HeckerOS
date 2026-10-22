# SPDX-License-Identifier: Apache-2.0
"""Reject Suite's boot-fatal permission omissions against the selected framework."""
from pathlib import Path
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"


def check_privapp_permissions(platform_manifest, suite_root):
    """Use real platform protection levels, rather than a second permission list.

    Both Suite and its allowlist install to system_ext (Android.bp). Android also
    accepts an explicit deny-permission decision; it never implies a grant.
    """
    suite_root = Path(suite_root)
    platform = ET.parse(platform_manifest).getroot()
    app = ET.parse(suite_root / "app/AndroidManifest.xml").getroot()
    requested = {node.get(A + "name") for node in app.findall("uses-permission")}
    required = {node.get(A + "name") for node in platform.findall("permission")
                if "privileged" in node.get(A + "protectionLevel", "").split("|")}
    path = suite_root / "configs/privapp-permissions-hecker-suite.xml"
    blocks = [node for node in ET.parse(path).getroot().findall("privapp-permissions")
              if node.get("package") == app.get("package")]
    if len(blocks) != 1:
        raise ValueError("Suite needs exactly one privileged permission allowlist block: " + str(path))
    granted = {node.get("name") for node in blocks[0].findall("permission")}
    denied = {node.get("name") for node in blocks[0].findall("deny-permission")}
    if granted & denied:
        raise ValueError("Conflicting Suite permission decisions: " + ", ".join(sorted(granted & denied)))
    missing = (requested & required) - granted - denied
    if missing:
        raise ValueError("HeckerSuite would prevent Android from booting; missing system_ext "
                         "privileged permission decisions: " + ", ".join(sorted(missing)))
    return requested & required
