// SPDX-License-Identifier: Apache-2.0
#include "hecker_recovery_boot.h"

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <bootloader_message/bootloader_message.h>
#include <zstd.h>

#include <array>
#include <memory>

#include "block_dev_initializer.h"
#include "switch_root.h"

namespace android::init::hecker_recovery {
namespace {

constexpr char kRoot[] = "/hecker-recovery";
constexpr char kArchive[] = "/hecker-recovery/.cpio";
constexpr char kCpio[] = "/system/bin/cpio";
constexpr char kRecoveryInit[] = "/system/bin/init";
constexpr size_t kExpandedLimit = 96 * 1024 * 1024;
constexpr char kMisc[] = "/dev/block/by-name/misc";

bool InteractiveBoot() {
    if (access(kMisc, R_OK) != 0) {
        BlockDevInitializer devices;
        if (!devices.InitBootDevicesFromPartUuid() || !devices.InitDevices({"misc"})) {
            return false;
        }
    }

    android::base::unique_fd fd(open(kMisc, O_RDONLY | O_CLOEXEC));
    bootloader_message bcb{};
    misc_virtual_ab_message merge{};
    if (fd < 0 ||
        !android::base::ReadFullyAtOffset(fd, &bcb, sizeof(bcb), BOOTLOADER_MESSAGE_OFFSET_IN_MISC) ||
        !android::base::ReadFullyAtOffset(fd, &merge, sizeof(merge), SYSTEM_SPACE_OFFSET_IN_MISC)) {
        PLOG(ERROR) << "Could not read Coral recovery mode from misc";
        return false;
    }

    if (merge.magic == MISC_VIRTUAL_AB_MAGIC_HEADER &&
        merge.version != 1 && merge.version != MISC_VIRTUAL_AB_MESSAGE_VERSION) {
        return false;
    }

    const unsigned status =
            merge.magic == MISC_VIRTUAL_AB_MAGIC_HEADER ? merge.merge_status : kNoMerge;
    return UseTouchRecovery(std::string_view(bcb.command, strnlen(bcb.command, sizeof(bcb.command))),
                            std::string_view(bcb.recovery,
                                             strnlen(bcb.recovery, sizeof(bcb.recovery))),
                            status);
}

bool Unpack(int input, int output) {
    std::unique_ptr<ZSTD_DCtx, decltype(&ZSTD_freeDCtx)> decoder(ZSTD_createDCtx(), ZSTD_freeDCtx);
    if (!decoder || ZSTD_isError(ZSTD_DCtx_setParameter(decoder.get(), ZSTD_d_windowLogMax, 27))) {
        return false;
    }

    std::array<char, 65536> compressed;
    std::array<char, 65536> expanded;
    size_t total = 0;
    size_t remaining = 1;
    ssize_t count;
    while ((count = TEMP_FAILURE_RETRY(read(input, compressed.data(), compressed.size()))) > 0) {
        ZSTD_inBuffer source = {compressed.data(), static_cast<size_t>(count), 0};
        while (source.pos < source.size) {
            ZSTD_outBuffer target = {expanded.data(), expanded.size(), 0};
            remaining = ZSTD_decompressStream(decoder.get(), &target, &source);
            total += target.pos;
            if (ZSTD_isError(remaining) || total > kExpandedLimit ||
                !android::base::WriteFully(output, expanded.data(), target.pos)) {
                return false;
            }
        }
    }
    return count == 0 && remaining == 0 && total != 0;
}

bool ExtractRecovery() {
    android::base::unique_fd input(open(kPayload, O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
    if (input < 0 || mkdir(kRoot, 0755) != 0 ||
        mount("tmpfs", kRoot, "tmpfs", 0, "mode=0755,size=192M") != 0) {
        PLOG(ERROR) << "Could not stage Hecker Recovery";
        return false;
    }
    // Write the verified archive on the original ramdisk so it can be freed
    // before handing its separate tmpfs to OrangeFox.
    android::base::unique_fd cpio(open(kArchive, O_RDWR | O_CREAT | O_EXCL | O_CLOEXEC, 0600));
    if (cpio < 0 || unlink(kArchive) != 0 || !Unpack(input, cpio) ||
        lseek(cpio, 0, SEEK_SET) != 0) {
        LOG(ERROR) << "Could not unpack the Hecker Recovery payload";
        umount2(kRoot, MNT_DETACH);
        return false;
    }

    pid_t child = fork();
    if (child == 0) {
        if (chdir(kRoot) == 0 && dup2(cpio, STDIN_FILENO) == STDIN_FILENO) {
            execl(kCpio, kCpio, "-idu", "--quiet", nullptr);
        }
        _exit(127);
    }

    int status = 0;
    if (child < 0 || TEMP_FAILURE_RETRY(waitpid(child, &status, 0)) != child ||
        !WIFEXITED(status) || WEXITSTATUS(status) != 0 ||
        access("/hecker-recovery/system/bin/init", X_OK) != 0 ||
        access("/hecker-recovery/system/bin/recovery", X_OK) != 0 ||
        access("/hecker-recovery/twres/ui.xml", R_OK) != 0) {
        LOG(ERROR) << "Could not extract Hecker Recovery";
        umount2(kRoot, MNT_DETACH);
        return false;
    }
    return true;
}

}  // namespace

void StartTouchRecovery() {
    if (!InteractiveBoot() || !ExtractRecovery()) {
        return;
    }
    LOG(INFO) << "Starting OrangeFox-based Hecker Recovery";
    // The data stays in the original initramfs across SwitchRoot(). Reclaim the
    // large copy once the isolated recovery filesystem is ready.
    unlink(kPayload);
    SwitchRoot(kRoot);
    execl(kRecoveryInit, kRecoveryInit, "selinux_setup", nullptr);
    PLOG(FATAL) << "Could not exec Hecker Recovery init";
}

}  // namespace android::init::hecker_recovery
