// SPDX-License-Identifier: Apache-2.0
// Execute prepared Bionic wrappers, preserving their real syscall ordering.
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <initializer_list>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <string>
#include <sys/syscall.h>
#include <unistd.h>
#include "custom_rom_hide.h"

static uid_t uid = 10101;
static int kernelCalls, mapsFd = -1, artifactFd = -1;
static bool failWrites;
static const char* sample;
static const char* linkAlias;
extern "C" void probe_fail_allocations(bool);
extern "C" size_t probe_allocation_calls();
extern "C" size_t probe_outstanding_allocations();
extern "C" void* probe_map(size_t);
extern "C" int probe_unmap(void*, size_t);
static bool invalid(const void* p) {
    return p == nullptr || p == reinterpret_cast<const void*>(0x800000u)
            || p == reinterpret_cast<const void*>(0x80000000u);
}
extern "C" uid_t probe_uid() { return uid; }
extern "C" int __openat(int fd, const char* path, int flags, int mode) {
    ++kernelCalls;
    if (invalid(path)) { errno = EFAULT; return -1; }
    bool maps = !strcmp(path, "/proc/self/maps");
    bool artifact = !strcmp(path, "/system/addon.d/50-lineage.sh");
    int result = static_cast<int>(syscall(SYS_openat, fd, maps || artifact ? sample : path, flags, mode));
    if (result >= 0) {
        if (maps) mapsFd = result; else if (mapsFd == result) mapsFd = -1;
        if (artifact) artifactFd = result; else if (artifactFd == result) artifactFd = -1;
    }
    return result;
}
extern "C" int __faccessat(int, const char* path, int) {
    ++kernelCalls;
    if (invalid(path)) { errno = EFAULT; return -1; }
    return 0;
}
extern "C" int __fstatat64(int, const char* path, struct stat* result, int) {
    ++kernelCalls;
    if (invalid(path) || !result) { errno = EFAULT; return -1; }
    memset(result, 0, sizeof(*result));
    return 0;
}
extern "C" int __statx(int, const char* path, int, unsigned, struct statx* result) {
    ++kernelCalls;
    if (invalid(path) || !result) { errno = EFAULT; return -1; }
    memset(result, 0, sizeof(*result));
    return 0;
}
extern "C" ssize_t __readlinkat(int fd, const char* path, char* buffer, size_t size) {
    ++kernelCalls;
    if (invalid(path) || invalid(buffer)) { errno = EFAULT; return -1; }
    return syscall(SYS_readlinkat, fd, path, buffer, size);
}
extern "C" void __fortify_fatal(const char*) { throw 47; }

extern "C" long probe_syscall(long operation, ...) {
    va_list args;
    va_start(args, operation);
    long result;
    switch (operation) {
        case SYS_prctl: {
            int option = va_arg(args, int); unsigned long flags = va_arg(args, unsigned long);
            assert(option == 0x484b4350 && !(flags & ~4u));
            result = 0; break;
        }
        case SYS_mmap: {
            void* address = va_arg(args, void*); size_t size = va_arg(args, size_t);
            int prot = va_arg(args, int), flags = va_arg(args, int), fd = va_arg(args, int);
            unsigned long offset = va_arg(args, unsigned long);
            assert(address == nullptr && prot == (PROT_READ | PROT_WRITE));
            assert(flags == (MAP_PRIVATE | MAP_ANONYMOUS) && fd == -1 && offset == 0);
            result = reinterpret_cast<long>(probe_map(size)); break;
        }
        case SYS_munmap: {
            void* address = va_arg(args, void*); size_t size = va_arg(args, size_t);
            result = probe_unmap(address, size); break;
        }
        case SYS_openat: {
            int fd = va_arg(args, int); const char* path = va_arg(args, const char*);
            int flags = va_arg(args, int), mode = va_arg(args, int);
            result = syscall(operation, fd, !strcmp(path, "/proc/self/maps") ? sample : path, flags, mode);
            break;
        }
        case SYS_readlinkat: {
            int fd = va_arg(args, int); const char* path = va_arg(args, const char*);
            char* buffer = va_arg(args, char*); size_t capacity = va_arg(args, size_t);
            char maps[64], artifact[64];
            snprintf(maps, sizeof(maps), "/proc/self/fd/%d", mapsFd);
            snprintf(artifact, sizeof(artifact), "/proc/self/fd/%d", artifactFd);
            const char* alias = mapsFd >= 0 && !strcmp(path, maps) ? (linkAlias ? linkAlias : "/proc/self/maps")
                    : artifactFd >= 0 && !strcmp(path, artifact) ? "/system/addon.d/50-lineage.sh" : nullptr;
            if (alias) {
                size_t n = strlen(alias); if (n > capacity) n = capacity;
                memcpy(buffer, alias, n); result = static_cast<long>(n);
            } else result = syscall(operation, fd, path, buffer, capacity);
            break;
        }
        case SYS_read: case SYS_write: {
            int fd = va_arg(args, int); void* data = va_arg(args, void*); size_t size = va_arg(args, size_t);
            if (failWrites && operation == SYS_write) { errno = EIO; result = -1; }
            else result = syscall(operation, fd, data, size);
            break;
        }
        case SYS_close: {
            int fd = va_arg(args, int); result = syscall(operation, fd);
            if (result == 0) {
                if (mapsFd == fd) mapsFd = -1;
                if (artifactFd == fd) artifactFd = -1;
            }
            break;
        }
        case SYS_getcwd: {
            char* data = va_arg(args, char*); size_t size = va_arg(args, size_t);
            result = syscall(operation, data, size); break;
        }
        case SYS_lseek: {
            int fd = va_arg(args, int); off_t at = va_arg(args, off_t); int whence = va_arg(args, int);
            result = syscall(operation, fd, at, whence); break;
        }
        case SYS_memfd_create: {
            const char* name = va_arg(args, const char*); unsigned long flags = va_arg(args, unsigned long);
            result = syscall(operation, name, flags); break;
        }
        default: errno = ENOSYS; result = -1;
    }
    va_end(args);
    return result;
}

int main(int argc, char** argv) {
    assert(argc == 2); sample = argv[1];
    assert(!custom_rom_hide_enabled());
    for (auto core : {0u, 1000u, 2000u, 1001000u}) {
        uid = core; assert(!__libc_hecker_set_rom_hide(true)); assert(!custom_rom_hide_enabled());
    }
    uid = 10101;
    assert(!custom_rom_hide_should_block_at(AT_FDCWD,"/dev/socket/adbd"));
    assert(__libc_hecker_set_compatibility_flags(4));
    assert(custom_rom_hide_should_block_at(AT_FDCWD,"/dev/socket/adbd"));
    assert(!custom_rom_hide_should_block_at(AT_FDCWD,"/dev/socket/adbdoesnotexist"));
    assert(!custom_rom_hide_should_block_at(AT_FDCWD,"/product/overlay/LineageBlackTheme"));
    assert(__libc_hecker_set_compatibility_flags(0));
    assert(__libc_hecker_set_rom_hide(true));
    assert(custom_rom_hide_should_block_at(AT_FDCWD,"/product/overlay/LineageBlackTheme"));
    assert(custom_rom_hide_should_block_at(AT_FDCWD,"/product/overlay/LineageBlackTheme/theme.apk"));
    assert(!custom_rom_hide_should_block_at(AT_FDCWD,"/product/overlay/LineageBlackThemeOther"));
    assert(custom_rom_hide_enabled());
    struct stat st; struct statx sx; char bytes[2048];
    for (auto bad : {static_cast<const char*>(nullptr), reinterpret_cast<const char*>(0x800000u),
                    reinterpret_cast<const char*>(0x80000000u)}) {
        errno = 0; assert(open(bad, O_RDONLY) == -1 && errno == EFAULT);
        errno = 0; assert(openat(AT_FDCWD, bad, O_RDONLY) == -1 && errno == EFAULT);
        errno = 0; assert(probe_open_2(bad, O_RDONLY) == -1 && errno == EFAULT);
        errno = 0; assert(probe_openat_2(AT_FDCWD, bad, O_RDONLY) == -1 && errno == EFAULT);
        errno = 0; assert(faccessat(AT_FDCWD, bad, F_OK, 0) == -1 && errno == EFAULT);
        errno = 0; assert(fstatat(AT_FDCWD, bad, &st, 0) == -1 && errno == EFAULT);
        errno = 0; assert(statx(AT_FDCWD, bad, 0, 0, &sx) == -1 && errno == EFAULT);
        errno = 0; assert(readlinkat(AT_FDCWD, bad, bytes, sizeof(bytes)) == -1 && errno == EFAULT);
    }
    int before = kernelCalls;
    assert(faccessat(AT_FDCWD, "/system/addon.d", F_OK, AT_EACCESS) == -1 && errno == EINVAL);
    assert(kernelCalls == before);
    try { probe_open_2(sample, O_CREAT); assert(false); } catch (int value) { assert(value == 47); }
    assert(faccessat(AT_FDCWD, "/system/addon.d", F_OK, 0) == -1 && errno == ENOENT);
    assert(fstatat(AT_FDCWD, "/system/etc/../addon.d/50-lineage.sh", &st, 0) == -1 && errno == ENOENT);
    assert(statx(AT_FDCWD, "/system/addon.d/test.sh", 0, 0, &sx) == -1 && errno == ENOENT);
    errno = EDOM; assert(fstatat(AT_FDCWD, "/system/addon.dummy", &st, 0) == 0 && errno == EDOM);
    assert(open("/system/addon.d/50-lineage.sh", O_RDONLY) == -1 && errno == ENOENT);
    assert(open("/system/addon.d/50-lineage.sh", O_PATH) == -1 && errno == ENOENT);
    size_t allocations = probe_allocation_calls();
    errno = EDOM;
    assert(fstatat(AT_FDCWD, "/system/addon.d/test", &st, 0) == -1 && errno == ENOENT);
    assert(fstatat(AT_FDCWD, "/system/bin/app_process64", &st, 0) == 0 && errno == ENOENT);
    assert(probe_allocation_calls() == allocations);  // Ordinary path checks do not allocate.
    std::string stem = "/system/";
    for (int i = 0; i < 12; ++i) stem += std::string(240, 'x') + "/";
    for (int i = 0; i < 12; ++i) stem += "../";
    std::string longPath = stem + "addon.d/test";
    errno = EDOM;
    assert(fstatat(AT_FDCWD, longPath.c_str(), &st, 0) == -1 && errno == ENOENT);
    longPath = stem + "bin/app_process64";
    errno = EDOM;
    assert(fstatat(AT_FDCWD, longPath.c_str(), &st, 0) == 0 && errno == EDOM);
    assert(probe_outstanding_allocations() == 0);
    probe_fail_allocations(true); errno = EDOM;
    assert(fstatat(AT_FDCWD, longPath.c_str(), &st, 0) == 0 && errno == EDOM);
    probe_fail_allocations(false);

    // Long relative paths and fd targets retain PATH_MAX handling and release scratch buffers.
    errno = EDOM;
    assert(fstatat(AT_FDCWD, longPath.c_str() + 1, &st, 0) == 0 && errno == EDOM);
    int directory = static_cast<int>(syscall(SYS_openat, AT_FDCWD, "/system", O_PATH, 0));
    if (directory < 0) directory = static_cast<int>(syscall(SYS_openat, AT_FDCWD, "/", O_PATH, 0));
    assert(directory >= 0);
    assert(fstatat(directory, longPath.c_str() + 1, &st, 0) == 0);
    close(directory);
    std::string parent(sample, static_cast<size_t>(strrchr(sample, '/') - sample));
    std::string deep1 = parent + "/" + std::string(150, 'a');
    std::string deep2 = deep1 + "/" + std::string(150, 'b');
    assert(mkdir(deep1.c_str(), 0700) == 0 && mkdir(deep2.c_str(), 0700) == 0);
    int cwd = static_cast<int>(syscall(SYS_openat, AT_FDCWD, ".", O_PATH, 0));
    assert(cwd >= 0 && chdir(deep2.c_str()) == 0);
    errno = EDOM;
    assert(fstatat(AT_FDCWD, "child", &st, 0) == 0 && errno == EDOM);
    probe_fail_allocations(true); errno = EDOM;
    assert(fstatat(AT_FDCWD, "child", &st, 0) == 0 && errno == EDOM);
    probe_fail_allocations(false);
    assert(fchdir(cwd) == 0); close(cwd);
    assert(rmdir(deep2.c_str()) == 0 && rmdir(deep1.c_str()) == 0);
    linkAlias = longPath.c_str();
    errno = EDOM;
    int longFd = open("/proc/self/maps", O_RDONLY);
    assert(longFd >= 0 && errno == EDOM); close(longFd);
    probe_fail_allocations(true); errno = EDOM;
    longFd = open("/proc/self/maps", O_RDONLY);
    assert(longFd >= 0 && errno == EDOM); close(longFd);
    probe_fail_allocations(false); linkAlias = nullptr;
    assert(probe_outstanding_allocations() == 0);

    errno = EDOM;
    int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
    assert(fd >= 0 && errno == EDOM);
    assert((fcntl(fd, F_GETFL) & O_ACCMODE) == O_RDONLY);
    assert((fcntl(fd, F_GETFD) & FD_CLOEXEC) != 0);
    ssize_t n = read(fd, bytes, sizeof(bytes) - 1); assert(n > 0); bytes[n] = 0;
    assert(strstr(bytes, "libc.so") && !strstr(bytes, "lineage"));
    assert(strstr(bytes, "com.heckerboard"));
    char link[64]; snprintf(link, sizeof(link), "/proc/self/fd/%d", fd);
    n = readlinkat(AT_FDCWD, link, bytes, sizeof(bytes));
    assert(n == static_cast<ssize_t>(strlen("/proc/self/maps")));
    assert(!memcmp(bytes, "/proc/self/maps", n));
    for (size_t capacity = 1; capacity < strlen("/proc/self/maps"); ++capacity) {
        memset(bytes, 0x7f, sizeof(bytes)); errno = EDOM;
        n = readlinkat(AT_FDCWD, link, bytes, capacity);
        assert(n == static_cast<ssize_t>(capacity) && errno == EDOM);
        assert(!memcmp(bytes, "/proc/self/maps", capacity) && bytes[capacity] == 0x7f);
    }
    close(fd);

    failWrites = true; errno = EDOM;
    fd = open("/proc/self/maps", O_RDONLY); assert(fd >= 0 && errno == EDOM);
    n = read(fd, bytes, sizeof(bytes) - 1); assert(n > 0); bytes[n] = 0;
    assert(strstr(bytes, "lineage")); close(fd); failWrites = false;
    fd = open("/proc/self/maps", O_RDWR); assert(fd >= 0);
    n = read(fd, bytes, sizeof(bytes) - 1); assert(n > 0); bytes[n] = 0;
    assert(strstr(bytes, "lineage")); close(fd);
    assert(probe_outstanding_allocations() == 0);
    assert(__libc_hecker_set_rom_hide(false)); assert(!custom_rom_hide_enabled());
    assert(fstatat(AT_FDCWD, "/system/addon.d", &st, 0) == 0);
    fd = open("/proc/self/maps", O_RDONLY); assert(fd >= 0);
    n = read(fd, bytes, sizeof(bytes) - 1); assert(n > 0); bytes[n] = 0;
    assert(strstr(bytes, "lineage")); close(fd);
    printf("Prepared libc: opt-in/reset/core UIDs, invalid pointers and EFAULT, fortify, path boundaries, read-only flags, errno, content filters and failure fallback PASS\n");
}
