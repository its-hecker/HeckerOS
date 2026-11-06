// SPDX-License-Identifier: Apache-2.0
#include "protocol.h"
#include "setup_status.h"
#include <cassert>
#include <iostream>
int main() {
    using namespace hecker::vpn;
    std::string config = "[Interface]\nPrivateKey=AQ==\nAddress=10.0.0.2/32\nIncludedApplications=com.example.test\n[Peer]\nPublicKey=AQ==\nAllowedIPs=0.0.0.0/0\nEndpoint=192.0.2.1:51820\n";
    assert(valid_config(config));
    unsigned int netid=0;std::string decoded;
    assert(parse_up("UP\n101\n"+config,netid,decoded)&&netid==101&&decoded==config);
    assert(parse_up("UP\n65535\n"+config,netid,decoded)&&netid==65535);
    for(const std::string bad:{"", "0", "99", "65536", "100000", "-1", "101;id", "101 102", "0x65", "101\nPostUp=id"})
        assert(!parse_up("UP\n"+bad+"\n"+config,netid,decoded));
    assert(!parse_up("UP\n"+config,netid,decoded));
    assert(authorized_peer(10001, "u:r:hecker_vpn_app:s0"));
    assert(!authorized_peer(1010001, "u:r:hecker_vpn_app:s0"));
    assert(!authorized_peer(10001, "u:r:platform_app:s0"));
    assert(!authorized_peer(10001, "u:r:hecker_vpn_app_evil:s0"));
    assert(!authorized_peer(0, "u:r:hecker_vpn_app:s0"));
    for (const std::string bad : {"PostUp=id\n", "Address=10.0.0.1;id\n", "DNS=$(id)\n", "DNS=`id`\n", "MTU=1 2\n", "IncludedApplications=\n", "ExcludedApplications=com.example.foo\n"}) {
        auto poisoned = config; poisoned.insert(poisoned.find("[Peer]"), bad); assert(!valid_config(poisoned));
    }
    assert(!valid_config(config + std::string(1, '\0')));
    assert(!valid_config(std::string(65537, 'x')));
    assert(!valid_config("[Peer]\n" + config));
    assert(setup_error("peer").find("endpoint") != std::string::npos);
    assert(setup_error("dns").find("DNS") != std::string::npos);
    assert(setup_error("interface") != setup_error("network"));
    for (const std::string stage : {"interface", "peer", "network", "address", "dns", "routes", "mtu", "apps", "ready"})
        assert(!setup_error(stage).empty());
    const std::string secret = "PrivateKey=NEVER_ECHO_THIS";
    assert(setup_error(secret).find(secret) == std::string::npos);
    std::cout << "HeckerVPN native authorization and canonical config boundary: PASS\n";
}
