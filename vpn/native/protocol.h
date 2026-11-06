// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <string>
namespace hecker::vpn {
constexpr size_t kMaxConfig = 65536;
bool valid_config(const std::string& config);
bool parse_up(const std::string& command,unsigned int& netid,std::string& config);
bool authorized_peer(unsigned int uid, const std::string& context);
}
