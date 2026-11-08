// SPDX-License-Identifier: GPL-3.0-only
#define main hecker_proxy_worker_main
#include "../vpn/native/proxy_core.cpp"
#undef main
#include <cassert>
#include <iostream>
#include <sys/wait.h>
#include <thread>
int main(int argc, char** argv) {
    assert(argc == 2);
    hecker::vpn::TcpStartup status;
    Core core(status); assert(core.load(argv[1]));
    Value reply;
    assert(core.action("unicode", Value("UTF-8 🔐"), reply));
    assert(reply.get<std::string>() == "雪 🔐 \\\"");
    assert(!core.action("failure", Value(true), reply));
    assert(!core.action("malformed", Value(true), reply));
    assert(core.test());
    tun_alive = true;
    Value stats; assert(picojson::parse(stats, core.statistics(true)).empty());
    assert(stats.get("rx").get<double>() == 11 && stats.get("tx").get<double>() == 13);
    assert(stats.get("validated").get<bool>());
    void* handle = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    auto completed = reinterpret_cast<int (*)()>(dlsym(handle, "fixture_completed"));
    for (int i = 0; i < 100 && completed() != 5; i++) std::this_thread::sleep_for(std::chrono::milliseconds(1));
    assert(completed() == 5);
    auto mode = reinterpret_cast<void (*)(int)>(dlsym(handle, "fixture_mode"));
    using Phase = hecker::vpn::TcpPhase; using Failure = hecker::vpn::TcpFailure;
    assert(core.prepare());
    assert(status.snapshot().phase == Phase::configuration && status.snapshot().failure == Failure::none);
    mode(1); assert(!core.prepare());
    assert(status.snapshot().phase == Phase::initialize && status.snapshot().failure == Failure::rejected);
    mode(2); assert(!core.prepare());
    assert(status.snapshot().phase == Phase::configuration && status.snapshot().failure == Failure::yaml);
    assert(hecker::vpn::tcp_startup_message(status.snapshot()).find("secret-proxy-password") == std::string::npos);
    mode(3); assert(!core.prepare()); assert(status.snapshot().failure == Failure::rejected);
    mode(4); assert(!core.prepare()); assert(status.snapshot().failure == Failure::protocol);
    mode(0); assert(core.prepare());
    auto start = reinterpret_cast<bool (*)(void*, int, char*, char*, char*)>(dlsym(handle, "startTUN"));
    assert(start(new Callback{}, 42, strdup("gvisor"), strdup("172.19.0.1/30"), strdup("172.19.0.2")));
    assert(!tun_alive); // Successful ABI return must not be mistaken for a live TUN.
    pid_t child = fork(); assert(child >= 0);
    if (!child) { protect(nullptr, -1); _exit(1); }
    int child_status; assert(waitpid(child, &child_status, 0) == child && WIFEXITED(child_status) && WEXITSTATUS(child_status) == 78);
    std::cout << "Production core bridge: async ownership, real startup reply shapes, failure stages, UTF-8, statistics, TUN release and failed socket-mark termination PASS\n";
}
