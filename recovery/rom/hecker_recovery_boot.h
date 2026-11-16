// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <string_view>

namespace android::init::hecker_recovery {

constexpr char kPayload[] = "/hecker-recovery.cpio.zst";
constexpr unsigned kNoMerge = 0;

// Keep Android's recovery in charge of unattended requests and snapshot merges.
// A menu reboot may leave harmless locale/reason information in the BCB;
// rejecting it would silently fall back to the stock Recovery menu.
constexpr bool InteractiveRecoveryOptions(std::string_view options) {
    if (options.empty() || options == "recovery") return true;
    if (options.substr(0, 9) != "recovery\n") return false;
    options.remove_prefix(9);
    while (!options.empty()) {
        const size_t newline = options.find('\n');
        const std::string_view line = options.substr(0, newline);
        const bool harmless = line == "--show_text" ||
                              line.substr(0, 9) == "--locale=" ||
                              line.substr(0, 9) == "--reason=";
        if (!harmless) return false;
        if (newline == std::string_view::npos) return true;
        options.remove_prefix(newline + 1);
    }
    return true;
}

constexpr bool UseTouchRecovery(std::string_view command, std::string_view options,
                                unsigned merge_status) {
    return (command.empty() || command == "boot-recovery") &&
           InteractiveRecoveryOptions(options) && merge_status == kNoMerge;
}

void StartTouchRecovery();

}  // namespace android::init::hecker_recovery
