// SPDX-License-Identifier: Apache-2.0
// Include host standard-library declarations before substituting allocator calls.
#include <stdlib.h>
extern "C" void* probe_malloc(size_t);
extern "C" void* probe_realloc(void*, size_t);
extern "C" void probe_free(void*);
#define malloc probe_malloc
#define realloc probe_realloc
#define free probe_free
