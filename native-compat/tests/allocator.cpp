// SPDX-License-Identifier: Apache-2.0
// Test-only allocator boundary: exercise failure, cleanup and errno preservation.
#include <errno.h>
#include <stddef.h>
#include <stdlib.h>
#include <sys/mman.h>
static bool fail;
static size_t calls, outstanding;
extern "C" void probe_fail_allocations(bool value) { fail = value; }
extern "C" size_t probe_allocation_calls() { return calls; }
extern "C" size_t probe_outstanding_allocations() { return outstanding; }
extern "C" void* probe_map(size_t size) {
    ++calls;
    if (fail) { errno = ENOMEM; return MAP_FAILED; }
    void* result = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (result != MAP_FAILED) ++outstanding;
    return result;
}
extern "C" int probe_unmap(void* data, size_t size) {
    int result = munmap(data, size);
    if (result == 0) --outstanding;
    errno = ECHILD;
    return result;
}
extern "C" void* probe_malloc(size_t size) {
    ++calls;
    if (fail) { errno = ENOMEM; return nullptr; }
    void* result = malloc(size);
    if (result) ++outstanding;
    return result;
}
extern "C" void* probe_realloc(void* data, size_t size) {
    ++calls;
    if (fail) { errno = ENOMEM; return nullptr; }
    bool fresh = data == nullptr;
    void* result = realloc(data, size);
    if (result && fresh) ++outstanding;
    return result;
}
extern "C" void probe_free(void* data) {
    if (data) --outstanding;
    free(data);
    errno = ECHILD;  // Production callers must preserve their original errno.
}
