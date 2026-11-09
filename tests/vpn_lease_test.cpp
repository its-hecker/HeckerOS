// SPDX-License-Identifier: Apache-2.0
#define main hecker_daemon_main
#include "../vpn/native/daemon.cpp"
#undef main
#include <cassert>
#include <iostream>
int main() {
    int lease[2];
    assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, lease) == 0);
    lease_fd = lease[1];
    assert(!lease_closed());
    char value = 'x', received = 0;
    assert(send(lease[0], &value, 1, 0) == 1);
    assert(io(lease_fd, &received, 1, false) && received == value);
    assert(shutdown(lease[0], SHUT_WR) == 0);
    assert(lease_closed());

    const auto before = std::chrono::steady_clock::now();
    assert(run("/bin/sleep", {"10"}).code == -1);
    assert(std::chrono::steady_clock::now() - before < std::chrono::seconds(2));
    assert(run("/bin/true", {}, "", false, true).code == 0);
    assert(request("TCP_UP\n{}").find("Unsupported operation") != std::string::npos);
    assert(request("TCP_PROBE").find("Unsupported operation") != std::string::npos);
    close(lease[0]); close(lease[1]);
    lease_fd = -1;
    std::cout << "Native controller lease: helper cancellation, cleanup after half-close and retired operation rejection PASS\n";
}
