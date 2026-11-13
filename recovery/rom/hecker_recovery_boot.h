// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <string_view>

namespace android::init::hecker_recovery {

constexpr char kPayload[] = "/hecker-recovery.cpio.zst";
constexpr unsigned kNoMerge = 0;

// Keep Android's recovery in charge of unattended requests and snapshot merges.
constexpr bool UseTouchRecovery(std::string_view command, std::string_view options,
                                unsigned merge_status) {
    return (command.empty() || command == "boot-recovery") &&
           (options.empty() || options == "recovery" || options == "recovery\n") &&
           merge_status == kNoMerge;
}

void StartTouchRecovery();

}  // namespace android::init::hecker_recovery
