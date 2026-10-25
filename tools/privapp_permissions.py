# SPDX-License-Identifier: Apache-2.0
"""Reject boot-fatal permission omissions against the selected framework."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"


def check_package_permissions(platform_manifest, package, requested, allowlist, label):
    platform = ET.parse(platform_manifest).getroot()
    required = {node.get(A + "name") for node in platform.findall("permission")
                if "privileged" in node.get(A + "protectionLevel", "").split("|")}
    path = Path(allowlist)
    blocks = [node for node in ET.parse(path).getroot().findall("privapp-permissions")
              if node.get("package") == package]
    if len(blocks) != 1:
        raise ValueError(label + " needs exactly one privileged permission allowlist block: " + str(path))
    granted = {node.get("name") for node in blocks[0].findall("permission")}
    denied = {node.get("name") for node in blocks[0].findall("deny-permission")}
    if granted & denied:
        raise ValueError("Conflicting " + label + " permission decisions: " + ", ".join(sorted(granted & denied)))
    missing = (requested & required) - granted - denied
    if missing:
        raise ValueError(label + " would prevent Android from booting; missing system_ext "
                         "privileged permission decisions: " + ", ".join(sorted(missing)))
    return requested & required


def check_privapp_permissions(platform_manifest, suite_root):
    """Use real platform protection levels, rather than a second permission list.

    Both Suite and its allowlist install to system_ext (Android.bp). Android also
    accepts an explicit deny-permission decision; it never implies a grant.
    """
    suite_root = Path(suite_root)
    app = ET.parse(suite_root / "app/AndroidManifest.xml").getroot()
    requested = {node.get(A + "name") for node in app.findall("uses-permission")}
    path = suite_root / "configs/privapp-permissions-hecker-suite.xml"
    return check_package_permissions(platform_manifest, app.get("package"), requested, path, "HeckerSuite")


def check_feature_privapp_permissions(platform_manifest, suite_root):
    """Audit both owned APKs and the exact permission additions injected into Launcher3.

    Upstream Launcher3 permissions keep their upstream allowlist. EXTRA_PERMISSIONS
    is shared with manifest generation, so this check cannot silently miss a new
    launcher extension. Verify the added permission files' real Soong partition too.
    """
    from aosp_launcher_port import EXTRA_PERMISSIONS
    root = Path(suite_root)
    result = {"org.hecker.suite": check_privapp_permissions(platform_manifest, root)}
    controller = ET.parse(root / "netlab/AndroidManifest.xml").getroot()
    requested = {p.get(A + "name") for p in controller.findall("uses-permission")}
    result[controller.get("package")] = check_package_permissions(platform_manifest,
        controller.get("package"), requested, root / "configs/privapp-permissions-hecker-netlab.xml", "HeckerNetLabController")
    result["com.android.launcher3"] = check_package_permissions(platform_manifest,
        "com.android.launcher3", set(EXTRA_PERMISSIONS), root / "configs/privapp-permissions-hecker-launcher.xml", "HeckerLauncher additions")
    blueprint = (root / "Android.bp").read_text()
    blocks = re.findall(r'prebuilt_etc\s*\{([^{}]*)\}', blueprint)
    for module, name in (("HeckerLauncherPermissions", "launcher"), ("HeckerNetLabPermissions", "netlab")):
        matches = [block for block in blocks if re.search(r'\bname:\s*"' + module + '"', block)]
        if len(matches) != 1 or not re.search(r'\bsystem_ext_specific:\s*true', matches[0]) \
                or '"configs/privapp-permissions-hecker-' + name + '.xml"' not in matches[0] \
                or not re.search(r'\bsub_dir:\s*"permissions"', matches[0]):
            raise ValueError(module + " must install its exact allowlist to system_ext/etc/permissions")
    return result
