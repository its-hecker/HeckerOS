// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <string>

namespace hecker::vpn {
// Only fixed messages reach the client; arbitrary helper output may contain keys.
inline std::string setup_error(const std::string& stage) {
    if (stage == "interface") return "Could not create the WireGuard interface. Check that the installed kernel matches this ROM.";
    if (stage == "peer") return "Could not configure the VPN peer or resolve its endpoint. Check the saved profile and network DNS.";
    if (stage == "network") return "Android could not create the VPN network. Check the network service and firewall setup.";
    if (stage == "address") return "Could not assign the VPN address. Check the profile address settings.";
    if (stage == "dns") return "Android could not configure the VPN DNS servers. Check the profile DNS settings.";
    if (stage == "routes" || stage == "mtu") return "Could not configure VPN routes or MTU. Check AllowedIPs and MTU in the profile.";
    if (stage == "apps") return "Could not configure app routing. Review the selected applications.";
    return "WireGuard setup failed before the tunnel was ready. Retry with the matching ROM and kernel.";
}
}
