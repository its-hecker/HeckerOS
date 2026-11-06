/* Copyright (C) 2025-2026 AxionOS, VoltageOS
 * Copyright (C) 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from VoltageOS/bionic custom_rom_hide, including the July 2026 fixes.
 * Policy is process-local and opt-in. Original kernel permission/error checks run first.
 */
#include "custom_rom_hide.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/memfd.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/mman.h>
#include <unistd.h>

static bool g_enabled;
static unsigned g_compatibility_flags;
static char g_stock_fingerprint[92];
static char g_stock_flavor[96];
struct prop_info;
extern "C" const prop_info* __system_property_find(const char*) __attribute__((weak));
extern "C" void __system_property_read_callback(const prop_info*,
        void (*)(void*, const char*, const char*, uint32_t), void*) __attribute__((weak));
struct ProfileValue { char* target; size_t capacity; };
static void capture_profile_value(void* cookie, const char*, const char* value, uint32_t) {
    auto* out = static_cast<ProfileValue*>(cookie);
    size_t length = strnlen(value, out->capacity);
    if (length < out->capacity) memcpy(out->target, value, length + 1);
}
static void capture_stock_profile() {
    g_stock_fingerprint[0] = 0;
    g_stock_flavor[0] = 0;
    if (!__system_property_find || !__system_property_read_callback) return;
    ProfileValue fingerprint{g_stock_fingerprint, sizeof(g_stock_fingerprint)};
    const prop_info* fp = __system_property_find("ro.build.fingerprint");
    if (fp) __system_property_read_callback(fp, capture_profile_value, &fingerprint);
    char device[80] = {};
    ProfileValue product{device, sizeof(device)};
    const prop_info* name = __system_property_find("ro.product.device");
    if (name) __system_property_read_callback(name, capture_profile_value, &product);
    if (device[0]) snprintf(g_stock_flavor, sizeof(g_stock_flavor), "%s-user", device);
}
bool __libc_hecker_set_compatibility_flags(unsigned flags) {
    bool app = getuid() % 100000 >= 10000;
    // The matched Coral kernel stores only this self-scoped, non-privileged bit
    // in copy-on-write credentials. Threads/forks inherit it, including exec.
    // Older kernels keep the libc/Settings protection if the private call is absent.
    int saved = errno;
    syscall(__NR_prctl, 0x484b4350, static_cast<unsigned long>(app ? flags & 4u : 0u), 0UL, 0UL, 0UL);
    errno = saved;
    __atomic_store_n(&g_compatibility_flags, app ? flags & 31u : 0u, __ATOMIC_RELAXED);
    return app;
}
bool custom_rom_hide_debugging() {
    return getuid() % 100000 >= 10000
            && (__atomic_load_n(&g_compatibility_flags, __ATOMIC_RELAXED) & 4u) != 0;
}
bool __libc_hecker_set_rom_hide(bool enabled) {
    bool app = getuid() % 100000 >= 10000;
    if (app && enabled) capture_stock_profile();
    __atomic_store_n(&g_enabled, app && enabled, __ATOMIC_RELAXED);
    return app;
}
bool custom_rom_hide_enabled() {
    // No package lookup, Settings read, allowlist file, allocation or PID scan on hot paths.
    return __atomic_load_n(&g_enabled, __ATOMIC_RELAXED) && getuid() % 100000 >= 10000;
}

static int raw_close(int fd) { return static_cast<int>(syscall(__NR_close, fd)); }
// Bionic requires every stack frame to stay below 2 KiB. Common paths use a
// small local buffer; long paths use a temporary mapping without reducing PATH_MAX
// or entering allocator locks from access/stat/readlink paths.
// Each call owns its scratch space, including nested calls and other threads.
class PathBuffer {
    char local_[256];
    char* data_ = local_;
    size_t capacity_ = sizeof(local_);
public:
    PathBuffer() = default;
    PathBuffer(const PathBuffer&) = delete;
    PathBuffer& operator=(const PathBuffer&) = delete;
    ~PathBuffer() {
        int saved = errno;
        if (data_ != local_) syscall(__NR_munmap, data_, capacity_);
        errno = saved;
    }
    char* data() { return data_; }
    size_t capacity() const { return capacity_; }
    // Callers overwrite the buffer after growth; no stale contents are retained.
    bool reserve(size_t capacity) {
        if (capacity <= capacity_) return true;
        if (capacity > PATH_MAX) return false;
        // mmap2 uses page units for the offset on 32-bit ARM; zero is valid for both ABIs.
#if defined(__NR_mmap2)
        constexpr long mapping = __NR_mmap2;
#else
        constexpr long mapping = __NR_mmap;
#endif
        void* memory = reinterpret_cast<void*>(syscall(mapping, nullptr, capacity,
                PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0UL));
        if (memory == MAP_FAILED) return false;
        char* larger = static_cast<char*>(memory);
        if (data_ != local_) syscall(__NR_munmap, data_, capacity_);
        data_ = larger;
        capacity_ = capacity;
        return true;
    }
};
static bool fd_path(int fd, PathBuffer& out) {
    if (fd < 0) return false;
    char link[64];
    snprintf(link, sizeof(link), "/proc/self/fd/%d", fd);
    ssize_t size = syscall(__NR_readlinkat, AT_FDCWD, link, out.data(), out.capacity() - 1);
    if (size > 0 && static_cast<size_t>(size) >= out.capacity() - 1) {
        if (!out.reserve(PATH_MAX)) return false;
        size = syscall(__NR_readlinkat, AT_FDCWD, link, out.data(), out.capacity() - 1);
    }
    if (size <= 0 || static_cast<size_t>(size) >= out.capacity() - 1) return false;
    out.data()[size] = 0;
    return true;
}
static bool normalize(const char* in, char* out, size_t capacity) {
    if (!in || in[0] != '/') return false;
    size_t length = strnlen(in, capacity);
    if (length >= capacity) return false;
    size_t used = 1;
    out[0] = '/';
    for (size_t i = 1; i < length;) {
        while (i < length && in[i] == '/') ++i;
        size_t start = i;
        while (i < length && in[i] != '/') ++i;
        size_t n = i - start;
        if (!n || (n == 1 && in[start] == '.')) continue;
        if (n == 2 && in[start] == '.' && in[start + 1] == '.') {
            while (used > 1 && out[used - 1] != '/') --used;
            if (used > 1) --used;
            continue;
        }
        if (used > 1) out[used++] = '/';
        if (used + n >= capacity) return false;
        memcpy(out + used, in + start, n);
        used += n;
    }
    out[used] = 0;
    return true;
}
static bool normalize_path(const char* in, PathBuffer& out) {
    size_t length = strnlen(in, PATH_MAX);
    return length < PATH_MAX && out.reserve(length + 1)
            && normalize(in, out.data(), out.capacity());
}
static bool rom_path(const char* path) {
    if (custom_rom_hide_debugging() && !strcmp(path, "/dev/socket/adbd")) return true;
    if (!custom_rom_hide_enabled()) return false;
    static constexpr char overlay[] = "/product/overlay/LineageBlackTheme";
    constexpr size_t length = sizeof(overlay) - 1;
    if (!strncmp(path, overlay, length) && (!path[length] || path[length] == '/')) return true;
    static const char* const parents[] = {"/system", "/system/etc", "/system_ext",
        "/system_ext/etc", "/product", "/product/etc", "/vendor", "/vendor/etc",
        "/sdcard", "/data/media/0", nullptr};
    static const char* const directories[] = {"addon.d", "init.d", "TWRP", nullptr};
    for (const char* const* parent = parents; *parent; ++parent) {
        size_t n = strlen(*parent);
        if (strncmp(path, *parent, n) || path[n] != '/') continue;
        const char* leaf = path + n + 1;
        for (const char* const* item = directories; *item; ++item) {
            size_t m = strlen(*item);
            if (!strncmp(leaf, *item, m) && (!leaf[m] || leaf[m] == '/')) return true;
        }
    }
    return false;
}
bool custom_rom_hide_should_block_at(int dirfd, const char* path) {
    if ((!custom_rom_hide_enabled() && !custom_rom_hide_debugging()) || !path || !*path) return false;
    int saved = errno;
    PathBuffer absolute, clean;
    bool valid = true;
    if (*path == '/') {
        valid = normalize_path(path, clean);
    } else {
        PathBuffer directory;
        if (dirfd == AT_FDCWD) {
            long result = syscall(__NR_getcwd, directory.data(), directory.capacity());
            if (result < 0 && errno == ERANGE && directory.reserve(PATH_MAX)) {
                result = syscall(__NR_getcwd, directory.data(), directory.capacity());
            }
            valid = result > 0;
        } else {
            valid = fd_path(dirfd, directory);
        }
        if (valid) {
            size_t capacity = strlen(directory.data()) + 1 + strnlen(path, PATH_MAX) + 1;
            valid = absolute.reserve(capacity);
            if (valid) {
                int length = snprintf(absolute.data(), absolute.capacity(), "%s/%s", directory.data(), path);
                valid = length > 0 && static_cast<size_t>(length) < absolute.capacity()
                        && normalize_path(absolute.data(), clean);
            }
        }
    }
    bool blocked = valid && rom_path(clean.data());
    errno = saved;
    return blocked;
}
bool custom_rom_hide_should_filter_dirent(int dirfd, const char* name) {
    if ((!custom_rom_hide_enabled() && !custom_rom_hide_debugging()) || !name) return false;
    if (strcmp(name, "addon.d") && strcmp(name, "init.d") && strcmp(name, "TWRP")
            && strcmp(name, "LineageBlackTheme") && strcmp(name, "adbd")) return false;
    return custom_rom_hide_should_block_at(dirfd, name);
}

enum Filter { NONE, MAPS, MOUNTS, FILESYSTEMS, CONTEXTS, UNIX_SOCKETS };
static Filter filter_type(const char* path) {
    if (custom_rom_hide_debugging() && !strncmp(path, "/proc/", 6)) {
        const char* net = strstr(path + 6, "/net/unix");
        if (!strcmp(path, "/proc/net/unix") || (net && !net[9])) return UNIX_SOCKETS;
    }
    if (!custom_rom_hide_enabled()) return NONE;
    if (!strcmp(path, "/proc/filesystems")) return FILESYSTEMS;
    if (!strncmp(path, "/proc/", 6)) {
        const char* leaf = strrchr(path, '/');
        if (leaf && (!strcmp(leaf, "/maps") || !strcmp(leaf, "/smaps"))) return MAPS;
        if (leaf && (!strcmp(leaf, "/mounts") || !strcmp(leaf, "/mountinfo"))) return MOUNTS;
    }
    static const char* const contexts[] = {
        "/system/etc/selinux/plat_service_contexts", "/system/etc/selinux/plat_seapp_contexts",
        "/system/etc/selinux/plat_file_contexts", "/system/etc/selinux/plat_property_contexts",
        "/system/etc/selinux/plat_sepolicy.cil", "/system_ext/etc/selinux/system_ext_service_contexts",
        "/system_ext/etc/selinux/system_ext_seapp_contexts", "/system_ext/etc/selinux/system_ext_file_contexts",
        "/system_ext/etc/selinux/system_ext_property_contexts", "/system_ext/etc/selinux/system_ext_sepolicy.cil",
        "/product/etc/selinux/product_service_contexts", "/product/etc/selinux/product_seapp_contexts",
        "/product/etc/selinux/product_file_contexts", "/product/etc/selinux/product_property_contexts",
        "/vendor/etc/selinux/vendor_file_contexts", "/vendor/etc/selinux/vendor_service_contexts",
        "/vendor/etc/selinux/vendor_hwservice_contexts", "/vendor/etc/selinux/vendor_property_contexts",
        "/vendor/etc/selinux/vendor_sepolicy.cil", "/vendor/etc/selinux/plat_pub_versioned.cil", nullptr};
    for (const char* const* item = contexts; *item; ++item) if (!strcmp(path, *item)) return CONTEXTS;
    return NONE;
}
static bool drop_line(Filter type, const char* line) {
    if (type == UNIX_SOCKETS)
        return strstr(line, "@jdwp-control") || strstr(line, "/dev/socket/adbd");
    static const char* const roms[] = {"lineage", "Lineage", "voltage", "VoltageOS",
        "hecker", "Hecker", "omnirom", "aospa", nullptr};
    static const char* const mounts[] = {"/debug_ramdisk", "overlay", "magisk", "ksu",
        "KSU", "ksud", "apatch", "/data/adb", nullptr};
    const char* const* words = type == MOUNTS ? mounts : roms;
    if (type == FILESYSTEMS) return strstr(line, "overlay") != nullptr;
    if (type == MAPS) {
        const char* slash = strchr(line, '/');
        if (!slash) return false;
        line = slash;
        // Match path/package components, not an unrelated app such as "com.heckerboard".
        for (; *words; ++words) {
            size_t n = strlen(*words);
            for (const char* at = strstr(line, *words); at; at = strstr(at + 1, *words)) {
                char before = at == line ? '/' : at[-1], after = at[n];
                if (strchr("/._-", before) && (!after || strchr("/._- \n", after))) return true;
            }
        }
        return false;
    }
    for (; *words; ++words) if (strstr(line, *words)) return true;
    return false;
}
static bool write_all(int fd, const char* data, size_t size) {
    while (size) {
        ssize_t done = syscall(__NR_write, fd, data, size);
        if (done < 0 && errno == EINTR) continue;
        if (done <= 0) return false;
        data += done;
        size -= static_cast<size_t>(done);
    }
    return true;
}
static int filtered_copy(const char* path, Filter type, bool cloexec) {
    int source = static_cast<int>(syscall(__NR_openat, AT_FDCWD, path, O_RDONLY | O_CLOEXEC, 0));
    if (source < 0) return -1;
    size_t capacity = 16384, used = 0;
    char* text = static_cast<char*>(malloc(capacity + 1));
    bool ok = text != nullptr;
    while (ok) {
        if (used == capacity) {
            if (capacity >= 8 * 1024 * 1024) { ok = false; break; }
            capacity *= 2;
            char* expanded = static_cast<char*>(realloc(text, capacity + 1));
            if (!expanded) { ok = false; break; }
            text = expanded;
        }
        ssize_t count = syscall(__NR_read, source, text + used, capacity - used);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) { ok = false; break; }
        if (!count) break;
        used += static_cast<size_t>(count);
    }
    raw_close(source);
    if (!ok) { free(text); return -1; }
    text[used] = 0;
    // Encode the actual source path, as VoltageOS does, to retain readlink's path semantics.
    char name[249] = "hrv:";
    size_t n = strlen(path);
    if (n > (sizeof(name) - 5) / 2) { free(text); return -1; }
    static const char hex[] = "0123456789abcdef";
    for (size_t i = 0; i < n; ++i) {
        name[4 + 2*i] = hex[static_cast<unsigned char>(path[i]) >> 4];
        name[5 + 2*i] = hex[path[i] & 15];
    }
    name[4 + 2*n] = 0;
    int result = static_cast<int>(syscall(__NR_memfd_create, name, cloexec ? MFD_CLOEXEC : 0));
    if (result < 0) { free(text); return -1; }
    for (size_t start = 0; start < used;) {
        char* newline = static_cast<char*>(memchr(text + start, '\n', used - start));
        size_t end = newline ? static_cast<size_t>(newline - text) + 1 : used;
        char saved = text[end];
        text[end] = 0;
        bool drop = drop_line(type, text + start);
        text[end] = saved;
        if (!drop && !write_all(result, text + start, end - start)) { ok = false; break; }
        start = end;
    }
    free(text);
    if (!ok || syscall(__NR_lseek, result, 0, SEEK_SET) < 0) { raw_close(result); return -1; }
    char link[64];
    snprintf(link, sizeof(link), "/proc/self/fd/%d", result);
    int readonly = static_cast<int>(syscall(__NR_openat, AT_FDCWD, link,
            O_RDONLY | (cloexec ? O_CLOEXEC : 0), 0));
    raw_close(result);
    return readonly;
}
int custom_rom_hide_post_open(int fd, int flags) {
    if (fd < 0 || (!custom_rom_hide_enabled() && !custom_rom_hide_debugging())) return fd;
    // Never replace writable or creation handles. Keep errno and the original fd on failure.
    if ((flags & O_ACCMODE) != O_RDONLY || (flags & (O_CREAT | O_TRUNC))
            || (flags & O_TMPFILE) == O_TMPFILE) return fd;
    int saved = errno;
    PathBuffer path, clean;
    if (!fd_path(fd, path) || !normalize_path(path.data(), clean)) { errno = saved; return fd; }
    if (rom_path(clean.data())) { raw_close(fd); errno = ENOENT; return -1; }
    if (flags & O_PATH) { errno = saved; return fd; }
    Filter type = filter_type(clean.data());
    int replacement = type == NONE ? -1 : filtered_copy(clean.data(), type, (flags & O_CLOEXEC) != 0);
    if (replacement >= 0) { raw_close(fd); fd = replacement; }
    errno = saved;
    return fd;
}
static int unhex(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}
ssize_t custom_rom_hide_readlink_post(int dirfd, const char* path, char* buffer, size_t size, ssize_t result) {
    static const char prefix[] = "/memfd:hrv:";
    constexpr size_t length = sizeof(prefix) - 1;
    if (result <= 0 || !custom_rom_hide_enabled()) return result;
    // The caller's buffer may be too short to contain the encoded name. The kernel has
    // already validated it; read the complete link into scratch space before truncation.
    int saved = errno;
    // memfd names have at most 248 bytes, plus the prefix and deleted suffix.
    char target[sizeof("/memfd:") + 249 + sizeof(" (deleted)")];
    ssize_t count = syscall(__NR_readlinkat, dirfd, path, target, sizeof(target));
    errno = saved;
    if (count <= static_cast<ssize_t>(length) || memcmp(target, prefix, length)) return result;
    char decoded[123];
    size_t used = 0, at = length;
    while (at + 1 < static_cast<size_t>(count) && used < sizeof(decoded)) {
        int a = unhex(target[at]), b = unhex(target[at + 1]);
        if (a < 0 || b < 0) break;
        decoded[used++] = static_cast<char>(16*a + b);
        at += 2;
    }
    if (!used || decoded[0] != '/') return result;
    if (used > size) used = size;
    memcpy(buffer, decoded, used);
    return static_cast<ssize_t>(used);
}
bool custom_rom_hide_should_hide_prop(const char* name) {
    if (!name) return false;
    if (custom_rom_hide_debugging() && (!strcmp(name, "init.svc_debug_pid.adbd")
            || !strcmp(name, "init.svc_debug_pid.adb_root"))) return true;
    if (!custom_rom_hide_enabled()) return false;
    static const char* const prefixes[] = {"ro.heckeros.", "ro.hecker.", "ro.lineage.",
        "org.lineageos.", "ro.voltage.", "org.voltage.", nullptr};
    for (const char* const* prefix = prefixes; *prefix; ++prefix)
        if (!strncmp(name, *prefix, strlen(*prefix))) return true;
    return !strcmp(name, "ro.modversion") || !strcmp(name, "init.svc_debug_pid.adb_root")
            || !strcmp(name, "init.svc.adb_root") || !strcmp(name, "service.adb.root");
}
const char* custom_rom_hide_get_prop_override(const char* name) {
    if (!name) return nullptr;
    if (custom_rom_hide_debugging()) {
        if (custom_rom_hide_should_hide_prop(name)) return "";
        if (!strcmp(name, "init.svc.adbd")) return "stopped";
        if (!strcmp(name, "persist.sys.usb.config") || !strcmp(name, "sys.usb.config")
                || !strcmp(name, "sys.usb.state")) return "mtp";
        if (!strcmp(name, "service.adb.tcp.port") || !strcmp(name, "persist.adb.tcp.port")) return "-1";
        if (!strcmp(name, "persist.adb.wifi.enabled")) return "0";
    }
    if (!custom_rom_hide_enabled()) return nullptr;
    if (custom_rom_hide_should_hide_prop(name)) return "";
    if (!strcmp(name, "ro.debuggable")) return "0";
    if (!strcmp(name, "ro.build.type")) return "user";
    if (!strcmp(name, "ro.build.tags")) return "release-keys";
    if (!strcmp(name, "ro.secure") || !strcmp(name, "ro.adb.secure")) return "1";
    if (!strcmp(name, "ro.build.flavor") && g_stock_flavor[0]) return g_stock_flavor;
    static const char* const partitions[] = {"ro.system.build.", "ro.system_ext.build.",
        "ro.product.build.", "ro.vendor.build.", "ro.odm.build.", "ro.bootimage.build.", nullptr};
    for (const char* const* p = partitions; *p; ++p) {
        size_t length = strlen(*p);
        if (strncmp(name, *p, length)) continue;
        const char* field = name + length;
        if (!strcmp(field, "fingerprint") && g_stock_fingerprint[0]) return g_stock_fingerprint;
        if (!strcmp(field, "type")) return "user";
        if (!strcmp(field, "tags")) return "release-keys";
    }
    return nullptr;
}
