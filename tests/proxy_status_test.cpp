// SPDX-License-Identifier: Apache-2.0
#include "../vpn/native/proxy_status.h"
#include <cassert>
#include <filesystem>
#include <iostream>
using namespace hecker::vpn;
int main() {
    char temp[] = "/tmp/hecker-status-XXXXXX"; assert(mkdtemp(temp));
    std::string file = std::string(temp) + "/startup", outside = std::string(temp) + "/outside";
    TcpStartup status(file.c_str()); TcpStartupSnapshot read;
    status.enter(TcpPhase::configuration); status.fail(tcp_config_failure("DNS nameserver rejected: secret-proxy-password"));
    assert(TcpStartup::read(file.c_str(), read));
    assert(read.phase == TcpPhase::configuration && read.failure == TcpFailure::dns);
    assert(tcp_startup_message(read).find("secret-proxy-password") == std::string::npos);
    status.enter(TcpPhase::tun_attach); status.fail(TcpFailure::system, EACCES);
    assert(TcpStartup::read(file.c_str(), read) && read.error == EACCES);
    struct stat st{}; assert(!stat(file.c_str(), &st) && (st.st_mode & 0777) == 0600);
    auto write = [&](const char* text) { FILE* out = fopen(file.c_str(), "w"); assert(out); assert(fputs(text, out) >= 0); fclose(out); };
    for (const char* invalid : {"999 0 0\n", "0 999 0\n", "0 0 -1\n", "0 0 4096\n", "4294967296 0 0\n", "1 2 3 extra", "secret-proxy-password", "1 2"}) {
        write(invalid); assert(!TcpStartup::read(file.c_str(), read));
    }
    assert(!rename(file.c_str(), outside.c_str())); assert(!symlink(outside.c_str(), file.c_str()));
    status.enter(TcpPhase::input); assert(!TcpStartup::read(file.c_str(), read));
    assert(!stat(outside.c_str(), &st) && st.st_size == 3);
    assert(!unlink(file.c_str())); assert(!link(outside.c_str(), file.c_str()));
    status.enter(TcpPhase::input); assert(!TcpStartup::read(file.c_str(), read));
    assert(!stat(outside.c_str(), &st) && st.st_size == 3);
    assert(!unlink(file.c_str())); assert(!unlink(outside.c_str()));
    assert(!mkfifo(file.c_str(), 0600)); assert(!TcpStartup::read(file.c_str(), read));
    std::filesystem::remove_all(temp);
    std::cout << "TCP startup status: credential redaction, bounded parsing, private mode, symlink/hardlink/FIFO refusal PASS\n";
}
