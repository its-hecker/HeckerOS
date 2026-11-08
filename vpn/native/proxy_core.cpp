// SPDX-License-Identifier: GPL-3.0-only
// Private init-owned TUN worker. No Android VpnService, public listener, shell or remote config.
#include "proxy_config.h"
#include "proxy_status.h"
#include "../../third_party/picojson/picojson.h"
#include <arpa/inet.h>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <fcntl.h>
#include <linux/if_tun.h>
#include <memory>
#include <mutex>
#include <net/if.h>
#include <poll.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/random.h>
#include <sys/socket.h>
#include <unistd.h>

namespace {
using Value = picojson::value;
using Object = picojson::object;
constexpr char kHome[] = "/data/misc/heckervpn/tcp";
constexpr char kLibrary[] = "/system_ext/lib64/libclash.so";
uint32_t physical_mark;
std::atomic<bool> tun_alive{false};
struct Pending { std::mutex mutex; std::condition_variable changed; bool done = false; std::string json; };
struct Callback { std::shared_ptr<Pending> action; };
void released(void* p) { if (p) { auto* c = static_cast<Callback*>(p); if (!c->action) tun_alive = false; delete c; } }
void free_string(char* p) { free(p); }
void result(void* p, const char* data) {
    auto* c = static_cast<Callback*>(p); if (!c || !c->action) return;
    std::lock_guard<std::mutex> lock(c->action->mutex);
    if (data && strnlen(data, 8193) <= 8192) c->action->json = data;
    c->action->done = true; c->action->changed.notify_all();
}
void protect(void*, int fd) {
    // Never allow an unmarked socket to escape through another SIM or recurse into TUN.
    // Terminating on failure prevents an unmarked proxy socket; the supervisor
    // retires the owned netd network when the worker's private socket closes.
    if (setsockopt(fd, SOL_SOCKET, SO_MARK, &physical_mark, sizeof(physical_mark))) _exit(78);
}
char* resolve(void*, int, const char*, const char*, int) { return strdup(""); }
bool frame(int fd, std::string& data, bool writing) {
    uint32_t size = htonl(static_cast<uint32_t>(data.size()));
    auto io = [fd](void* address, size_t length, bool send_data) {
        char* p = static_cast<char*>(address);
        while (length) {
            pollfd ready{fd, static_cast<short>(send_data ? POLLOUT : POLLIN), 0};
            int polled = poll(&ready, 1, send_data ? 25000 : 120000);
            if (polled < 0 && errno == EINTR) continue;
            if (polled <= 0) return false;
            ssize_t n = send_data ? send(fd, p, length, MSG_NOSIGNAL) : recv(fd, p, length, 0);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) return false;
            p += n; length -= static_cast<size_t>(n);
        }
        return true;
    };
    if (!io(&size, sizeof(size), writing)) return false;
    if (!writing) { size = ntohl(size); if (!size || size > 16384) return false; data.resize(size); }
    return io(data.data(), data.size(), writing);
}
class Core {
    hecker::vpn::TcpStartup& status;
    void (*invoke)(void*, char*) = nullptr;
    bool (*start)(void*, int, char*, char*, char*) = nullptr;
    void (*stop)() = nullptr;
public:
    explicit Core(hecker::vpn::TcpStartup& progress) : status(progress) {}
    bool load(const char* path = kLibrary) {
        status.enter(hecker::vpn::TcpPhase::library);
        // Production main always uses the fixed path. The argument enables host ABI tests.
        void* library = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        if (!library) return status.fail(hecker::vpn::TcpFailure::system, errno);
        invoke = reinterpret_cast<decltype(invoke)>(dlsym(library, "invokeAction"));
        start = reinterpret_cast<decltype(start)>(dlsym(library, "startTUN"));
        stop = reinterpret_cast<decltype(stop)>(dlsym(library, "stopTun"));
        auto release_slot = reinterpret_cast<void (**)(void*)>(dlsym(library, "release_object_func"));
        auto free_slot = reinterpret_cast<void (**)(char*)>(dlsym(library, "free_string_func"));
        auto protect_slot = reinterpret_cast<void (**)(void*, int)>(dlsym(library, "protect_func"));
        auto resolve_slot = reinterpret_cast<char* (**)(void*, int, const char*, const char*, int)>(dlsym(library, "resolve_process_func"));
        auto result_slot = reinterpret_cast<void (**)(void*, const char*)>(dlsym(library, "result_func"));
        if (!invoke || !start || !stop || !release_slot || !free_slot || !protect_slot || !resolve_slot || !result_slot) return status.fail(hecker::vpn::TcpFailure::symbols);
        *release_slot = released; *free_slot = free_string; *protect_slot = protect; *resolve_slot = resolve; *result_slot = result;
        return true;
    }
    bool action(const char* method, const Value& data, Value& reply) {
        auto wait = std::make_shared<Pending>();
        std::string json = Value(Object{{"id", Value("hecker")}, {"method", Value(method)}, {"data", data}}).serialize();
        invoke(new Callback{wait}, strdup(json.c_str())); // Go owns strings and releases callbacks.
        std::unique_lock<std::mutex> lock(wait->mutex);
        if (!wait->changed.wait_for(lock, std::chrono::seconds(12), [&] { return wait->done; })) return status.fail(hecker::vpn::TcpFailure::timeout);
        Value response;
        if (!picojson::parse(response, wait->json).empty() || !response.is<Object>()) return status.fail(hecker::vpn::TcpFailure::protocol);
        const Value& code = response.get("code");
        if (!code.is<double>()) return status.fail(hecker::vpn::TcpFailure::protocol);
        if (code.get<double>() != 0) return status.fail(hecker::vpn::TcpFailure::rejected);
        reply = response.get("data"); return true;
    }
    bool prepare() {
        Value reply;
        status.enter(hecker::vpn::TcpPhase::initialize);
        std::string init = Value(Object{{"home-dir", Value(kHome)}, {"version", Value(37.0)}}).serialize();
        if (!action("initClash", Value(init), reply)) return false;
        if (!reply.is<bool>() || !reply.get<bool>()) return status.fail(hecker::vpn::TcpFailure::rejected);
        status.enter(hecker::vpn::TcpPhase::configuration);
        if (!action("setupConfig", Value("{\"selected-map\":{},\"test-url\":\"https://www.gstatic.com/generate_204\"}"), reply)) return false;
        if (!reply.is<std::string>()) return status.fail(hecker::vpn::TcpFailure::protocol);
        if (!reply.get<std::string>().empty()) return status.fail(hecker::vpn::tcp_config_failure(reply.get<std::string>()));
        return true;
    }
    bool setup() {
        if (!prepare()) return false;
        status.enter(hecker::vpn::TcpPhase::tun_check);
        if (if_nametoindex("hkvpn0")) return status.fail(hecker::vpn::TcpFailure::system, EEXIST);
        status.enter(hecker::vpn::TcpPhase::tun_open);
        int fd = open("/dev/tun", O_RDWR | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) return status.fail(hecker::vpn::TcpFailure::system, errno);
        status.enter(hecker::vpn::TcpPhase::tun_attach);
        ifreq request{}; strcpy(request.ifr_name, "hkvpn0"); request.ifr_flags = IFF_TUN | IFF_NO_PI;
        if (ioctl(fd, TUNSETIFF, &request)) { int error = errno; close(fd); return status.fail(hecker::vpn::TcpFailure::system, error); }
        status.enter(hecker::vpn::TcpPhase::tun_start);
        tun_alive = true;
        start(new Callback{}, fd, strdup("gvisor"), strdup("172.19.0.1/30,fdfe:dcba:9876::1/126"), strdup("172.19.0.2"));
        // The pinned startTUN returns true even on listener failure. Use its release callback.
        if (!tun_alive.load() || !if_nametoindex("hkvpn0")) return status.fail(hecker::vpn::TcpFailure::rejected);
        status.enter(hecker::vpn::TcpPhase::ready); return true;
    }
    bool test() {
        Value reply;
        if (!action("asyncTestDelay", Value("{\"proxy-name\":\"Hecker TCP\",\"test-url\":\"https://www.gstatic.com/generate_204\",\"timeout\":6000}"), reply)
            || !reply.is<std::string>()) return false;
        Value delay;
        return picojson::parse(delay, reply.get<std::string>()).empty() && delay.is<Object>() &&
            delay.get("value").is<double>() && delay.get("value").get<double>() > 0;
    }
    std::string statistics(bool validated) {
        Value data, traffic;
        if (!tun_alive || !action("getTotalTraffic", Value(true), data) || !data.is<std::string>() ||
            !picojson::parse(traffic, data.get<std::string>()).empty() || !traffic.is<Object>()) return "{\"ok\":false}";
        // Do not use upstream's getTotalTraffic C export: that version returns a freed pointer.
        return Value(Object{{"ok", Value(true)}, {"configured", Value(true)}, {"validated", Value(validated)},
            {"rx", traffic.get("down")}, {"tx", traffic.get("up")}, {"transport", Value("tcp")}}).serialize();
    }
    void shutdown() { if (stop) stop(); }
};
bool dns_probe(unsigned int netid) {
    // Test the complete netd -> TUN -> DNS-over-HTTPS -> selected proxy path before Active.
    int fd = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0); if (fd < 0) return false;
    uint32_t mark = 0xf0000U | netid; timeval timeout{7, 0};
    bool ok = !setsockopt(fd, SOL_SOCKET, SO_MARK, &mark, sizeof(mark)) &&
        !setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    sockaddr_in target{}; target.sin_family = AF_INET; target.sin_port = htons(53); inet_pton(AF_INET, "172.19.0.2", &target.sin_addr);
    uint16_t id = 0; if (getrandom(&id, sizeof(id), 0) != sizeof(id)) ok = false;
    unsigned char query[] = {0,0,1,0,0,1,0,0,0,0,0,0,7,'e','x','a','m','p','l','e',3,'c','o','m',0,0,1,0,1};
    memcpy(query, &id, 2);
    if (ok) ok = !connect(fd, reinterpret_cast<sockaddr*>(&target), sizeof(target)) && send(fd, query, sizeof(query), 0) == sizeof(query);
    unsigned char response[2048]; ssize_t count = ok ? recv(fd, response, sizeof(response), 0) : -1;
    close(fd);
    return count >= 12 && !memcmp(query, response, 2) && (response[2] & 0x80) &&
        !(response[3] & 0x0f) && (response[6] || response[7]);
}
}
int main() {
    // Only heckervpnd supplies the private inherited socket. No command-line API.
    if (getuid() || getppid() == 1 || prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() == 1) return 1;
    signal(SIGPIPE, SIG_IGN);
    hecker::vpn::TcpStartup status(hecker::vpn::kTcpStartup);
    status.enter(hecker::vpn::TcpPhase::input);
    std::string input; hecker::vpn::ProxyConfig config;
    if (!frame(0, input, false) || !hecker::vpn::parse_proxy(input, config)) { status.fail(hecker::vpn::TcpFailure::protocol); return 2; }
    physical_mark = 0xf0000U | config.netid;
    status.enter(hecker::vpn::TcpPhase::config_write);
    int file = open("/data/misc/heckervpn/tcp/config.yaml", O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW | O_CLOEXEC, 0600);
    std::string json = hecker::vpn::proxy_core_config(config);
    if (file < 0) { status.fail(hecker::vpn::TcpFailure::system, errno); return 3; }
    size_t offset = 0;
    while (offset < json.size()) { ssize_t n = write(file, json.data() + offset, json.size() - offset); if (n <= 0) { int error = errno; close(file); status.fail(hecker::vpn::TcpFailure::system, error); return 3; } offset += n; }
    bool saved = fsync(file) == 0; int error = errno; close(file); if (!saved) { status.fail(hecker::vpn::TcpFailure::system, error); return 3; }
    Core core(status);
    if (!core.load() || !core.setup()) return 4;
    std::string reply = "{\"ok\":true}";
    if (!frame(0, reply, true)) return 5;
    bool validated = false;
    while (true) {
        // Parent polls every few seconds; an abandoned private lease expires.
        if (!frame(0, input, false)) break;
        if (!tun_alive || !if_nametoindex("hkvpn0")) break;
        if (input.rfind("PROBE\n", 0) == 0) {
            std::string value = input.substr(6);
            if (value.empty() || value.size() > 5 || value.find_first_not_of("0123456789") != std::string::npos) break;
            unsigned long netid = strtoul(value.c_str(), nullptr, 10);
            validated = netid >= 4096 && netid <= 65534 && core.test() && dns_probe(netid);
        } else if (input != "STATUS") break;
        reply = core.statistics(validated); if (!frame(0, reply, true)) break;
    }
    core.shutdown(); unlink("/data/misc/heckervpn/tcp/config.yaml"); return 0;
}
