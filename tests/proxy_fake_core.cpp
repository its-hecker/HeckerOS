// SPDX-License-Identifier: Apache-2.0
// Implements the pinned FlClash C ABI, including asynchronous result/release ownership.
#include "../third_party/picojson/picojson.h"
#include <atomic>
#include <cassert>
#include <chrono>
#include <thread>
extern "C" {
void (*release_object_func)(void*) = nullptr;
void (*free_string_func)(char*) = nullptr;
void (*protect_func)(void*, int) = nullptr;
char* (*resolve_process_func)(void*, int, const char*, const char*, int) = nullptr;
void (*result_func)(void*, const char*) = nullptr;
std::atomic<int> completed{0};
std::atomic<int> mode{0};
int fixture_completed() { return completed.load(); }
void fixture_mode(int value) { mode = value; }
void invokeAction(void* callback, char* input) {
    assert(release_object_func && free_string_func && protect_func && resolve_process_func && result_func);
    picojson::value request; assert(picojson::parse(request, std::string(input)).empty());
    free_string_func(input);
    std::string method = request.get("method").get<std::string>();
    if (method == "initClash" || method == "setupConfig") {
        // Mirror the pinned Go InitParams/SetupParams contract, rather than
        // merely returning success for every initialization request.
        assert(request.get("data").is<std::string>());
        picojson::value params; assert(picojson::parse(params, request.get("data").get<std::string>()).empty());
        if (method == "initClash") {
            assert(params.get("home-dir").get<std::string>() == "/data/misc/heckervpn/tcp");
            assert(params.get("version").get<double>() == 37);
        } else {
            assert(params.get("selected-map").is<picojson::object>());
            assert(params.get("test-url").get<std::string>() == "https://www.gstatic.com/generate_204");
        }
    }
    int selected = mode.load();
    std::thread([callback, method, selected] {
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
        picojson::value data(true); double code = 0;
        if (method == "initClash" && selected == 1) data = picojson::value(false);
        if (method == "setupConfig") {
            data = picojson::value(selected == 2 ? "yaml: invalid config secret-proxy-password" : "");
            if (selected == 3) code = -1;
            if (selected == 4) data = picojson::value(true);
        }
        if (method == "getTotalTraffic") data = picojson::value("{\"up\":13,\"down\":11}");
        if (method == "asyncTestDelay") data = picojson::value("{\"value\":42}");
        if (method == "unicode") data = picojson::value("雪 🔐 \\\"");
        if (method == "failure") code = -1;
        std::string reply = picojson::value(picojson::object{{"id", picojson::value("hecker")}, {"method", picojson::value(method)},
            {"code", picojson::value(code)}, {"data", data}}).serialize();
        if (method == "malformed") reply = "invalid JSON";
        result_func(callback, reply.c_str());
        release_object_func(callback); ++completed;
    }).detach();
}
bool startTUN(void* callback, int, char* stack, char* addresses, char* dns) {
    free_string_func(stack); free_string_func(addresses); free_string_func(dns);
    release_object_func(callback); return true; // Mimic upstream failure that still returns true.
}
void stopTun() {}
}
