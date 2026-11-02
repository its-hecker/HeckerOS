/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
#include <errno.h>
#include <sys/types.h>
#include <unistd.h>
#include "custom_rom_hide.h"
extern "C" ssize_t __readlinkat(int, const char*, char*, size_t);
ssize_t readlinkat(int fd, const char* path, char* buffer, size_t size) {
    ssize_t result = __readlinkat(fd, path, buffer, size);
    if (result < 0) return result;
    if (custom_rom_hide_should_block_at(fd, path)) {
        errno = ENOENT;
        return -1;
    }
    return custom_rom_hide_readlink_post(fd, path, buffer, size, result);
}
