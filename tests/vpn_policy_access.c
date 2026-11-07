// SPDX-License-Identifier: Apache-2.0
// Query the compiled policy, including attributes, rather than merely matching source text.
#include <sepol/sepol.h>
#include <sepol/policydb/services.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void access_check(const char *source, const char *target, const char *class_name,
                         const char *permission, int expected) {
    sepol_security_id_t ssid, tsid;
    sepol_security_class_t tclass;
    sepol_access_vector_t requested;
    struct sepol_av_decision decision;
    if (sepol_context_to_sid(source, strlen(source) + 1, &ssid) ||
        sepol_context_to_sid(target, strlen(target) + 1, &tsid) ||
        sepol_string_to_security_class(class_name, &tclass) ||
        sepol_string_to_av_perm(tclass, permission, &requested) ||
        sepol_compute_av(ssid, tsid, tclass, requested, &decision)) {
        fprintf(stderr, "Could not query HeckerVPN policy access\n"); exit(1);
    }
    int allowed = (decision.allowed & requested) == requested;
    if (allowed != expected) {
        fprintf(stderr, "Unexpected access: %s -> %s %s:%s (allowed=%d expected=%d)\n",
                source, target, class_name, permission, allowed, expected); exit(1);
    }
}

int main(int argc, char **argv) {
    if (argc != 2) return 1;
    FILE *file = fopen(argv[1], "rb");
    if (!file || sepol_set_policydb_from_file(file)) return 1;
    fclose(file);
    const char *app = "u:r:hecker_vpn_app:s0";
    const char *services[] = {"activity_service", "activity_task_service", "package_service",
                              "connectivity_service", "notification_service"};
    for (size_t i = 0; i < sizeof(services) / sizeof(services[0]); i++) {
        char context[128]; snprintf(context, sizeof(context), "u:object_r:%s:s0", services[i]);
        access_check(app, context, "service_manager", "find", 1);
    }
    access_check(app, "u:r:system_server:s0", "binder", "call", 1);
    access_check("u:r:platform_app:s0", app, "binder", "call", 1);
    access_check(app, "u:r:hecker_vpnd:s0", "unix_stream_socket", "connectto", 1);
    access_check("u:r:untrusted_app:s0", "u:r:hecker_vpnd:s0", "unix_stream_socket", "connectto", 0);
    access_check("u:r:platform_app:s0", "u:r:hecker_vpnd:s0", "unix_stream_socket", "connectto", 0);
    access_check(app, "u:object_r:hecker_vpn_socket:s0", "sock_file", "write", 1);
    access_check("u:r:hecker_vpnd:s0", "u:r:hecker_vpnd:s0", "unix_stream_socket", "listen", 1);
    access_check("u:r:hecker_vpnd:s0", "u:r:hecker_vpnd:s0", "unix_stream_socket", "accept", 1);
    access_check("u:r:hecker_vpnd:s0", "u:r:hecker_vpnd:s0", "unix_stream_socket", "getopt", 1);
    access_check("u:r:hecker_vpnd:s0", "u:r:hecker_vpn_app:s0", "unix_stream_socket", "read", 1);
    access_check("u:r:hecker_vpnd:s0", "u:object_r:hecker_vpn_tool_exec:s0", "file", "execute", 1);
    access_check("u:r:netutils_wrapper:s0", "u:r:netutils_wrapper:s0", "capability", "net_admin", 1);
    access_check("u:r:netutils_wrapper:s0", "u:r:netutils_wrapper:s0", "netlink_generic_socket", "create", 1);
    access_check("u:r:hecker_vpn_app:s0:c512,c768", "u:r:hecker_vpnd:s0", "unix_stream_socket", "connectto", 1);
    access_check("u:r:hecker_vpnd:s0", "u:r:hecker_vpn_app:s0:c512,c768", "unix_stream_socket", "read", 1);
    access_check("u:r:hecker_vpnd:s0", "u:r:hecker_vpn_app:s0:c512,c768", "unix_stream_socket", "write", 1);
    access_check("u:r:netutils_wrapper:s0", "u:object_r:dnsproxyd_socket:s0", "sock_file", "write", 1);
    access_check("u:r:netutils_wrapper:s0", "u:r:netd:s0", "unix_stream_socket", "connectto", 1);
    const char *proxy = "u:r:hecker_vpn_proxy:s0";
    access_check("u:r:hecker_vpnd:s0", "u:object_r:hecker_vpn_proxy_exec:s0", "file", "execute", 1);
    access_check(proxy, "u:r:hecker_vpnd:s0", "fd", "use", 1);
    access_check(proxy, "u:r:hecker_vpnd:s0", "unix_stream_socket", "read", 1);
    access_check(proxy, "u:r:hecker_vpnd:s0", "unix_stream_socket", "write", 1);
    access_check(proxy, proxy, "tun_socket", "create", 1);
    access_check(proxy, proxy, "capability", "net_admin", 1);
    access_check(proxy, "u:object_r:tun_device:s0", "chr_file", "open", 1);
    access_check(proxy, "u:object_r:system_security_cacerts_file:s0", "file", "read", 1);
    access_check(proxy, "u:object_r:hecker_vpn_data_file:s0", "file", "write", 1);
    access_check(proxy, "u:object_r:netd_service:s0", "service_manager", "find", 0);
    access_check("u:r:untrusted_app:s0", "u:object_r:hecker_vpn_proxy_exec:s0", "file", "execute", 0);
    puts("HeckerVPN compiled policy: Android service lookup, Binder and private daemon access PASS");
    return 0;
}
