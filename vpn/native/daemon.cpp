// SPDX-License-Identifier: Apache-2.0
// Narrow init-owned controller. No su, caller-selected executable, path, interface or shell command.
#include "protocol.h"
#include "setup_status.h"
#include "proxy_config.h"
#include <arpa/inet.h>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <sstream>
#include <string>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>
#ifdef __ANDROID__
#include <cutils/sockets.h>
#endif

namespace {
constexpr char kConfig[] = "/data/misc/heckervpn/hkvpn0.conf";
constexpr char kStage[] = "/data/misc/heckervpn/setup-stage";
constexpr char kNetid[] = "/data/misc/heckervpn/netid";
constexpr char kWg[] = "/system_ext/bin/hecker-wg";
constexpr char kQuick[] = "/system_ext/bin/hecker-wg-quick";
constexpr char kTcp[] = "/system_ext/bin/hecker-tcp-core";
pid_t tcp_pid = -1;
int tcp_fd = -1;
unsigned int tcp_netid = 0;
volatile sig_atomic_t stopping = 0;
bool cleanup_pending = false;
void stopped(int) { stopping = 1; }
struct Result { int code = -1; std::string output; };
bool io(int fd, void* buffer, size_t size, bool writing);

bool tcp_exchange(std::string& data, bool send_request = true) {
    if (tcp_fd < 0) return false;
    uint32_t size = htonl(static_cast<uint32_t>(data.size()));
    if (send_request && (!io(tcp_fd, &size, sizeof(size), true) || !io(tcp_fd, data.data(), data.size(), true))) return false;
    if (!io(tcp_fd, &size, sizeof(size), false)) return false;
    size = ntohl(size); if (size < 2 || size > 8192) return false;
    data.resize(size); return io(tcp_fd, data.data(), size, false);
}
void stop_tcp() {
    if (tcp_fd >= 0) { close(tcp_fd); tcp_fd = -1; }
    if (tcp_pid > 0) {
        kill(-tcp_pid, SIGKILL); kill(tcp_pid, SIGKILL);
        while (waitpid(tcp_pid, nullptr, 0) < 0 && errno == EINTR) {}
        tcp_pid = -1;
    }
    tcp_netid = 0;
    unlink("/data/misc/heckervpn/tcp/config.yaml");
}
bool start_tcp(const hecker::vpn::ProxyConfig& config) {
    int pair[2]; if (socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, pair)) return false;
    tcp_pid = fork();
    if (!tcp_pid) {
        setpgid(0, 0); dup2(pair[1], STDIN_FILENO);
        int null = open("/dev/null", O_RDWR); dup2(null, STDOUT_FILENO); dup2(null, STDERR_FILENO);
        close(pair[0]); if (pair[1] != STDIN_FILENO) close(pair[1]); if (null > 2) close(null);
        char path[] = "PATH=/system_ext/bin:/system/bin", lang[] = "LANG=C";
        char root[] = "ANDROID_ROOT=/system", data[] = "ANDROID_DATA=/data";
        char certs[] = "SSL_CERT_DIR=/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts";
        char memory[] = "GOMEMLIMIT=192MiB";
        char* env[] = {path, lang, root, data, certs, memory, nullptr};
        char* args[] = {const_cast<char*>(kTcp), nullptr}; execve(kTcp, args, env); _exit(127);
    }
    close(pair[1]); if (tcp_pid < 0) { close(pair[0]); return false; }
    setpgid(tcp_pid, tcp_pid); tcp_fd = pair[0];
    timeval timeout{25, 0}; setsockopt(tcp_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(tcp_fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    std::string message = hecker::vpn::proxy_json(config);
    return tcp_exchange(message) && message == "{\"ok\":true}";
}

Result run(const char* command, std::vector<std::string> arguments, const std::string& input = "", bool capture = false, bool cleanup_call = false) {
    Result result; int in[2], out[2];
    if (pipe2(in, O_CLOEXEC)) return result;
    if (pipe2(out, O_CLOEXEC)) { close(in[0]); close(in[1]); return result; }
    pid_t child = fork();
    if (child == 0) {
        setpgid(0, 0);
        int null = open("/dev/null", O_RDWR);
        dup2(in[0], STDIN_FILENO); dup2(capture ? out[1] : null, STDOUT_FILENO); dup2(null, STDERR_FILENO);
        close(in[0]); close(in[1]); close(out[0]); close(out[1]); if (null > 2) close(null);
        std::vector<char*> argv; argv.push_back(const_cast<char*>(command));
        for (auto& argument : arguments) argv.push_back(argument.data());
        argv.push_back(nullptr);
        char path[] = "PATH=/system_ext/bin:/system/bin";
        char root[] = "ANDROID_ROOT=/system", data[] = "ANDROID_DATA=/data", lang[] = "LANG=C";
        char* environment[] = {path, root, data, lang, nullptr};
        execve(command, argv.data(), environment); _exit(127);
    }
    close(in[0]); close(out[1]);
    if (child < 0) { close(in[1]); close(out[0]); return result; }
    setpgid(child, child);
    if (!input.empty()) { ssize_t ignored = write(in[1], input.data(), input.size()); (void)ignored; }
    close(in[1]); fcntl(out[0], F_SETFL, O_NONBLOCK);
    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(20);
    int status = 0;
    while (true) {
        char buffer[2048]; ssize_t size;
        while ((size = read(out[0], buffer, sizeof(buffer))) > 0) {
            result.output.append(buffer, static_cast<size_t>(size));
            if (result.output.size() > 16384) break;
        }
        pid_t exited = waitpid(child, &status, WNOHANG);
        if (exited == child) { result.code = WIFEXITED(status) ? WEXITSTATUS(status) : -1; break; }
        if (exited < 0 || (stopping && !cleanup_call) || result.output.size() > 16384 || std::chrono::steady_clock::now() > deadline) {
            kill(-child, SIGKILL); while (waitpid(child, &status, 0) < 0 && errno == EINTR) {} break;
        }
        if (!capture) poll(nullptr, 0, 30);
        else {
            pollfd ready{out[0], POLLIN, 0}; poll(&ready, 1, 30);
            if (ready.revents & POLLHUP) poll(nullptr, 0, 30);
        }
    }
    char buffer[2048]; ssize_t size;
    while ((size = read(out[0], buffer, sizeof(buffer))) > 0 && result.output.size() <= 16384)
        result.output.append(buffer, static_cast<size_t>(size));
    close(out[0]); return result;
}
bool cleanup() {
    bool cleaned = true;
    if (access(kConfig, F_OK) == 0) cleaned = run(kQuick, {"down", kConfig}, "", false, true).code == 0;
    // The recorded netid survives an interrupted helper. It belongs solely to this tunnel.
    if (access(kNetid, F_OK) == 0) cleaned = run(kQuick, {"cleanup"}, "", false, true).code == 0 && cleaned;
    stop_tcp();
    cleanup_pending = !cleaned;
    if (cleaned) { unlink(kNetid); unlink(kConfig); unlink(kStage); }
    return cleaned;
}
bool save_config(const std::string& config) {
    int file = open(kConfig, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (file < 0) return false;
    size_t offset = 0;
    while (offset < config.size()) {
        ssize_t size = write(file, config.data() + offset, config.size() - offset);
        if (size <= 0) { close(file); unlink(kConfig); return false; }
        offset += static_cast<size_t>(size);
    }
    bool ok = fsync(file) == 0; close(file); return ok;
}
std::string tcp_error(const char* message) {
    return std::string("{\"ok\":false,\"cleanup_required\":") + (cleanup_pending ? "true" : "false") + ",\"error\":\"" + message + "\"}";
}
std::string trim(std::string value) {
    while (!value.empty() && (value.back() == '\n' || value.back() == '\r')) value.pop_back();
    return value;
}
bool key(const std::string& text) {
    return text.size() == 44 && text.back() == '=' && text.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=") == std::string::npos;
}
std::string state() {
    if (tcp_pid > 0) {
        std::string message = "STATUS";
        if (!tcp_exchange(message) || message.find("\"ok\":true") == std::string::npos) {
            cleanup(); return tcp_error("The root TCP worker stopped. Reconnect or choose another server.");
        }
        return message;
    }
    Result handshakes = run(kWg, {"show", "hkvpn0", "latest-handshakes"}, "", true);
    bool active = handshakes.code == 0;
    uint64_t handshake = 0, rx = 0, tx = 0;
    if (active) {
        std::istringstream hs(handshakes.output); std::string public_key; uint64_t time;
        while (hs >> public_key >> time) if (time > handshake) handshake = time;
        Result transfer = run(kWg, {"show", "hkvpn0", "transfer"}, "", true);
        if (transfer.code) return "{\"ok\":false,\"error\":\"Unable to read tunnel statistics\"}";
        std::istringstream counters(transfer.output); uint64_t received, sent;
        while (counters >> public_key >> received >> sent) { rx += received; tx += sent; }
    }
    return std::string("{\"ok\":true,\"kernel\":") + (access("/sys/module/wireguard", F_OK) == 0 ? "true" : "false")
        + ",\"configured\":" + (active ? "true" : "false") + ",\"handshake\":" + std::to_string(handshake)
        + ",\"rx\":" + std::to_string(rx) + ",\"tx\":" + std::to_string(tx) + "}";
}
std::string request(const std::string& command) {
    if (command == "STATUS") {
        // Recover stale journal ownership before refusing a new connection. A missing
        // netd network after reboot is a normal idempotent cleanup case.
        if (cleanup_pending && !cleanup()) return "{\"ok\":false,\"cleanup_required\":true,\"error\":\"Previous tunnel cleanup is incomplete. Retry disconnect.\"}";
        return state();
    }
    if (command == "DOWN") {
        if (!cleanup()) return "{\"ok\":false,\"cleanup_required\":true,\"error\":\"Network cleanup is incomplete; ownership journal retained for retry.\"}";
        return state();
    }
    if (command == "KEYS") {
        Result generated = run(kWg, {"genkey"}, "", true); std::string private_key = trim(generated.output);
        if (generated.code || !key(private_key)) return "{\"ok\":false,\"error\":\"Key generation failed\"}";
        Result derived = run(kWg, {"pubkey"}, private_key + "\n", true); std::string public_key = trim(derived.output);
        if (derived.code || !key(public_key)) return "{\"ok\":false,\"error\":\"Public key generation failed\"}";
        return "{\"ok\":true,\"private\":\"" + private_key + "\",\"public\":\"" + public_key + "\"}";
    }
    if (command.rfind("TCP_UP\n", 0) == 0) {
        hecker::vpn::ProxyConfig config;
        if (!hecker::vpn::parse_proxy(command.substr(7), config)) return "{\"ok\":false,\"error\":\"Rejected TCP proxy configuration\"}";
        if (!cleanup()) return "{\"ok\":false,\"cleanup_required\":true,\"error\":\"Previous tunnel cleanup is incomplete\"}";
        if (!save_config(hecker::vpn::proxy_routes(config))) return tcp_error("Unable to stage root TCP routing");
        if (!start_tcp(config)) {
            // No netd setup has run yet. A worker that rejected an existing
            // foreign hkvpn0 must not cause cleanup to delete that interface.
            unlink(kConfig); stop_tcp();
            return tcp_error("Root TCP core could not start. Check the ROM installation and SELinux logs.");
        }
        Result up = run(kQuick, {"up-tcp", kConfig, std::to_string(config.netid)});
        if (!up.code) {
            FILE* journal = fopen(kNetid, "re");
            if (journal) { if (fscanf(journal, "%u", &tcp_netid) != 1) tcp_netid = 0; fclose(journal); }
        }
        if (up.code || tcp_netid < 4096 || tcp_netid > 65534) {
            cleanup(); return std::string("{\"ok\":false,\"cleanup_required\":") + (cleanup_pending ? "true" : "false") + ",\"error\":\"Root TCP routing setup failed. Check SELinux and HeckerVPN service logs.\"}";
        }
        std::string probe = "PROBE\n" + std::to_string(tcp_netid);
        if (!tcp_exchange(probe) || probe.find("\"validated\":true") == std::string::npos) {
            cleanup(); return std::string("{\"ok\":false,\"cleanup_required\":") + (cleanup_pending ? "true" : "false") + ",\"error\":\"The TCP server did not pass the HTTPS and tunnel DNS checks. Try another free server or import a working HTTPS proxy.\"}";
        }
        return probe;
    }
    if (command == "TCP_PROBE" && tcp_pid > 0) {
        std::string probe = "PROBE\n" + std::to_string(tcp_netid);
        if (tcp_exchange(probe)) return probe;
        cleanup(); return tcp_error("The root TCP worker stopped");
    }
    if (command.rfind("UP\n", 0) == 0) {
        std::string config;unsigned int underlay=0;
        if (!hecker::vpn::parse_up(command,underlay,config)) return "{\"ok\":false,\"error\":\"Rejected noncanonical configuration or physical network\"}";
        if (!cleanup()) return "{\"ok\":false,\"cleanup_required\":true,\"error\":\"Previous tunnel cleanup is incomplete\"}";
        if (!save_config(config)) return "{\"ok\":false,\"error\":\"Unable to stage tunnel configuration\"}";
        Result up = run(kQuick, {"up", kConfig,std::to_string(underlay)});
        if (up.code) {
            char stage[32] = {};
            int fd = open(kStage, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
            if (fd >= 0) { ssize_t count = read(fd, stage, sizeof(stage) - 1); (void)count; close(fd); }
            std::string error = hecker::vpn::setup_error(stage);
            cleanup();
            return std::string("{\"ok\":false,\"cleanup_required\":") + (cleanup_pending ? "true" : "false")
                + ",\"exit_code\":" + std::to_string(up.code) + ",\"error\":\"" + error + "\"}";
        }
        std::string result = state();
        if (result.find("\"configured\":true") == std::string::npos) {
            cleanup(); return "{\"ok\":false,\"error\":\"Tunnel setup did not produce a readable WireGuard interface\"}";
        }
        return result;
    }
    return "{\"ok\":false,\"error\":\"Unsupported operation\"}";
}
bool io(int fd, void* buffer, size_t size, bool writing) {
    auto* bytes = static_cast<char*>(buffer);
    while (size && !stopping) {
        ssize_t count = writing ? send(fd, bytes, size, MSG_NOSIGNAL) : recv(fd, bytes, size, 0);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return false;
        bytes += count; size -= static_cast<size_t>(count);
    }
    return size == 0;
}
bool authorized(int fd) {
    ucred credential{}; socklen_t size = sizeof(credential);
    char context[256]{}; socklen_t length = sizeof(context) - 1;
    return getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &credential, &size) == 0
        && getsockopt(fd, SOL_SOCKET, SO_PEERSEC, context, &length) == 0
        && hecker::vpn::authorized_peer(credential.uid, std::string(context, strnlen(context, sizeof(context))));
}
}
int main() {
    signal(SIGPIPE, SIG_IGN); struct sigaction action{}; action.sa_handler = stopped;
    sigaction(SIGTERM, &action, nullptr); sigaction(SIGINT, &action, nullptr);
    int server = -1;
#ifdef __ANDROID__
    server = android_get_control_socket("heckervpnd");
#endif
    if (server < 0 || listen(server, 4)) return 1;
    fcntl(server, F_SETFD, FD_CLOEXEC); umask(0077);
    cleanup();
    while (!stopping) {
        int client = accept4(server, nullptr, nullptr, SOCK_CLOEXEC);
        if (client < 0) { if (errno == EINTR) continue; break; }
        if (!authorized(client)) { close(client); continue; }
        timeval timeout{90, 0}; setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
        while (!stopping) {
            uint32_t length;
            if (!io(client, &length, sizeof(length), false)) break;
            length = ntohl(length); if (!length || length > hecker::vpn::kMaxConfig + 16) break;
            std::string command(length, '\0'); if (!io(client, command.data(), length, false)) break;
            std::string response = request(command); uint32_t size = htonl(static_cast<uint32_t>(response.size()));
            if (!io(client, &size, sizeof(size), true) || !io(client, response.data(), response.size(), true)) break;
        }
        cleanup(); close(client);
    }
    cleanup(); close(server); return 0;
}
