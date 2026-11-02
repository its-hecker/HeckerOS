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
#include <sys/syscall.h>
#include <unistd.h>
#include "custom_rom_hide.h"

static uid_t uid = 10101;
static int kernelCalls, mapsFd = -1, artifactFd = -1;
static bool failWrites;
static const char* sample;
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
            const char* alias = mapsFd >= 0 && !strcmp(path, maps) ? "/proc/self/maps"
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
        case SYS_close: result = syscall(operation, va_arg(args, int)); break;
        case SYS_getcwd: {
            char* data = va_arg(args, char*); size_t size = va_arg(args, size_t);
            result = syscall(operation, data, size); break;
        }
        case SYS_lseek: {
            int fd = va_arg(args, int); off_t at = va_arg(args, off_t); int whence = va_arg(args, int);
            result = syscall(operation, fd, at, whence); break;
        }
        case SYS_memfd_create: {
            const char* name = va_arg(args, const char*); unsigned flags = va_arg(args, unsigned);
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
    uid = 10101; assert(__libc_hecker_set_rom_hide(true));
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
    assert(__libc_hecker_set_rom_hide(false)); assert(!custom_rom_hide_enabled());
    assert(fstatat(AT_FDCWD, "/system/addon.d", &st, 0) == 0);
    fd = open("/proc/self/maps", O_RDONLY); assert(fd >= 0);
    n = read(fd, bytes, sizeof(bytes) - 1); assert(n > 0); bytes[n] = 0;
    assert(strstr(bytes, "lineage")); close(fd);
    printf("Prepared libc: opt-in/reset/core UIDs, invalid pointers and EFAULT, fortify, path boundaries, read-only flags, errno, content filters and failure fallback PASS\n");
}
