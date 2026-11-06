/* Copyright (C) 2025-2026 AxionOS, VoltageOS
 * Copyright (C) 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0 */
#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <sys/types.h>

__BEGIN_DECLS
/* Platform-private handshake, reset before package code; not an NDK API. */
__attribute__((visibility("default"))) bool __libc_hecker_set_rom_hide(bool enabled);
__attribute__((visibility("default"))) bool __libc_hecker_set_compatibility_flags(unsigned flags);
__attribute__((visibility("hidden"))) bool custom_rom_hide_debugging(void);
__attribute__((visibility("hidden"))) bool custom_rom_hide_enabled(void);
__attribute__((visibility("hidden"))) bool custom_rom_hide_should_block_at(int dirfd, const char* path);
__attribute__((visibility("hidden"))) bool custom_rom_hide_should_filter_dirent(int dirfd, const char* name);
__attribute__((visibility("hidden"))) int custom_rom_hide_post_open(int fd, int flags);
__attribute__((visibility("hidden"))) ssize_t custom_rom_hide_readlink_post(int dirfd, const char* path, char* buffer, size_t size, ssize_t result);
__attribute__((visibility("hidden"))) bool custom_rom_hide_should_hide_prop(const char* name);
__attribute__((visibility("hidden"))) const char* custom_rom_hide_get_prop_override(const char* name);
__END_DECLS
