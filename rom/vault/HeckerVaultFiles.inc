// SPDX-License-Identifier: Apache-2.0
// Included inside installd after the existing pinned ownership-checked copier.
// No new Binder API, shell command, SELinux domain or arbitrary privileged path surface.

static constexpr const char* HV_GLOBAL = ".heckervault-restore-v1";
static constexpr const char* HV_PHASE = ".heckervault-phase-v1";
static constexpr const char* HV_TEMP = ".heckervault-journal-tmp-v1";

static bool hv_names(int fd, std::vector<std::string>* names) {
    // dup() shares the directory offset; every scan needs its own description.
    int scan = openat(fd, ".", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (scan < 0) return false;
    DIR* dir = fdopendir(scan);
    if (!dir) { close(scan); return false; }
    bool ok = true;
    for (;;) {
        errno = 0; dirent* item = readdir(dir);
        if (!item) { ok = errno == 0; break; }
        std::string name(item->d_name); if (name == "." || name == "..") continue;
        if (names->size() >= 50000) { ok = false; break; }
        names->push_back(name);
    }
    closedir(dir); return ok;
}
static bool hv_remove(int parent, const std::string& name) {
    struct stat st{};
    if (fstatat(parent, name.c_str(), &st, AT_SYMLINK_NOFOLLOW)) return errno == ENOENT;
    if (S_ISDIR(st.st_mode)) {
        unique_fd folder(openat(parent, name.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        if (folder.get() < 0) return false;
        std::vector<std::string> children; if (!hv_names(folder.get(), &children)) return false;
        for (const auto& child : children) if (!hv_remove(folder.get(), child)) return false;
        return unlinkat(parent, name.c_str(), AT_REMOVEDIR) == 0;
    }
    return unlinkat(parent, name.c_str(), 0) == 0;
}
static bool hv_write_state(int parent, const char* name, uint64_t nonce, int phase) {
    // Publish journal updates atomically; truncating the active journal can make
    // a failed write unrecoverable after a daemon interruption or full disk.
    unique_fd fd(openat(parent, HV_TEMP, O_WRONLY | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0600));
    struct stat st{};
    if (fd.get() < 0 || fstat(fd.get(), &st) || !S_ISREG(st.st_mode) || st.st_uid != getuid() || st.st_nlink != 1) return false;
    std::string value = std::to_string(nonce) + " " + std::to_string(phase) + "\n";
    if (ftruncate(fd.get(), 0) || fchmod(fd.get(), 0600)) return false;
    size_t offset = 0;
    while (offset < value.size()) {
        ssize_t n = write(fd.get(), value.data() + offset, value.size() - offset);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return false;
        offset += static_cast<size_t>(n);
    }
    if (fsync(fd.get()) || renameat(parent, HV_TEMP, parent, name)) return false;
    return fsync(parent) == 0;
}
static bool hv_read_state(int parent, const char* name, uint64_t* nonce, int* phase, bool* exists) {
    *exists = false;
    unique_fd fd(openat(parent, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC));
    if (fd.get() < 0) return errno == ENOENT;
    *exists = true; struct stat st{};
    if (fstat(fd.get(), &st) || !S_ISREG(st.st_mode) || st.st_uid != getuid() || st.st_nlink != 1 || st.st_size < 3 || st.st_size > 64) return false;
    char data[65]{}; ssize_t length = read(fd.get(), data, static_cast<size_t>(st.st_size));
    if (length != st.st_size) return false;
    std::istringstream in(std::string(data, static_cast<size_t>(length))); std::string extra;
    if (!(in >> *nonce >> *phase) || !*nonce || *phase < 0 || *phase > 3 || (in >> extra)) return false;
    return true;
}
struct HvRoot {
    unique_fd fd;
    unique_fd source;
    std::string path;
    std::string fresh, old;
    uint64_t nonce = 0;
    int phase = 0;
    bool active = false;
};
static void hv_set_names(HvRoot& root, uint64_t nonce) {
    root.nonce = nonce;
    root.fresh = ".heckervault-new-" + std::to_string(nonce);
    root.old = ".heckervault-old-" + std::to_string(nonce);
}
static bool hv_reserved(const HvRoot& root, const std::string& name) {
    return name == "lib" || name == HV_GLOBAL || name == HV_PHASE || name == HV_TEMP || name == root.fresh || name == root.old;
}
static bool hv_move_children(int from, int to) {
    std::vector<std::string> names; if (!hv_names(from, &names)) return false;
    for (const auto& name : names) {
        struct stat st{};
        if (!fstatat(to, name.c_str(), &st, AT_SYMLINK_NOFOLLOW) || errno != ENOENT) return false;
        if (renameat(from, name.c_str(), to, name.c_str())) return false;
    }
    return fsync(from) == 0 && fsync(to) == 0;
}
static bool hv_finish_root(HvRoot& root) {
    if (!root.active) return true;
    if (!hv_remove(root.fd.get(), root.old) || !hv_remove(root.fd.get(), root.fresh)) return false;
    if (unlinkat(root.fd.get(), HV_PHASE, 0) && errno != ENOENT) return false;
    return fsync(root.fd.get()) == 0;
}
static bool hv_rollback_root(HvRoot& root) {
    if (!root.active) return true;
    if (root.phase >= 2) {
        std::vector<std::string> names; if (!hv_names(root.fd.get(), &names)) return false;
        for (const auto& name : names) if (!hv_reserved(root, name) && !hv_remove(root.fd.get(), name)) return false;
        // Once the new tree is gone, persist phase 1 before moving old files
        // back. A repeated recovery must never delete already-restored files.
        if (fsync(root.fd.get()) || !hv_write_state(root.fd.get(), HV_PHASE,
                root.nonce, 1)) return false;
        root.phase = 1;
    }
    if (root.phase >= 1) {
        unique_fd old(openat(root.fd.get(), root.old.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        if (old.get() < 0 || !hv_move_children(old.get(), root.fd.get())) return false;
    }
    return hv_finish_root(root);
}
static bool hv_recover(std::vector<HvRoot>& roots) {
    for (auto& root : roots) {
        struct stat st{};
        if (!fstatat(root.fd.get(), HV_TEMP, &st, AT_SYMLINK_NOFOLLOW)) {
            if (!S_ISREG(st.st_mode) || st.st_uid != getuid() || st.st_nlink != 1
                    || unlinkat(root.fd.get(), HV_TEMP, 0)) return false;
        } else if (errno != ENOENT) return false;
    }
    uint64_t nonce = 0; int committed = 0; bool found = false;
    if (roots.empty() || !hv_read_state(roots[0].fd.get(), HV_GLOBAL, &nonce, &committed, &found)) return false;
    if (!found) {
        for (auto& root : roots) {
            uint64_t ignored; int phase; bool stray;
            if (!hv_read_state(root.fd.get(), HV_PHASE, &ignored, &phase, &stray) || stray) return false;
        }
        return true;
    }
    if (committed > 1) return false;
    for (auto& root : roots) {
        uint64_t local = 0; bool phase = false;
        if (!hv_read_state(root.fd.get(), HV_PHASE, &local, &root.phase, &phase) || (phase && local != nonce)) return false;
        root.active = phase; hv_set_names(root, nonce);
    }
    for (auto& root : roots) if (!(committed ? hv_finish_root(root) : hv_rollback_root(root))) return false;
    if (unlinkat(roots[0].fd.get(), HV_GLOBAL, 0) && errno != ENOENT) return false;
    return fsync(roots[0].fd.get()) == 0;
}
static bool hv_prepare(HvRoot& root, uint64_t nonce, uid_t uid) {
    hv_set_names(root, nonce); root.phase = 0; root.active = true;
    if (!hv_write_state(root.fd.get(), HV_PHASE, nonce, 0)
            || mkdirat(root.fd.get(), root.fresh.c_str(), 0700) || mkdirat(root.fd.get(), root.old.c_str(), 0700)) return false;
    unique_fd stage(openat(root.fd.get(), root.fresh.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    if (stage.get() < 0) return false;
    std::vector<std::string> names; if (!hv_names(root.source.get(), &names)) return false;
    std::vector<std::string> failed;
    for (const auto& name : names) {
        if (name == "cache" || name == "code_cache" || name == "lib" || name.rfind(".heckervault-", 0) == 0) return false;
        copy_app_data_recursive(root.source.get(), stage.get(), name.c_str(), root.path, uid, &failed, true, 1);
        if (!failed.empty()) return false;
    }
    return fsync(stage.get()) == 0;
}
static bool hv_swap(HvRoot& root, uint64_t nonce) {
    root.phase = 1; if (!hv_write_state(root.fd.get(), HV_PHASE, nonce, 1)) return false;
    unique_fd old(openat(root.fd.get(), root.old.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    unique_fd stage(openat(root.fd.get(), root.fresh.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    if (old.get() < 0 || stage.get() < 0) return false;
    std::vector<std::string> names; if (!hv_names(root.fd.get(), &names)) return false;
    for (const auto& name : names) if (!hv_reserved(root, name) && renameat(root.fd.get(), name.c_str(), old.get(), name.c_str())) return false;
    if (fsync(old.get()) || fsync(root.fd.get())) return false;
    root.phase = 2; if (!hv_write_state(root.fd.get(), HV_PHASE, nonce, 2)) return false;
    return hv_move_children(stage.get(), root.fd.get());
}

static bool hecker_vault_transfer(const std::optional<std::string>& uuid, const std::string& from,
        const std::string& to, int userId, uid_t uid, const std::string& seInfo, int flags,
        uid_t sourceUid) {
    constexpr int RESTORE = 0x20000000, RECOVER = 0x10000000;
    if (uuid || userId != 0 || uid < 10000 || uid >= 100000 || sourceUid < 10000 || sourceUid >= 100000
            || (flags != RESTORE && flags != RECOVER)) return false;
    const std::string ce = "/data/user/0/", de = "/data/user_de/0/";
    if (to.rfind(ce, 0) != 0) return false;
    const std::string pkg = to.substr(ce.size());
    if (pkg.empty() || pkg.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.") != std::string::npos
            || pkg.front() == '.' || pkg.find("..") != std::string::npos || pkg == "org.hecker.vault") return false;
    std::vector<HvRoot> roots;
    for (const auto& path : {to, de + pkg}) {
        std::string name; auto [base, suffix] = split_app_data_path(nullptr, userId, path);
        unique_fd parent = open_parent_recursive(path, base, &name);
        if (parent.get() < 0) return false;
        unique_fd root(openat(parent.get(), name.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        if (root.get() < 0) { if (path != to && errno == ENOENT) continue; return false; }
        struct stat st{}; if (fstat(root.get(), &st) || st.st_uid != uid) return false;
        HvRoot item; item.fd = std::move(root); item.path = path; roots.push_back(std::move(item));
    }
    if (!hv_recover(roots)) return false;
    if (flags == RECOVER) return true;
    const std::string prefix = "/data/user/0/org.hecker.vault/no_backup/staging/";
    if (from.rfind(prefix, 0) != 0 || from.size() > 768) return false;
    auto source = verify_app_data_source(uuid, from, userId, sourceUid, [](int, const std::string&) {});
    if (!source || source->name != pkg) return false;
    unique_fd payload(openat(source->fd.get(), source->name.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    if (payload.get() < 0) return false;
    for (size_t i = 0; i < roots.size(); ++i) {
        const char* domain = i == 0 ? "ce" : "de";
        unique_fd folder(openat(payload.get(), domain, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        if (folder.get() < 0) return false;
        roots[i].source.reset(openat(folder.get(), pkg.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        if (roots[i].source.get() < 0) return false;
    }
    if (roots.size() == 1) {
        unique_fd folder(openat(payload.get(), "de", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        unique_fd data(folder.get() < 0 ? -1 : openat(folder.get(), pkg.c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
        std::vector<std::string> names; if (data.get() < 0 || !hv_names(data.get(), &names) || !names.empty()) return false;
    }
    uint64_t nonce = 0; if (getrandom(&nonce, sizeof(nonce), 0) != sizeof(nonce) || !nonce) return false;
    if (!hv_write_state(roots[0].fd.get(), HV_GLOBAL, nonce, 0)) return false;
    bool ok = true;
    for (auto& root : roots) if (!hv_prepare(root, nonce, uid)) { ok = false; break; }
    if (ok) for (auto& root : roots) if (!hv_swap(root, nonce)) { ok = false; break; }
    if (ok) for (auto& root : roots) if (selinux_android_restorecon_pkgdir(root.path.c_str(), seInfo.c_str(), uid, SELINUX_ANDROID_RESTORECON_RECURSE) < 0) { ok = false; break; }
    if (ok) ok = hv_write_state(roots[0].fd.get(), HV_GLOBAL, nonce, 1);
    bool recovered = hv_recover(roots);
    return ok && recovered;
}
