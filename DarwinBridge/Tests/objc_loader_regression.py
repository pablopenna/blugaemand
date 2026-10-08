#!/usr/bin/env python3
"""Load actual compiled dylibs: the bad superclass must fail BEFORE constructor.
This is a macOS native runtime reproduction, NOT an iPad execution claim.
"""
import json, os, resource, subprocess, tempfile
from pathlib import Path
out=Path(os.environ.get('DB_AUDIT_DIR','artifacts/loader-audit')).resolve();out.mkdir(parents=True,exist_ok=True)
source='''#import <Foundation/Foundation.h>
#include <unistd.h>
#ifdef BAD_ABI
Class DBFakeNSObject __asm__("_OBJC_CLASS_$_NSObject") = Nil;
#endif
@interface DBRegressionVictim : NSObject @end
@implementation DBRegressionVictim @end
__attribute__((constructor)) static void init(void) { write(2, "CONSTRUCTOR_REACHED\\n", 20); }
'''
host='''#include <dlfcn.h>
#include <stdio.h>
int main(int argc, char **argv) {
 void *h=dlopen(argv[1], RTLD_NOW|RTLD_LOCAL);
 if(!h){fprintf(stderr,"dlopen failed: %s\\n",dlerror());return 2;}
 return 0;
}
'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'probe.m').write_text(source);(p/'host.c').write_text(host)
 subprocess.run(['clang',str(p/'host.c'),'-o',str(p/'host')],check=True)
 result={}
 for mode in ('bad','good'):
  path=p/(mode+'.dylib')
  args=['clang','-dynamiclib',str(p/'probe.m'),'-framework','Foundation','-o',str(path)]
  if mode=='bad':args+=['-DBAD_ABI=1']
  else:args+=['-Wl,-reexport_framework,Foundation']
  subprocess.run(args,check=True)
  def limit():resource.setrlimit(resource.RLIMIT_CORE,(0,0))
  try:
   r=subprocess.run([str(p/'host'),str(path)],text=True,capture_output=True,timeout=15,preexec_fn=limit)
   result[mode]=dict(returncode=r.returncode,stdout=r.stdout,stderr=r.stderr,constructor_reached='CONSTRUCTOR_REACHED' in r.stderr)
  except subprocess.TimeoutExpired as e:
   result[mode]=dict(timeout=True,stderr=str(e.stderr),constructor_reached=b'CONSTRUCTOR_REACHED' in (e.stderr or b''))
 (out/'objc-loader-reproduction.json').write_text(json.dumps(result,indent=2))
 print(json.dumps(result,indent=2))
 assert not result['bad']['constructor_reached'],'bad class unexpectedly allowed constructor'
 assert result['bad'].get('returncode',1)!=0,'bad class unexpectedly loaded'
 assert result['good'].get('returncode')==0 and result['good']['constructor_reached'],'good class did not initialize'
