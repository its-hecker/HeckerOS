#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the actual SM8150 game-charge inhibitor with a simulated register/workqueue.

This compiles the production driver's control functions, not a second policy implementation.
A full kernel build and wired-power measurements are still required.
"""
import argparse, subprocess, tempfile
from pathlib import Path
HARNESS=r'''
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <assert.h>
#include <sys/types.h>
#define HZ 100
#define PAGE_SIZE 4096
#define CHGR_CHARGING_ENABLE_CMD 0x1042
#define CHARGING_ENABLE_CMD_BIT 1
#define container_of(ptr,type,member) ((type *)((char *)(ptr)-offsetof(type,member)))
#define to_delayed_work(ptr) container_of(ptr,struct delayed_work,work)
#define time_before(a,b) ((long)((a)-(b))<0)
#define scnprintf snprintf
struct mutex {int locked;};
static void mutex_lock(struct mutex *m){assert(!m->locked);m->locked=1;}
static void mutex_unlock(struct mutex *m){assert(m->locked);m->locked=0;}
struct work_struct {int unused;};
struct delayed_work {struct work_struct work;bool pending;unsigned long delay;};
static unsigned long jiffies;
static int system_wq;
static void mod_delayed_work(int queue,struct delayed_work *work,unsigned long delay){(void)queue;work->pending=true;work->delay=delay;}
static void cancel_delayed_work(struct delayed_work *work){work->pending=false;}
struct bms_dev {struct mutex game_charge_lock;struct delayed_work game_charge_timeout;unsigned long game_charge_deadline;bool normal_charge_disabled,game_charge_disabled;void *pmic_regmap;};
struct power_supply {struct bms_dev *data;};
struct device {struct power_supply *data;};
static void *dev_get_drvdata(struct device *dev){return dev->data;}
static struct bms_dev *power_supply_get_drvdata(struct power_supply *psy){return psy->data;}
struct attribute {int unused;};struct device_attribute {struct attribute attr;};
struct attribute_group {struct attribute **attrs;};
#define DEVICE_ATTR_RW(name) struct device_attribute dev_attr_##name
static int kstrtobool(const char *s,bool *b){if(!strcmp(s,"1")||!strcmp(s,"1\n")){*b=true;return 0;}if(!strcmp(s,"0")||!strcmp(s,"0\n")){*b=false;return 0;}return -EINVAL;}
typedef unsigned char u8;
static u8 command=1;static bool io_error;
static int sm8150_masked_write(void *map,int address,u8 mask,u8 val){(void)map;assert(address==0x1042&&mask==1);if(io_error)return -EIO;command=val;return 0;}
'''
TESTS=r'''
int main(void){
 struct bms_dev b={0};struct power_supply psy={.data=&b};struct device dev={.data=&psy};char text[4096];
 (void)sm8150_game_group;
 assert(sm8150_charge_disable(&b,false)==0 && command==1);
 assert(game_charge_disable_store(&dev,NULL,"1",1)==1 && b.game_charge_disabled && command==0);
 assert(b.game_charge_timeout.pending && b.game_charge_timeout.delay==6*HZ);
 assert(game_charge_disable_show(&dev,NULL,text)>0 && !strcmp(text,"1\n"));
 assert(sm8150_charge_disable(&b,false)==0 && command==0); // health cannot defeat game inhibitor
 assert(sm8150_charge_disable(&b,true)==0 && command==0);
 assert(game_charge_disable_store(&dev,NULL,"0",1)==1 && command==0); // preserve thermal/health inhibit
 assert(!b.game_charge_timeout.pending);
 assert(sm8150_charge_disable(&b,false)==0 && command==1);
 assert(game_charge_disable_store(&dev,NULL,"1",1)==1);
 jiffies=5*HZ;sm8150_game_charge_timeout(&b.game_charge_timeout.work);
 assert(b.game_charge_disabled && command==0 && b.game_charge_timeout.delay==HZ);
 assert(game_charge_disable_store(&dev,NULL,"1",1)==1); // heartbeat renews lease
 jiffies=6*HZ;sm8150_game_charge_timeout(&b.game_charge_timeout.work);
 assert(b.game_charge_disabled && command==0);
 jiffies=12*HZ;sm8150_game_charge_timeout(&b.game_charge_timeout.work);
 assert(!b.game_charge_disabled && command==1); // controller death restores normal charging
 io_error=true;assert(game_charge_disable_store(&dev,NULL,"1",1)==-EIO && !b.game_charge_disabled);
 io_error=false;assert(game_charge_disable_store(&dev,NULL,"1",1)==1);
 assert(sm8150_charge_disable(&b,true)==0);
 jiffies=20*HZ;sm8150_game_charge_timeout(&b.game_charge_timeout.work);
 assert(!b.game_charge_disabled && command==0); // timeout never cancels health's own request
 assert(game_charge_disable_store(&dev,NULL,"garbage",7)==-EINVAL);
 assert(sm8150_charge_disable(&b,false)==0 && command==1);
 assert(game_charge_disable_store(&dev,NULL,"1",1)==1);
 io_error=true;jiffies=30*HZ;sm8150_game_charge_timeout(&b.game_charge_timeout.work);
 assert(!b.game_charge_disabled && b.game_charge_timeout.delay==HZ); // retry failed restore
 io_error=false;jiffies+=HZ;sm8150_game_charge_timeout(&b.game_charge_timeout.work);assert(command==1);
 puts("SM8150 production charge gate: health isolation, heartbeat, timeout, readback and I/O recovery PASS");
}
'''
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--cache',type=Path,default=Path('/tmp/hecker-game-kernel-check'));args=p.parse_args()
 if args.source is None:
  import json
  from compile_sdk import fetch
  lock=json.loads((Path(__file__).resolve().parents[1]/'tests/game-kernel-source.lock.json').read_text())
  args.source=fetch(args.cache,'game-sm8150_bms.c',lock)
 source=args.source.read_text();start=source.index('static int sm8150_apply_charge_disable(');end=source.index('static int sm8150_charge_pause(',start)
 with tempfile.TemporaryDirectory(prefix='hecker-charge-gate-') as tmp:
  cpp=Path(tmp)/'gate.c';exe=Path(tmp)/'gate';cpp.write_text(HARNESS+source[start:end]+TESTS)
  subprocess.run(['cc','-std=c11','-Wall','-Werror',str(cpp),'-o',str(exe)],check=True);subprocess.run([str(exe)],check=True)
if __name__=='__main__':main()
