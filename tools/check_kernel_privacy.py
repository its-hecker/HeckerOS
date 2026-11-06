#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Execute the reviewed KSU parser and LSM ordering with kernel boundary fakes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
from compile_sdk import fetch

ROOT=Path(__file__).resolve().parents[1]

PREAMBLE = r'''
#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
typedef uint32_t u32;
#define READ_ONCE(x) (x)
#define __read_mostly
#define __user
#define __nocfi
#define IS_BUILTIN(x) 1
#define CONFIG_KSU 1
#define mutex_lock(x) ((void)(x))
#define mutex_unlock(x) ((void)(x))
static int selinux_hide_list_mutex;
static uid_t uid=10101;
static bool manager, granted, fail_copy, ksu_selinux_hide_is_enabled=true;
struct kuid { uid_t val; };
static struct kuid current_uid(void){return (struct kuid){uid};}
static bool is_appuid(uid_t u){return u%100000>=10000&&u%100000<=19999;}
static bool is_isolated_process(uid_t u){return u%100000>=99000&&u%100000<=99999;}
static bool is_manager(void){return manager;}
static bool ksu_is_allow_uid_for_current(uid_t u){(void)u;return granted;}
static char types[]=":ksu:\0:ksu_file:\0:su:\0:adbroot:";
static char rules[]=":shell:\0:su:";
static char *ksu_hide_type_list=types,*ksu_hide_rule_list=rules;
static size_t ksu_hide_type_len=sizeof(types),ksu_hide_rule_len=sizeof(rules);
struct file {};
static int ksu_copy_from_user_retry(void* to,const void* from,size_t n){
 if(fail_copy)return 1;
 memcpy(to,from,n);return 0;
}
static int original_calls;
static ssize_t original_write(struct file*f,const char*b,size_t n,loff_t*p){
 (void)f;(void)b;(void)n;(void)p;++original_calls;return 43;
}
struct task_security_struct{};struct cred{};
static int selinux_state,perm_error;
#define SECCLASS_PROCESS 1
#define PROCESS__SETEXEC 1
#define PROCESS__SETFSCREATE 1
#define PROCESS__SETKEYCREATE 1
#define PROCESS__SETSOCKCREATE 1
#define PROCESS__SETCURRENT 1
static uint32_t current_sid(void){return 1;}
static int avc_has_perm(int*s,uint32_t a,uint32_t b,int c,int p,void*v){
 (void)s;(void)a;(void)b;(void)c;(void)p;(void)v;return perm_error;
}
'''
TEST = r'''
int main(void){
 selinux_transaction_write_fn=original_write;
 const char* hidden[]={"u:r:ksu:s0","u:object_r:ksu_file:s0","u:r:adbroot:s0","u:r:su:s0"};
 uid_t apps[]={10101,110101,99001,199001};
 for(unsigned u=0;u<sizeof(apps)/sizeof(*apps);u++){
  uid=apps[u];
  for(unsigned t=0;t<sizeof(hidden)/sizeof(*hidden);t++){
   const char*c=hidden[t];size_t n=strlen(c);
   assert(ksu_should_hide_selinux_context(c,n));
   assert(ksu_selinux_transaction_write(0,c,n,0)==-EINVAL);
   perm_error=0;assert(selinux_setprocattr("current",(void*)c,n)==-EINVAL);
   perm_error=-EACCES;assert(selinux_setprocattr("current",(void*)c,n)==-EACCES);
  }
 }
 uid=10101;perm_error=0;
 const char* ordinary="u:r:untrusted_app:s0:c1,c2";
 assert(!ksu_should_hide_selinux_context(ordinary,strlen(ordinary)));
 assert(!ksu_should_hide_selinux_context("u:r:ksu_other:s0",16));
 assert(!ksu_should_hide_selinux_context("u:r:fakeksu:s0",14));
 assert(selinux_setprocattr("current",(void*)ordinary,strlen(ordinary))==42);
 assert(ksu_selinux_transaction_write(0,ordinary,strlen(ordinary),0)==43);
 char exact[10]={'u',':','r',':','k','s','u',':','s','0'};
 assert(ksu_should_hide_selinux_context(exact,sizeof(exact))); // no userspace NUL required
 char oversized[256];memset(oversized,'x',sizeof(oversized));
 assert(!ksu_should_hide_selinux_context(oversized,sizeof(oversized)));
 assert(ksu_selinux_transaction_write(0,oversized,sizeof(oversized),0)==43);
 assert(!ksu_should_hide_selinux_context(0,1));
 assert(!ksu_should_hide_selinux_context(exact,0));
 fail_copy=true;assert(ksu_selinux_transaction_write(0,exact,sizeof(exact),0)==43);fail_copy=false;
 for(int mode=0;mode<3;mode++){
  manager=mode==0;granted=mode==1;ksu_selinux_hide_is_enabled=mode!=2;
  assert(!ksu_should_hide_selinux_context(exact,sizeof(exact)));
  assert(ksu_selinux_transaction_write(0,exact,sizeof(exact),0)==43);
  assert(selinux_setprocattr("current",exact,sizeof(exact))==42);
 }
 manager=granted=false;ksu_selinux_hide_is_enabled=true;
 uid_t admins[]={0,1000,2000,1001000,90000,1090000};
 for(unsigned i=0;i<sizeof(admins)/sizeof(*admins);i++){
  uid=admins[i];assert(!ksu_should_hide_selinux_context(exact,sizeof(exact)));
  assert(ksu_selinux_transaction_write(0,exact,sizeof(exact),0)==43);
 }
 assert(original_calls>0);
 puts("Real KSU parser, bounded copies, app/isolated/secondary-user scope, manager/root/admin bypass and LSM denial ordering PASS");
}
'''


def check_debug_privacy(hooks):
    start=hooks.index('/* Private self-only handshake with bionic.')
    end=hooks.index('\n#endif',start)
    helpers=hooks[start:end]
    preamble=r'''#include <assert.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <errno.h>
#include <string.h>
#include <sys/un.h>
#include <sys/socket.h>
#include <stdio.h>
#include <sys/types.h>
static uid_t uid=10101;
struct kuid{uid_t val;};
static struct kuid current_uid(void){return (struct kuid){uid};}
struct task_security_struct{uint32_t hecker_compatibility_flags;};
struct cred{struct task_security_struct security;};
static struct cred current,scratch;static bool allocation_fails;static int committed;
#define selinux_cred(c) (&(c)->security)
static const struct cred* current_cred(void){return &current;}
static struct cred* prepare_creds(void){scratch=current;return allocation_fails?NULL:&scratch;}
static int commit_creds(struct cred*c){current=*c;++committed;return 0;}
struct path{const char* name;};
#define IS_ERR(p) ((uintptr_t)(p)>(uintptr_t)-4096)
static char* d_path(const struct path*p,char* buffer,size_t size){
 if(strlen(p->name)>=size)return (char*)-ENAMETOOLONG;
 strcpy(buffer,p->name);return buffer;
}
'''
    test=r'''
int main(void){
 struct sockaddr_un sock={.sun_family=AF_UNIX};
 memcpy(sock.sun_path+1,"jdwp-control",12);int length=offsetof(struct sockaddr_un,sun_path)+13;
 struct path adbd={"/dev/socket/adbd"},other={"/dev/socket/other"};
 assert(!hecker_hide_debug_socket((struct sockaddr*)&sock,length));
 assert(!hecker_hide_adbd_path(&adbd));
 assert(selinux_hecker_task_prctl(1,4,0,0,0)==-ENOSYS);
 assert(selinux_hecker_task_prctl(HECKER_APP_PRIVACY_PRCTL,16,0,0,0)==-EINVAL);
 assert(selinux_hecker_task_prctl(HECKER_APP_PRIVACY_PRCTL,4,1,0,0)==-EINVAL);
 assert(selinux_hecker_task_prctl(HECKER_APP_PRIVACY_PRCTL,1UL<<40,0,0,0)==-EINVAL);
 allocation_fails=true;
 assert(selinux_hecker_task_prctl(HECKER_APP_PRIVACY_PRCTL,4,0,0,0)==-ENOMEM);
 assert(committed==0);allocation_fails=false;
 uid_t apps[]={10101,110101,99001,199001};
 for(unsigned i=0;i<sizeof(apps)/sizeof(*apps);i++){
  uid=apps[i];assert(selinux_hecker_task_prctl(HECKER_APP_PRIVACY_PRCTL,4,0,0,0)==0);
  assert(hecker_hide_adbd_path(&adbd));assert(!hecker_hide_adbd_path(&other));
  assert(hecker_hide_debug_socket((struct sockaddr*)&sock,length));
  for(int n=-1;n<length;n++)assert(!hecker_hide_debug_socket((struct sockaddr*)&sock,n));
  assert(!hecker_hide_debug_socket((struct sockaddr*)&sock,length+1));
  sock.sun_family=AF_INET;assert(!hecker_hide_debug_socket((struct sockaddr*)&sock,length));
  sock.sun_family=AF_UNIX;sock.sun_path[1]='X';assert(!hecker_hide_debug_socket((struct sockaddr*)&sock,length));sock.sun_path[1]='j';
  assert(selinux_hecker_task_prctl(HECKER_APP_PRIVACY_PRCTL,0,0,0,0)==0);
  assert(!hecker_hide_debug_socket((struct sockaddr*)&sock,length));
 }
 uid_t admins[]={0,1000,2000,1001000};
 for(unsigned i=0;i<sizeof(admins)/sizeof(*admins);i++){
  uid=admins[i];current.security.hecker_compatibility_flags=4;
  assert(selinux_hecker_task_prctl(HECKER_APP_PRIVACY_PRCTL,4,0,0,0)==-EINVAL);
  assert(!hecker_hide_debugging());assert(!hecker_hide_adbd_path(&adbd));
 }
 puts("Real kernel per-app privacy handshake, credential-copy/reset/failure, exact JDWP address, adbd path and core UID bypass PASS");
}
'''
    with tempfile.TemporaryDirectory(prefix='hecker-debug-privacy-') as temporary:
        out=Path(temporary);source=out/'probe.c';source.write_text(preamble+helpers+test)
        subprocess.run(['gcc','-std=gnu11','-O2','-Wall','-Wextra','-Werror','-fsanitize=address,undefined',str(source),'-o',str(out/'probe')],check=True)
        subprocess.run([str(out/'probe')],check=True,env={**os.environ,'ASAN_OPTIONS':'detect_leaks=0'})
    # Verify that the executed helpers actually guard the corresponding LSM entries.
    assert 'LSM_HOOK_INIT(task_prctl, selinux_hecker_task_prctl)' in hooks
    assert 'if (hecker_hide_debug_socket(address, addrlen))\n\t\treturn -ECONNREFUSED;' in hooks
    assert 'if (hecker_hide_adbd_path(path))\n\t\treturn -ENOENT;' in hooks


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ksu-source',type=Path)
    parser.add_argument('--kernel-source',type=Path)
    args=parser.parse_args()
    lock=json.loads((ROOT/'integration/kernel-privacy.lock.json').read_text())
    for key,attribute in [('kernel','kernel_source'),('kernelsu','ksu_source')]:
        project=lock[key];folder=getattr(args,attribute)
        if folder is None:
            folder=ROOT/'out/kernel-privacy'/project['revision']
            for path,digest in project['files'].items():
                fetch(folder,path,{'url':'https://raw.githubusercontent.com/'+project['repository']+'/'+project['revision']+'/'+path,'sha256':digest})
            setattr(args,attribute,folder)
        for path,digest in project['files'].items():
            if hashlib.sha256((folder/path).read_bytes()).hexdigest()!=digest:raise ValueError('Kernel privacy input differs: '+path)
    ksu=(args.ksu_source/'kernel/feature/selinux_hide.c').read_text()
    a=ksu.index('static inline bool ksu_should_destroy_context(')
    b=ksu.index('\nvoid ksu_add_probe_to_list(',a)
    c=ksu.index('static bool ksu_selinux_private_observer(')
    d=ksu.index('\n#if defined(KSU_COMPAT_USE_SELINUX_STATE)',c)
    hooks=(args.kernel_source/'security/selinux/hooks.c').read_text()
    start=hooks.index('static int selinux_setprocattr(')
    stop=hooks.index('\t/* Obtain a SID for the context',start)
    hook=hooks[start:stop]+'\t(void)tsec;(void)new;(void)sid;(void)ptsid;(void)str;return 42;\n}\n'
    with tempfile.TemporaryDirectory(prefix='hecker-kernel-privacy-') as temporary:
        out=Path(temporary);source=out/'probe.c'
        source.write_text(PREAMBLE+ksu[a:b]+ksu[c:d]+hook+TEST)
        binary=out/'probe'
        subprocess.run(['gcc','-std=gnu11','-O2','-Wall','-Wextra','-Werror',
                        '-fsanitize=address,undefined','-fno-omit-frame-pointer',str(source),'-o',str(binary)],check=True)
        # LSan cannot enumerate this workspace's traced processes; ASan/UBSan remain on.
        subprocess.run([str(binary)],check=True,env={**os.environ,'ASAN_OPTIONS':'detect_leaks=0'})
    check_debug_privacy(hooks)
    print('Full kernel/ROM build and device probes remain required.')


if __name__=='__main__': main()
