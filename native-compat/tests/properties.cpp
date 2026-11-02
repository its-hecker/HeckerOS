// SPDX-License-Identifier: Apache-2.0
// The prepared property API bodies are inserted below; only their storage boundary is a facade.
#include <assert.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <string>
#include <vector>
#include <sys/types.h>
#include "custom_rom_hide.h"
static uid_t uid = 10101;
static int io;
extern "C" uid_t probe_uid() { return uid; }
extern "C" long probe_syscall(long, ...) { ++io; errno = ENOSYS; return -1; }
struct prop_info { std::string name, value; uint32_t serial = 17; };
struct Properties {
    std::vector<prop_info> values;
    const prop_info* Find(const char* name) {
        if (!name) return nullptr;
        for (auto& item : values) if (item.name == name) return &item;
        return nullptr;
    }
    int Read(const prop_info* item, char* name, char* value) {
        if (name) strcpy(name, item->name.c_str());
        if (value) strcpy(value, item->value.c_str());
        return static_cast<int>(item->value.size());
    }
    int Get(const char* name, char* value) {
        auto* item = Find(name);
        if (!item) { value[0] = 0; return 0; }
        return Read(item, nullptr, value);
    }
    void ReadCallback(const prop_info* item, void (*callback)(void*, const char*, const char*, uint32_t), void* cookie) {
        callback(cookie, item->name.c_str(), item->value.c_str(), item->serial);
    }
    const prop_info* FindNth(unsigned n) { return n < values.size() ? &values[n] : nullptr; }
    int Foreach(void (*callback)(const prop_info*, void*), void* cookie) {
        for (const auto& item : values) callback(&item, cookie);
        return 0;
    }
} system_properties;
#define __BIONIC_WEAK_FOR_NATIVE_BRIDGE
// PREPARED_PROPERTY_API
struct ReadResult { std::string name, value; uint32_t serial; };
static void capture(void* cookie, const char* name, const char* value, uint32_t serial) {
    *static_cast<ReadResult*>(cookie) = {name, value, serial};
}
int main() {
    std::string longName = "ro.normal." + std::string(180, 'n'), longValue(5000, 'v');
    system_properties.values = {{"ro.heckeros.version", "1.0"}, {"ro.hecker.app_compatibility", "2"},
        {"ro.build.type", "userdebug"}, {"ro.build.tags", "test-keys"},
        {"ro.boot.flash.locked", "0"}, {longName, longValue}};
    const prop_info* cached = system_properties.Find("ro.heckeros.version");
    char name[256], value[256];
    for (int step = 0; step < 1000; ++step) {
        assert(__system_property_get("ro.build.type", value) == 9);
        assert(!strcmp(value, "userdebug"));
    }
    assert(io == 0);
    assert(__libc_hecker_set_rom_hide(true));
    assert(!__system_property_find("ro.heckeros.version"));
    assert(__system_property_get("ro.heckeros.version", value) == 0 && !value[0]);
    assert(__system_property_get("ro.build.type", value) == 4 && !strcmp(value, "user"));
    assert(__system_property_get("ro.build.tags", value) == 12 && !strcmp(value, "release-keys"));
    assert(__system_property_get("ro.boot.flash.locked", value) == 1 && !strcmp(value, "0"));
    assert(__system_property_read(cached, name, value) == 0 && !name[0] && !value[0]);
    ReadResult result;
    __system_property_read_callback(cached, capture, &result);
    assert(result.name.empty() && result.value.empty() && result.serial == 17);
    auto* normal = __system_property_find(longName.c_str()); assert(normal);
    __system_property_read_callback(normal, capture, &result);
    assert(result.name == longName && result.value == longValue && result.serial == 17);
    std::vector<const prop_info*> visible;
    __system_property_foreach([](const prop_info* item, void* cookie) {
        static_cast<std::vector<const prop_info*>*>(cookie)->push_back(item);
    }, &visible);
    assert(visible.size() == 4);
    for (unsigned i = 0; i < visible.size(); ++i) assert(__system_property_find_nth(i) == visible[i]);
    assert(!__system_property_find_nth(visible.size()));
    assert(io == 0);
    assert(__libc_hecker_set_rom_hide(false));
    assert(__system_property_find("ro.heckeros.version") == cached);
    visible.clear(); __system_property_foreach([](const prop_info* item, void* cookie) {
        static_cast<std::vector<const prop_info*>*>(cookie)->push_back(item);
    }, &visible);
    assert(visible.size() == 6);
    for (auto core : {0u, 1000u, 2000u, 1001000u}) {
        uid = core; assert(!__libc_hecker_set_rom_hide(true));
        assert(__system_property_find("ro.heckeros.version") == cached);
    }
    printf("Prepared sysprops: Get/Find/read/callback/foreach/nth agree, long names and 5 KB values preserved, opt-out/core UIDs unchanged, no policy-file I/O PASS\n");
}
