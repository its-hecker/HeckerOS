// SPDX-License-Identifier: Apache-2.0
// The verifier/copier below is extracted from the patched, pinned Android source.
#include <cassert>
#include <cerrno>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <functional>
#include <iostream>
#include <memory>
#include <optional>
#include <sstream>
#include <string>
#include <sys/random.h>
#include <sys/stat.h>
#include <unistd.h>
#include <utility>
#include <vector>
#include <filesystem>

namespace android::base {
class unique_fd {
 int value=-1;
public:
 unique_fd()=default;explicit unique_fd(int value):value(value){}~unique_fd(){reset();}
 unique_fd(unique_fd&& other)noexcept:value(other.release()){}
 unique_fd& operator=(unique_fd&& other)noexcept{reset(other.release());return *this;}
 unique_fd(const unique_fd&)=delete;unique_fd& operator=(const unique_fd&)=delete;
 int get()const{return value;}int release(){int old=value;value=-1;return old;}
 void reset(int next=-1){if(value>=0)close(value);value=next;}
};
DIR* Fdopendir(unique_fd fd){return fdopendir(fd.release());}
}
using android::base::unique_fd;using android::base::Fdopendir;
#define PLOG(level) std::cerr
struct IAppDataOperationCallback {static constexpr int STATUS_FAILURE=3;};
static bool copy_simple_file(int from,int to,uid_t uid,gid_t gid,mode_t mode,off_t size,std::function<void(const char*)> error){
 char bytes[4096];off_t copied=0;ssize_t length;
 while((length=read(from,bytes,sizeof(bytes)))>0){if(write(to,bytes,length)!=length){error("write");return false;}copied+=length;}
 if(length<0||copied!=size||fchown(to,uid,gid)||fchmod(to,mode&07777)){error("copy");return false;}return true;
}
struct PinnedPath {unique_fd fd;std::string name,path,appDataPath;};
// Boundary stubs are not exercised by these fd-relative transaction tests.
static std::optional<PinnedPath> verify_app_data_source(const std::optional<std::string>&,const std::string&,int,int32_t,std::function<void(int,const std::string&)>,bool=false){return std::nullopt;}
static std::pair<std::string,std::string> split_app_data_path(const char*,int,const std::string& path){return {path,path};}
static unique_fd open_parent_recursive(const std::string&,const std::string&,std::string*){return unique_fd(-1);}
static int selinux_android_restorecon_pkgdir(const char*,const char*,uid_t,int){return 0;}
static constexpr int SELINUX_ANDROID_RESTORECON_RECURSE=1;
#include "vault_installd_copy.inc"
#include "HeckerVaultFiles.inc"

namespace fs=std::filesystem;
static void file(const fs::path& p,const std::string& value){fs::create_directories(p.parent_path());int fd=open(p.c_str(),O_WRONLY|O_CREAT|O_TRUNC,0600);assert(fd>=0);assert(write(fd,value.data(),value.size())==static_cast<ssize_t>(value.size()));close(fd);}
static std::string read_file(const fs::path& p){int fd=open(p.c_str(),O_RDONLY);assert(fd>=0);char data[256];ssize_t n=read(fd,data,sizeof(data));close(fd);assert(n>=0);return std::string(data,n);}
static HvRoot root(const fs::path& target,const fs::path& source){HvRoot r;r.fd.reset(open(target.c_str(),O_RDONLY|O_DIRECTORY));r.source.reset(open(source.c_str(),O_RDONLY|O_DIRECTORY));r.path=target;assert(r.fd.get()>=0&&r.source.get()>=0);return r;}
int main(){
 char temp[]="/tmp/vault-native-XXXXXX";assert(mkdtemp(temp));fs::path base=temp;
 auto finish=[&]{fs::remove_all(base);};
 fs::create_directories(base/"source/cache");fs::create_directories(base/"source/files/cache");fs::create_directory(base/"copy");
 file(base/"source/cache/skip","cache");file(base/"source/files/cache/keep","private data");file(base/"outside","outside");
 fs::create_symlink(base/"outside",base/"source/lib");fs::create_symlink(base/"outside",base/"source/files/link");
 unique_fd parent(open(base.c_str(),O_RDONLY|O_DIRECTORY)),dest(open((base/"copy").c_str(),O_RDONLY|O_DIRECTORY));
 bool failed=false;
 assert(verify_app_data_recursive(parent.get(),"source",getuid(),"source",[&](int,const std::string&){failed=true;},true));
 assert(!verify_app_data_recursive(parent.get(),"source",getuid()+1,"source",[](int,const std::string&){},true));
 std::vector<std::string> errors;copy_app_data_recursive(parent.get(),dest.get(),"source","source",getuid(),&errors,true);
 assert(errors.empty()&&!fs::exists(base/"copy/source/cache")&&!fs::exists(base/"copy/source/lib")&&!fs::exists(base/"copy/source/files/link"));
 assert(read_file(base/"copy/source/files/cache/keep")=="private data"&&read_file(base/"outside")=="outside");
 for(const char* domain:{"ce","de"}){file(base/domain/"files/current","old");file(base/domain/"cache/stale","cache");file(base/(std::string(domain)+"-new")/"files/restored","new");}
 fs::create_symlink(base/"outside",base/"ce/lib");
 auto roots=[&]{std::vector<HvRoot> list;list.push_back(root(base/"ce",base/"ce-new"));list.push_back(root(base/"de",base/"de-new"));return list;};
 {
  auto list=roots();uint64_t nonce=123;assert(hv_write_state(list[0].fd.get(),HV_GLOBAL,nonce,0));
  for(auto& r:list)assert(hv_prepare(r,nonce,getuid()));
  assert(hv_swap(list[0],nonce));
  assert(!fs::exists(base/"ce/files/current")&&fs::exists(base/"ce/files/restored"));
 }
 {auto list=roots();assert(hv_recover(list));assert(read_file(base/"ce/files/current")=="old"&&read_file(base/"de/files/current")=="old");assert(!fs::exists(base/"ce/files/restored"));}
 // Interrupt recovery itself after one old child has been put back. A second
 // recovery must retain that child and finish the remaining rollback.
 file(base/"ce/prefs/current","old prefs");
 {
  auto list=roots();uint64_t nonce=234;assert(hv_write_state(list[0].fd.get(),HV_GLOBAL,nonce,0));
  for(auto& r:list)assert(hv_prepare(r,nonce,getuid()));
  assert(hv_swap(list[0],nonce));
  assert(hv_remove(list[0].fd.get(),"files"));
  assert(hv_write_state(list[0].fd.get(),HV_PHASE,nonce,1));
  unique_fd old(openat(list[0].fd.get(),list[0].old.c_str(),O_RDONLY|O_DIRECTORY|O_NOFOLLOW));
  assert(renameat(old.get(),"files",list[0].fd.get(),"files")==0);
 }
 {auto list=roots();assert(hv_recover(list));assert(hv_recover(list));assert(read_file(base/"ce/files/current")=="old"&&read_file(base/"ce/prefs/current")=="old prefs");}
 {
  auto list=roots();uint64_t nonce=456;assert(hv_write_state(list[0].fd.get(),HV_GLOBAL,nonce,0));for(auto& r:list)assert(hv_prepare(r,nonce,getuid()));
  for(auto& r:list)assert(hv_swap(r,nonce));
  assert(hv_write_state(list[0].fd.get(),HV_GLOBAL,nonce,1));
 }
 {auto list=roots();assert(hv_recover(list));assert(read_file(base/"ce/files/restored")=="new"&&read_file(base/"de/files/restored")=="new");assert(!fs::exists(base/"ce/cache"));assert(fs::is_symlink(base/"ce/lib"));assert(read_file(base/"outside")=="outside");}
 // A symlink in the journal position is refused without touching its target.
 fs::create_symlink(base/"outside",base/"ce"/HV_GLOBAL);{auto list=roots();assert(!hv_recover(list));}assert(read_file(base/"outside")=="outside");fs::remove(base/"ce"/HV_GLOBAL);
 file(base/"ce"/HV_TEMP,"incomplete write");{auto list=roots();assert(hv_recover(list));}assert(!fs::exists(base/"ce"/HV_TEMP));
 finish();std::cout<<"Pinned installd ownership/copy code and CE/DE swap recovery, transient exclusion, no-follow links: PASS\n";
}
