# SPDX-License-Identifier: Apache-2.0
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import pakistan_ims as ims


def carrier(name, apns=b"", configs=b"", version=None):
    value = ims.encode(1, 2, name.encode())
    if apns:
        value += ims.encode(3, 2, apns)
    if configs:
        value += ims.encode(4, 2, configs)
    if version is not None:
        value += ims.encode(2, 0, version)
    return value


def carrier_list(entries=None):
    return b"".join(ims.encode(1, 2,
        ims.encode(1, 2, name.encode()) + b"".join(
            ims.encode(2, 2, ims.encode(1, 2, plmn.encode())) for plmn in plmns))
        for name, plmns in (entries or {p: [p] for p in ims.policy()["carriers"]}).items())


def records(blob):
    return {ims.text(f.value, 1): f.value for f in ims.fields(blob) if f.number == 2}


class PakistanImsTest(unittest.TestCase):
    def setUp(self):
        # A dedicated existing IMS APN with custom authentication must survive.
        self.custom_apn = bytes.fromhex("120e0a03494d531203696d7318075003")
        self.opaque = ims.encode(100, 2, b"opaque\x00extension")
        self.unrelated = carrier("23415", self.custom_apn) + self.opaque
        self.input = ims.encode(1, 0, 40_000_000_689) + b"".join(
            ims.encode(2, 2, carrier(p)) for p in ims.policy()["carriers"])
        self.input += ims.encode(2, 2, self.unrelated) + self.opaque

    def test_exact_sim_scope_and_untouched_data(self):
        result = ims.patch_others(self.input, carrier_list())
        before, after = records(self.input), records(result)
        self.assertEqual(after["23415"], before["23415"])
        self.assertTrue(result.endswith(self.opaque))
        for plmn in ims.policy()["carriers"]:
            values = {}
            for field in ims.fields(ims.one(after[plmn], 4, 2)):
                if field.number == 2:
                    values[ims.text(field.value, 1)] = next(
                        f.value for f in ims.fields(field.value) if f.number != 1)
            self.assertEqual(values["carrier_volte_available_bool"], 1)
            self.assertEqual(values["carrier_wfc_ims_available_bool"], 1)
            self.assertEqual(values["carrier_default_wfc_ims_enabled_bool"], 1)
            self.assertEqual(values["hide_enhanced_4g_lte_bool"], 0)
            self.assertFalse(any("provision" in k or "emergency" in k for k in values))

    def test_versions_must_increase_from_effective_cached_version(self):
        result = ims.patch_others(self.input, carrier_list())
        self.assertEqual(ims.one(result, 1, 0), 40_000_000_690)
        for name, value in records(result).items():
            if name in ims.policy()["carriers"]:
                self.assertEqual(ims.one(value, 2, 0), 40_000_000_690)
        self.assertEqual(ims.patch_others(result, carrier_list()), result)

    def test_existing_ims_apn_and_unknown_fields_are_preserved(self):
        # Construct a valid CarrierApns record independently of the production writer.
        apn = bytes.fromhex("0a03494d531203696d7318075003")
        apns = bytes([0x12, len(apn)]) + apn + self.opaque
        result = ims.patch_apns(apns, ims.policy()["carriers"]["41001"])
        self.assertEqual(result, apns)
        # Existing provisioning settings are left in place, including a true requirement.
        config = ims.encode(2, 2, ims.encode(1, 2,
            b"carrier_volte_provisioning_required_bool") + b"\x28\x01")
        self.assertIn(config, ims.patch_configs(config + self.opaque, ims.policy()))
        self.assertIn(self.opaque, ims.patch_configs(config + self.opaque, ims.policy()))

    def test_new_ims_apn_never_becomes_the_internet_apn(self):
        result = ims.patch_others(self.input, carrier_list())
        for plmn, value in records(result).items():
            if plmn not in ims.policy()["carriers"]:
                continue
            apns = [f.value for f in ims.fields(ims.one(value, 3, 2)) if f.number == 2]
            self.assertEqual(len(apns), 1)
            self.assertEqual(ims.apn_types(apns[0]), [7])
            self.assertEqual(ims.text(apns[0], 2), "ims")
            self.assertEqual(ims.one(apns[0], 23, 0), 0)
            self.assertEqual(ims.one(apns[0], 24, 0), 0)

    def test_shared_foreign_carrier_and_missing_mappings_fail_closed(self):
        mapping = {p: [p] for p in ims.policy()["carriers"]}
        mapping["41001"].append("23415")
        with self.assertRaisesRegex(ValueError, "other SIMs"):
            ims.patch_others(self.input, carrier_list(mapping))
        del mapping["41001"]
        with self.assertRaisesRegex(ValueError, "mappings missing"):
            ims.patch_others(self.input, carrier_list(mapping))

    def test_corrupt_protobuf_and_duplicate_records_are_rejected(self):
        for malformed in [b"\x12\x80", b"\x12\x05ab", b"\x00", b"\x0b", b"\x08" + b"\xff" * 10]:
            with self.subTest(data=malformed), self.assertRaises(ValueError):
                ims.fields(malformed)
        with self.assertRaisesRegex(ValueError, "Duplicate Pakistan"):
            ims.patch_others(self.input + ims.encode(2, 2, carrier("41001")), carrier_list())

    def test_cli_cannot_verify_an_absent_vendor_checkout(self):
        with tempfile.TemporaryDirectory() as temporary:
            result = subprocess.run([sys.executable, str(ROOT / "tools/pakistan_ims.py"),
                "--rom-root", temporary, "--verify"], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("No Floral vendor", result.stderr)

    def test_real_git_plan_apply_verify_revert_and_custom_edits(self):
        with tempfile.TemporaryDirectory() as temporary:
            rom = Path(temporary)
            vendor = rom / "vendor/google/flame"
            assets = vendor / ims.ASSETS
            assets.mkdir(parents=True)
            (assets / "others.pb").write_bytes(self.input)
            (assets / "carrier_list.pb").write_bytes(carrier_list())
            for args in [["init", "-q"], ["add", "."],
                         ["-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
                          "commit", "-qm", "fixture"]]:
                subprocess.run(["git", *args], cwd=vendor, check=True, capture_output=True)
            plan = ims.pakistan_ims_plan(rom)
            self.assertEqual(set(plan), {assets / "others.pb"})
            for path, value in plan.items():
                path.write_bytes(value)
            self.assertEqual(ims.pakistan_ims_plan(rom), {})
            reverse = ims.pakistan_ims_plan(rom, revert=True)
            self.assertEqual(reverse[assets / "others.pb"], self.input)
            (assets / "others.pb").write_bytes(self.input + self.opaque)
            with self.assertRaisesRegex(ValueError, "unfamiliar vendor"):
                ims.pakistan_ims_plan(rom)


if __name__ == "__main__":
    unittest.main()
