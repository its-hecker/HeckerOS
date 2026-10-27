// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <functional>
#include <string>
#include <vector>
#include <sys/types.h>

namespace hecker::lan {
struct FirewallResult { int status; std::string output; };
using FirewallRunner = std::function<FirewallResult(const std::vector<std::string>&)>;
FirewallResult runFirewall(const std::vector<std::string>& arguments);

/** Drop only selected peers in the kernel FORWARD path; raw relay uses neither jump. */
class ForwardingGuard {
    std::string iface_, chain_;
    std::vector<std::string> peers_;
    FirewallRunner runner_;
    bool owned_ = false, incoming_ = false, outgoing_ = false;
    int lease_ = -1;
    pid_t guardian_ = -1;
    void require(const std::vector<std::string>& arguments);
public:
    ForwardingGuard(std::string iface, std::string chain, FirewallRunner runner = runFirewall);
    ~ForwardingGuard();
    ForwardingGuard(const ForwardingGuard&) = delete;
    ForwardingGuard& operator=(const ForwardingGuard&) = delete;
    void install(const std::vector<std::string>& peers);
    bool intact();
    // A separate bounded process removes this guard even if the relay is killed.
    void supervise(unsigned seconds);
    bool clear();
};
}
