/* Copyright (C) 2026 AxionOS, VoltageOS
 * Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
#pragma once
#include <sys/types.h>
#include <string>
namespace android::hecker {
inline bool hideRomService(uid_t uid, const std::string& sid, const std::string& name) {
    if (uid % 100000 < 10000) return false;
    constexpr char prefix[] = "u:r:untrusted_app";
    constexpr size_t size = sizeof(prefix) - 1;
    if (sid.size() <= size || sid.compare(0, size, prefix)
            || (sid[size] != ':' && sid[size] != '_')) return false;
    return name.compare(0, 7, "lineage") == 0
            || name.compare(0, 15, "vendor.lineage.") == 0 || name == "profile";
}
}
