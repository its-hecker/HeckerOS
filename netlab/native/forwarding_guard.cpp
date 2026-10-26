// SPDX-License-Identifier: Apache-2.0
#include "forwarding_guard.h"
#include <algorithm>
#include <cerrno>
#include <csignal>
#include <cstdint>
#include <cstdlib>
#include <dirent.h>
#include <fcntl.h>
#include <poll.h>
#include <sstream>
#include <stdexcept>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

namespace hecker::lan {
namespace {
uint64_t elapsed() { timespec ts{}; clock_gettime(CLOCK_BOOTTIME, &ts); return uint64_t(ts.tv_sec) * 1000 + ts.tv_nsec / 1000000; }
std::vector<std::string> rules(const std::string& text) {
    std::vector<std::string> result; std::istringstream stream(text); std::string line;
    while (std::getline(stream, line)) if (line.rfind("-A ", 0) == 0) result.push_back(line);
    return result;
}
volatile sig_atomic_t guardianStop;
void stopGuardian(int) { guardianStop = 1; }
}

FirewallResult runFirewall(const std::vector<std::string>& arguments) {
    int pipe[2]; if (pipe2(pipe, O_CLOEXEC | O_NONBLOCK) != 0) return {-1, {}};
    pid_t child = fork();
    if (child == 0) {
        dup2(pipe[1], STDOUT_FILENO);
        int null = open("/dev/null", O_WRONLY | O_CLOEXEC); if (null >= 0) dup2(null, STDERR_FILENO);
        std::vector<const char*> argv{"/system/bin/iptables", "-w", "1"};
        for (const auto& part : arguments) argv.push_back(part.c_str());
        argv.push_back(nullptr);
        execv(argv[0], const_cast<char* const*>(argv.data())); _exit(127);
    }
    close(pipe[1]);
    if (child < 0) { close(pipe[0]); return {-1, {}}; }
    FirewallResult result{-1, {}}; int status = 0; uint64_t end = elapsed() + 2500;
    bool done = false;
    while (elapsed() < end) {
        char data[4096]; ssize_t count;
        while ((count = read(pipe[0], data, sizeof(data))) > 0) {
            if (result.output.size() + count > 65536) { end = 0; break; }
            result.output.append(data, count);
        }
        pid_t waited = waitpid(child, &status, WNOHANG);
        if (waited == child) { done = true; break; }
        if (waited < 0 && errno != EINTR) break;
        pollfd fd{pipe[0], POLLIN, 0}; poll(&fd, 1, 20);
    }
    if (!done) { kill(child, SIGKILL); while (waitpid(child, &status, 0) < 0 && errno == EINTR) {} }
    else {
        char data[4096]; ssize_t count;
        while ((count = read(pipe[0], data, sizeof(data))) > 0 && result.output.size() + count <= 65536)
            result.output.append(data, count);
        result.status = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    }
    close(pipe[0]); return result;
}

ForwardingGuard::ForwardingGuard(std::string iface, std::string chain, FirewallRunner runner)
        : iface_(std::move(iface)), chain_(std::move(chain)), runner_(std::move(runner)) {
    // Inputs are also validated by Link. Keep this primitive independently bounded.
    if (iface_.empty() || iface_.size() > 15 || iface_.find_first_not_of("abcdefghijklmnopqrstuvwxyz0123456789") != std::string::npos
            || chain_.rfind("HKNETLAB_", 0) != 0 || chain_.size() > 28
            || chain_.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789") != std::string::npos)
        throw std::invalid_argument("forwarding_guard_unavailable");
}
ForwardingGuard::~ForwardingGuard() { clear(); }
void ForwardingGuard::require(const std::vector<std::string>& arguments) {
    if (runner_(arguments).status != 0) throw std::runtime_error("forwarding_guard_unavailable");
}
void ForwardingGuard::install(const std::vector<std::string>& peers) {
    if (owned_ || peers.empty() || peers.size() > 32) throw std::invalid_argument("forwarding_guard_unavailable");
    for (const auto& peer : peers) {
        if (peer.empty() || peer.size() > 15 || peer.find_first_not_of("0123456789.") != std::string::npos)
            throw std::invalid_argument("forwarding_guard_unavailable");
    }
    require({"-N", chain_}); owned_ = true; peers_ = peers;
    try {
        for (const auto& peer : peers_) for (const auto& direction : {"-s", "-d"})
            require({"-A", chain_, direction, peer + "/32", "-j", "DROP"});
        require({"-I", "FORWARD", "1", "-i", iface_, "-j", chain_}); incoming_ = true;
        require({"-I", "FORWARD", "2", "-o", iface_, "-j", chain_}); outgoing_ = true;
        if (!intact()) throw std::runtime_error("forwarding_guard_unavailable");
    } catch (...) { clear(); throw; }
}
bool ForwardingGuard::intact() {
    if (!owned_) return false;
    auto forwards = runner_({"-S", "FORWARD"}), own = runner_({"-S", chain_});
    if (forwards.status || own.status) return false;
    auto jumps = rules(forwards.output), actual = rules(own.output);
    if (jumps.size() < 2 || jumps[0] != "-A FORWARD -i " + iface_ + " -j " + chain_
            || jumps[1] != "-A FORWARD -o " + iface_ + " -j " + chain_) return false;
    std::vector<std::string> expected;
    for (const auto& peer : peers_) for (const auto& direction : {"-s", "-d"})
        expected.push_back("-A " + chain_ + " " + direction + " " + peer + "/32 -j DROP");
    return expected == actual;
}
void ForwardingGuard::supervise(unsigned seconds) {
    if (!owned_ || guardian_ > 0 || seconds < 1 || seconds > 3600) throw std::runtime_error("forwarding_guard_unavailable");
    int pipe[2]; if (pipe2(pipe, O_CLOEXEC) != 0) throw std::runtime_error("forwarding_guard_unavailable");
    guardian_ = fork();
    if (guardian_ < 0) { close(pipe[0]); close(pipe[1]); throw std::runtime_error("forwarding_guard_unavailable"); }
    if (guardian_ == 0) {
        guardian_ = -1; close(pipe[1]);
        // Do not retain raw sockets or the UI's stdin/stdout in the guardian.
        if (DIR* directory = opendir("/proc/self/fd")) {
            while (dirent* entry = readdir(directory)) {
                char* end = nullptr; long fd = strtol(entry->d_name, &end, 10);
                if (*entry->d_name && end && !*end && fd != pipe[0] && fd != dirfd(directory)) close(static_cast<int>(fd));
            }
            closedir(directory);
        }
        int null = open("/dev/null", O_RDWR);
        if (null >= 0) { dup2(null, 0); dup2(null, 1); dup2(null, 2); if (null > 2 && null != pipe[0]) close(null); }
        guardianStop = 0; signal(SIGTERM, stopGuardian); signal(SIGINT, stopGuardian); signal(SIGHUP, stopGuardian);
        uint64_t end = elapsed() + (seconds + 15) * 1000ULL;
        pollfd lease{pipe[0], POLLIN | POLLHUP, 0};
        while (!guardianStop && elapsed() < end) if (poll(&lease, 1, 200) > 0) break;
        close(pipe[0]);
        for (int attempt = 0; attempt < 3; ++attempt) if (clear()) _exit(0);
        _exit(1);
    }
    close(pipe[0]); lease_ = pipe[1];
}
bool ForwardingGuard::clear() {
    if (lease_ >= 0) { close(lease_); lease_ = -1; }
    if (guardian_ > 0) {
        // The guardian owns removal after it is armed. Wait for bounded commands.
        int status = 0; pid_t waited;
        do { waited = waitpid(guardian_, &status, 0); } while (waited < 0 && errno == EINTR);
        guardian_ = -1; owned_ = incoming_ = outgoing_ = false;
        return waited > 0 && WIFEXITED(status) && WEXITSTATUS(status) == 0;
    }
    if (!owned_) return true;
    if (incoming_) runner_({"-D", "FORWARD", "-i", iface_, "-j", chain_});
    if (outgoing_) runner_({"-D", "FORWARD", "-o", iface_, "-j", chain_});
    // Never flush FORWARD or change its policy, another chain, NAT or a sysctl.
    runner_({"-F", chain_}); runner_({"-X", chain_});
    auto forwards = runner_({"-S", "FORWARD"}), own = runner_({"-S", chain_});
    bool removed = forwards.status == 0 && own.status != 0;
    for (const auto& line : rules(forwards.output)) if (line.find("-j " + chain_) != std::string::npos) removed = false;
    if (removed) owned_ = incoming_ = outgoing_ = false;
    return removed;
}
}
