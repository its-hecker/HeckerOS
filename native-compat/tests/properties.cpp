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
#include <sys/syscall.h>
#include "custom_rom_hide.h"
static uid_t uid = 10101;
static int io;
extern "C" uid_t probe_uid() { return uid; }
extern "C" long probe_syscall(long op, ...) { if(op != SYS_prctl) ++io; errno = ENOSYS; return -1; }
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
extern "C" {
// PREPARED_PROPERTY_API
}
struct ReadResult { std::string name, value; uint32_t serial; };
static void capture(void* cookie, const char* name, const char* value, uint32_t serial) {
    *static_cast<ReadResult*>(cookie) = {name, value, serial};
}
int main() {
    std::string longName = "ro.normal." + std::string(180, 'n'), longValue(5000, 'v');
    system_properties.values = {{"ro.heckeros.version", "1.0"}, {"ro.hecker.app_compatibility", "2"},
        {"ro.build.type", "userdebug"}, {"ro.build.tags", "test-keys"},
        {"ro.boot.flash.locked", "0"}, {longName, longValue},
        {"ro.build.fingerprint", "google/coral/coral:13/TP1A.221005.002.B2/9382335:user/release-keys"},
        {"ro.product.device", "coral"}, {"ro.system.build.fingerprint", "Android/generic_system/generic:17/eng:userdebug/test-keys"},
        {"ro.vendor.build.tags", "test-keys"}, {"ro.build.flavor", "hecker_coral-userdebug"},
        {"init.svc.adbd", "running"}, {"sys.usb.config", "mtp,adb"},
        {"init.svc_debug_pid.adbd", "1234"}};
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
    assert(visible.size() == system_properties.values.size() - 2);
    for (unsigned i = 0; i < visible.size(); ++i) assert(__system_property_find_nth(i) == visible[i]);
    assert(!__system_property_find_nth(visible.size()));
    assert(__system_property_get("ro.system.build.fingerprint", value) > 0);
    assert(!strcmp(value, system_properties.Find("ro.build.fingerprint")->value.c_str()));
    assert(__system_property_get("ro.vendor.build.tags", value) == 12 && !strcmp(value,"release-keys"));
    assert(__system_property_get("ro.build.flavor", value) == 10 && !strcmp(value,"coral-user"));
    assert(__system_property_get("init.svc.adbd",value)==7 && !strcmp(value,"running"));
    assert(io == 0);
    assert(__libc_hecker_set_rom_hide(false));
    assert(__system_property_find("ro.heckeros.version") == cached);
    visible.clear(); __system_property_foreach([](const prop_info* item, void* cookie) {
        static_cast<std::vector<const prop_info*>*>(cookie)->push_back(item);
    }, &visible);
    assert(visible.size() == system_properties.values.size());
    auto* adbd = system_properties.Find("init.svc.adbd");
    auto* pid = system_properties.Find("init.svc_debug_pid.adbd");
    assert(__libc_hecker_set_compatibility_flags(4));
    assert(__system_property_get("init.svc.adbd", value) == 7 && !strcmp(value,"stopped"));
    assert(__system_property_read(adbd, name, value) == 7 && !strcmp(value,"stopped"));
    __system_property_read_callback(adbd,capture,&result);assert(result.value=="stopped");
    assert(__system_property_get("sys.usb.config",value)==3 && !strcmp(value,"mtp"));
    assert(__system_property_find("init.svc_debug_pid.adbd")==nullptr);
    assert(__system_property_read(pid,name,value)==0 && !name[0] && !value[0]);
    assert(__system_property_get("ro.build.type",value)==9 && !strcmp(value,"userdebug"));
    assert(__system_property_find("ro.heckeros.version")==cached); // independent toggles
    visible.clear();__system_property_foreach([](const prop_info* item,void* cookie){
        static_cast<std::vector<const prop_info*>*>(cookie)->push_back(item);
    },&visible);
    assert(visible.size()==system_properties.values.size()-1);
    for(unsigned i=0;i<visible.size();++i)assert(__system_property_find_nth(i)==visible[i]);
    assert(!__system_property_find_nth(visible.size()));
    assert(__libc_hecker_set_compatibility_flags(0));
    assert(__system_property_find("init.svc_debug_pid.adbd")==pid);
    assert(__system_property_get("init.svc.adbd",value)==7 && !strcmp(value,"running"));
    assert(io==0);
    for (auto core : {0u, 1000u, 2000u, 1001000u}) {
        uid = core; assert(!__libc_hecker_set_rom_hide(true));
        assert(!__libc_hecker_set_compatibility_flags(31));assert(!custom_rom_hide_debugging());
        assert(__system_property_find("ro.heckeros.version") == cached);
    }
    printf("Prepared sysprops: Get/Find/read/callback/foreach/nth agree, long names and 5 KB values preserved, opt-out/core UIDs unchanged, no policy-file I/O PASS\n");
}
