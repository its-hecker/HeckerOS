/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
#include <errno.h>
#include <sys/stat.h>
#include "custom_rom_hide.h"
extern "C" int __statx(int, const char*, int, unsigned, struct statx*);
int statx(int fd, const char* path, int flags, unsigned mask, struct statx* result) {
    int status = __statx(fd, path, flags, mask, result);
    if (status == 0 && custom_rom_hide_should_block_at(fd, path)) {
        errno = ENOENT;
        return -1;
    }
    return status;
}
