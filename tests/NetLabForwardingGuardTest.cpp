// SPDX-License-Identifier: Apache-2.0
// A fake firewall executes the production ownership and rollback logic, with no kernel writes.
#include "forwarding_guard.h"
#include <algorithm>
#include <cassert>
#include <csignal>
#include <fstream>
#include <iostream>
#include <sstream>
#include <sys/wait.h>
#include <unistd.h>
using namespace hecker::lan;
struct Firewall {
    bool exists = false, failSecondJump = false;
    std::vector<std::string> forward{"-A FORWARD -j existing_hotspot"}, own;
    std::vector<std::vector<std::string>> calls;
    std::string chain = "HKNETLAB_123";
    FirewallResult operator()(const std::vector<std::string>& a) {
        calls.push_back(a);
        auto join = [](std::vector<std::string> parts) { std::string s; for (const auto& p : parts) { if (!s.empty()) s += " "; s += p; } return s; };
        if (a[0] == "-N") { if (exists) return {1, {}}; exists = true; return {0, {}}; }
        if (a[0] == "-A") { own.push_back(join(a)); return {0, {}}; }
        if (a[0] == "-I") {
            if (failSecondJump && a[2] == "2") return {1, {}};
            auto rule = a; rule[0] = "-A"; rule.erase(rule.begin() + 2);
            forward.insert(forward.begin() + std::stoi(a[2]) - 1, join(rule)); return {0, {}};
        }
        if (a[0] == "-S") { if (a[1] != "FORWARD" && !exists) return {1, {}}; std::string text; for (const auto& rule : a[1] == "FORWARD" ? forward : own) text += rule + "\n"; return {0, text}; }
        if (a[0] == "-D") { auto rule = a; rule[0] = "-A"; auto it = std::find(forward.begin(), forward.end(), join(rule)); if (it != forward.end()) forward.erase(it); return {0, {}}; }
        if (a[0] == "-F") { assert(a[1] == chain); own.clear(); return {0, {}}; }
        if (a[0] == "-X") { assert(a[1] == chain); exists = false; return {0, {}}; }
        assert(false); return {1, {}};
    }
};
int main() {
    Firewall firewall;
    {
        ForwardingGuard guard("wlan0", firewall.chain, std::ref(firewall));
        guard.install({"192.168.1.9", "192.168.1.12"});
        assert(guard.intact() && firewall.own.size() == 4 && firewall.forward.size() == 3);
        // An earlier ACCEPT, deleted guard or changed peer rule must stop control.
        firewall.forward.insert(firewall.forward.begin(), "-A FORWARD -j ACCEPT"); assert(!guard.intact());
        firewall.forward.erase(firewall.forward.begin()); assert(guard.intact());
        firewall.own.pop_back(); assert(!guard.intact());
    }
    assert(!firewall.exists && firewall.own.empty() && firewall.forward == std::vector<std::string>{"-A FORWARD -j existing_hotspot"});
    firewall.failSecondJump = true;
    try { ForwardingGuard guard("wlan0", firewall.chain, std::ref(firewall)); guard.install({"192.168.1.9"}); assert(false); }
    catch (const std::runtime_error&) {}
    assert(!firewall.exists && firewall.forward.size() == 1);
    firewall.failSecondJump = false; firewall.exists = true; size_t before = firewall.calls.size();
    try { ForwardingGuard guard("wlan0", firewall.chain, std::ref(firewall)); guard.install({"192.168.1.9"}); assert(false); }
    catch (const std::runtime_error&) {}
    assert(firewall.exists && firewall.calls.size() == before + 1); // Unfamiliar chain is never claimed/flushed.
    try { ForwardingGuard guard("wlan0;bad", firewall.chain, std::ref(firewall)); assert(false); }
    catch (const std::invalid_argument&) {}
    for (const auto& command : firewall.calls) {
        assert(command[0] != "-P");
        if (command[0] == "-F" || command[0] == "-X") assert(command[1] == firewall.chain);
        for (const auto& argument : command) assert(argument != "nat" && argument != "sysctl");
    }
    for (bool killed : {false, true}) {
        char name[] = "/tmp/hecker-guard-test-XXXXXX"; int log = mkstemp(name); assert(log >= 0); close(log);
        int ready[2]; assert(pipe(ready) == 0);
        pid_t relay = fork(); assert(relay >= 0);
        if (relay == 0) {
            close(ready[0]); Firewall isolated;
            ForwardingGuard guard("wlan0", isolated.chain, [&](const std::vector<std::string>& command) {
                auto result = isolated(command);
                if (command[0] == "-X") { std::ofstream file(name, std::ios::app); file << "removed\n"; }
                return result;
            });
            guard.install({"192.168.1.9"}); guard.supervise(1);
            assert(write(ready[1], "R", 1) == 1); close(ready[1]);
            if (killed) { for (;;) pause(); }
            assert(guard.clear()); _exit(0);
        }
        close(ready[1]); char signal; assert(read(ready[0], &signal, 1) == 1); close(ready[0]);
        if (killed) kill(relay, SIGKILL);
        int status; assert(waitpid(relay, &status, 0) == relay);
        bool removed = false;
        for (int tries = 0; tries < 100 && !removed; ++tries) {
            std::ifstream file(name); std::string line; removed = bool(std::getline(file, line)) && line == "removed";
            if (!removed) usleep(20000);
        }
        assert(removed); unlink(name);
    }
    std::cout << "NetLab cleanup guardian: normal stop and SIGKILL of the relay remove only the mock-owned rules PASS\n";
    std::cout << "NetLab forwarding guard: selected peers only, no global changes, precedence loss, rollback and ownership PASS\n";
}
