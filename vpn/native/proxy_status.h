// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

namespace hecker::vpn {
inline constexpr char kTcpStartup[] = "/data/misc/heckervpn/tcp/startup-status";
enum class TcpPhase { input, config_write, library, initialize, configuration, tun_check, tun_open, tun_attach, tun_start, ready, count };
enum class TcpFailure { none, system, symbols, timeout, protocol, rejected, yaml, dns, proxy, rules, path, stopped, count };
struct TcpStartupSnapshot { TcpPhase phase = TcpPhase::input; TcpFailure failure = TcpFailure::none; int error = 0; };
inline const char* tcp_phase_name(TcpPhase phase) {
    static const char* const names[] = {"input", "config_write", "library", "initialize", "configuration", "tun_check", "tun_open", "tun_attach", "tun_start", "ready"};
    return static_cast<unsigned>(phase) < static_cast<unsigned>(TcpPhase::count) ? names[static_cast<unsigned>(phase)] : "unknown";
}
inline const char* tcp_failure_name(TcpFailure failure) {
    static const char* const names[] = {"none", "system", "symbols", "timeout", "protocol", "rejected", "yaml", "dns", "proxy", "rules", "path", "stopped"};
    return static_cast<unsigned>(failure) < static_cast<unsigned>(TcpFailure::count) ? names[static_cast<unsigned>(failure)] : "unknown";
}
// Keep native parser output private: it may quote proxy credentials. Only the
// category is retained or returned to the app, never the original text.
inline TcpFailure tcp_config_failure(std::string text) {
    for (char& c : text) if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
    if (text.find("yaml") != std::string::npos || text.find("unmarshal") != std::string::npos) return TcpFailure::yaml;
    if (text.find("nameserver") != std::string::npos || text.find("dns") != std::string::npos) return TcpFailure::dns;
    if (text.find("proxy") != std::string::npos || text.find("proxies") != std::string::npos) return TcpFailure::proxy;
    if (text.find("rule") != std::string::npos) return TcpFailure::rules;
    if (text.find("permission denied") != std::string::npos || text.find("no such file") != std::string::npos || text.find("path") != std::string::npos) return TcpFailure::path;
    return TcpFailure::rejected;
}
class TcpStartup {
    const char* path;
    TcpStartupSnapshot value;
    void save() const {
        if (!path) return;
        // Keep the last complete marker readable even if the supervisor kills
        // this process during a subsequent update. No linked inode is truncated.
        struct stat old{};
        if (!lstat(path, &old)) {
            if (!S_ISREG(old.st_mode) || old.st_uid != getuid() || old.st_nlink != 1) return;
        } else if (errno != ENOENT) return;
        std::string temporary = std::string(path) + ".tmp";
        int fd = open(temporary.c_str(), O_WRONLY | O_CREAT | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK, 0600);
        if (fd < 0) return;
        struct stat st{};
        if (fstat(fd, &st) || !S_ISREG(st.st_mode) || st.st_uid != getuid() || st.st_nlink != 1 || fchmod(fd, 0600) || ftruncate(fd, 0)) { close(fd); return; }
        char data[64]; int size = snprintf(data, sizeof(data), "%u %u %d\n", static_cast<unsigned>(value.phase), static_cast<unsigned>(value.failure), value.error);
        size_t offset = 0;
        while (offset < static_cast<size_t>(size)) {
            ssize_t n = write(fd, data + offset, static_cast<size_t>(size) - offset);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) break;
            offset += static_cast<size_t>(n);
        }
        close(fd);
        if (offset == static_cast<size_t>(size)) rename(temporary.c_str(), path);
    }
public:
    explicit TcpStartup(const char* file = nullptr) : path(file) {}
    void enter(TcpPhase phase) { value = {phase, TcpFailure::none, 0}; save(); }
    bool fail(TcpFailure failure, int error = 0) { value.failure = failure; value.error = error >= 0 && error <= 4095 ? error : 0; save(); return false; }
    TcpStartupSnapshot snapshot() const { return value; }
    static bool read(const char* path, TcpStartupSnapshot& result) {
        int fd = open(path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK); if (fd < 0) return false;
        struct stat st{}; char data[65]{};
        bool valid = !fstat(fd, &st) && S_ISREG(st.st_mode) && st.st_uid == getuid() && st.st_nlink == 1 && st.st_size >= 6 && st.st_size < 64;
        ssize_t length = valid ? ::read(fd, data, sizeof(data) - 1) : -1; close(fd);
        unsigned phase = 0, failure = 0; int error = 0; char extra;
        if (length != st.st_size || !valid || sscanf(data, "%u %u %d %c", &phase, &failure, &error, &extra) != 3
                || phase >= static_cast<unsigned>(TcpPhase::count) || failure >= static_cast<unsigned>(TcpFailure::count) || error < 0 || error > 4095) return false;
        char canonical[64]; int canonical_size = snprintf(canonical, sizeof(canonical), "%u %u %d\n", phase, failure, error);
        if (length != canonical_size || memcmp(data, canonical, static_cast<size_t>(canonical_size))) return false;
        result = {static_cast<TcpPhase>(phase), static_cast<TcpFailure>(failure), error}; return true;
    }
};
inline std::string tcp_startup_message(const TcpStartupSnapshot& status) {
    std::string message = status.failure == TcpFailure::timeout ? "TCP core startup timed out" : "TCP core startup failed";
    message += " (stage: "; message += tcp_phase_name(status.phase); message += ", reason: "; message += tcp_failure_name(status.failure);
    if (status.error) message += ", errno: " + std::to_string(status.error);
    return message + "). Check the matching ROM installation and HeckerVPN logs.";
}
}
