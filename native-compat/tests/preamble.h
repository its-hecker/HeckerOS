// Test-only scalar facades, never copied into the ROM.
#include <sys/cdefs.h>
#undef __THROW
#define __THROW
#define __strong_alias(alias, original)
extern "C" int probe_open_2(const char*, int);
extern "C" int probe_openat_2(int, const char*, int);
