# Network Lab: simple device controls

1. Open Network Lab and tap **Scan Wi-Fi**. Grant local-network and KernelSU access when requested. The scan also collects service-advertised names when available.
2. Find the device by name or IP. Each row shows whether it is a Wi-Fi peer or a phone-hotspot client, plus its current status. Unknown names are labelled honestly.
3. Tap **Manage**. Wi-Fi peers offer **Pause internet (IPv4)** and **Speed limit**; phone-hotspot clients use the supported native pause action. The current router is informational.
4. Choose a duration. Speed limits have independent upload/download values in kbps. Zero leaves one direction unlimited.
5. Use **Restore** on a controlled device, **Stop and restore** on the session card, or **More → Restore all devices**.

**More** also contains multi-device controls, traffic, network information, history/options, diagnostics and advanced root controls. These remain available without crowding the initial device list. Saved default rates are edited when applying a device limit.

Root Wi-Fi controls affect selected IPv4 gateway traffic. They do not disconnect Wi-Fi association, control IPv6/direct local traffic, or override router protections. Status remains Waiting for traffic until the helper observes the selected traffic. A scan does not establish complete network inventory or verified device identity.

The existing single-session backend still applies: stop the running session before starting another selection. Scanned identities are checked again before control, and unchanged signature/caller/local-network guards protect every request. The presentation adds no generic root command surface or new network backend.

Validation: Suite/controller Java, Android resource linking, generated AIDL, source checks and existing host/integration regressions. Full ROM/Soong compilation and visual/touch/font-size/rotation tests on Coral remain pending.
