// SPDX-License-Identifier: Apache-2.0
#define main hecker_daemon_main
#include "../vpn/native/daemon.cpp"
#undef main
#include <cassert>
#include <iostream>
// Loopback sockets exercise poll/half-close without needing an Android init socket.
std::pair<int, int> pair() {
    int listener = socket(AF_INET, SOCK_STREAM, 0); assert(listener >= 0);
    sockaddr_in address{}; address.sin_family = AF_INET; address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    assert(!bind(listener, reinterpret_cast<sockaddr*>(&address), sizeof(address)) && !listen(listener, 1));
    socklen_t length = sizeof(address); assert(!getsockname(listener, reinterpret_cast<sockaddr*>(&address), &length));
    int sender = socket(AF_INET, SOCK_STREAM, 0); assert(sender >= 0);
    assert(!connect(sender, reinterpret_cast<sockaddr*>(&address), sizeof(address)));
    int receiver = accept(listener, nullptr, nullptr); assert(receiver >= 0); close(listener);
    return {sender, receiver};
}
int main() {
    auto lease = pair(), worker = pair(); lease_fd = lease.second; tcp_fd = worker.second;
    assert(!lease_closed());
    char value = 'x', received = 0; assert(send(worker.first, &value, 1, 0) == 1);
    assert(io(tcp_fd, &received, 1, false) && received == value);
    assert(!shutdown(lease.first, SHUT_WR)); assert(lease_closed());
    auto before = std::chrono::steady_clock::now();
    assert(!io(tcp_fd, &received, 1, false) && errno == ECANCELED);
    assert(std::chrono::steady_clock::now() - before < std::chrono::seconds(1));
    assert(with_netid("{\"ok\":true}", 5000) == "{\"netid\":5000,\"ok\":true}");
    close(lease.first); close(lease.second); close(worker.first); close(worker.second);
    lease_fd = tcp_fd = -1;
    std::cout << "Native controller lease: live read, half-close cancellation during core wait, bounded return and test netid PASS\n";
}
