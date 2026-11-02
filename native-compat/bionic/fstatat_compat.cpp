/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include "custom_rom_hide.h"
extern "C" int __fstatat64(int, const char*, struct stat*, int);
int fstatat(int fd, const char* path, struct stat* result, int flags) {
    int status = __fstatat64(fd, path, result, flags);
    if (status == 0 && custom_rom_hide_should_block_at(fd, path)) {
        errno = ENOENT;
        return -1;
    }
    return status;
}
__strong_alias(fstatat64, fstatat);
