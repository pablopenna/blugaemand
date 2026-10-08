#!/usr/bin/env python3
"""Compile and run the exact upstream patcher on an already-packaged binary.
Only extraction/driver glue is local. This does NOT claim the installed LC version.
"""
import hashlib,json,shutil,subprocess,sys,urllib.request
from pathlib import Path
from macho_audit import MachO,check
pin='4dbe0f9a626de801184a42c0be8d2cb105058e3d'
out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
url=f'https://raw.githubusercontent.com/LiveContainer/LiveContainer/{pin}/LiveContainer/LCMachOUtils.m'
source=urllib.request.urlopen(url,timeout=45).read().decode();(out/'upstream-LCMachOUtils.m').write_text(source)
def function(name):
 pos=source.index(name+'(');a=source.rfind('\n',0,pos)+1;b=source.index('{',pos);depth=1;i=b+1
 while depth:
  depth += (source[i]=='{')-(source[i]=='}');i+=1
 return source[a:i]
names=['rnd32','get_chained_fixups_seg_count','insertDylibCommand','replaceDylinkerWithIDDylibCommand','LCPatchExecSlice']
code='''#import <Foundation/Foundation.h>
#include <mach-o/loader.h>
#include <mach-o/fixup-chains.h>
#include <libgen.h>
#include <assert.h>
#define PATCH_EXEC_RESULT_NO_SPACE_FOR_TWEAKLOADER 1
#define PATCH_EXEC_RESULT_SEG_COUNT_MISMATCH 2
'''+ '\n'.join(function(n) for n in names)+'''
int main(int argc,char **argv) {
 @autoreleasepool {
  NSMutableData *data=[NSMutableData dataWithContentsOfFile:@(argv[1])];
  if(!data) return 9;
  int result=LCPatchExecSlice(argv[1],data.mutableBytes,true);
  printf("patch_flags=%d\\n",result);
  if(![data writeToFile:@(argv[1]) atomically:YES]) return 10;
  return result;
 }
}
'''
(out/'patch.m').write_text(code)
subprocess.run(['clang','-fmodules',str(out/'patch.m'),'-framework','Foundation','-o',str(out/'patch')],check=True)
target=out/'LeagueOfLegends';shutil.copy2(sys.argv[1],target)
before=MachO(target.read_bytes());subprocess.run([str(out/'patch'),str(target)],check=True)
after=MachO(target.read_bytes());check(after.u32(12)==6,'LiveContainer did not convert to MH_DYLIB')
check(after.dependencies[:-1]==before.dependencies,'LiveContainer shifted guest ordinals')
check(after.dependencies[-1]['path']=='@loader_path/../../Tweaks/TweakLoader.dylib','TweakLoader missing')
check(after.sections==before.sections,'LC changed section positions')
for s in before.sections:check(before.section_bytes(s)==after.section_bytes(s),'LC changed section bytes')
check(after.blob(0x80000034)==before.blob(0x80000034),'LC changed chained fixups')
for c,o,n in before.commands:
 if c==0x80000028:
  p=next(p for k,p,_ in after.commands if k==c)
  check(before.data[o:o+n]==after.data[p:p+n],'LC_MAIN changed')
check(not after.code_pages()['valid'],'LC patch must invalidate old signature')
subprocess.run(['codesign','--force','--sign','-',str(target)],check=True)
subprocess.run(['codesign','--verify','--strict',str(target)],check=True)
resigned=MachO(target.read_bytes());check(resigned.code_pages()['valid'],'LC-patched signature invalid')
r=dict(upstream_commit=pin,upstream_url=url,source_sha256=hashlib.sha256(source.encode()).hexdigest(),filetype_after=after.u32(12),ordinals_preserved=True,sections_preserved=True,lc_main_preserved=True,stale_signature_detected=True,signature_after_resign_valid=True,installed_livecontainer_version_verified=False,final_binary=resigned.report())
(out/'livecontainer-patch-audit.json').write_text(json.dumps(r,indent=2));print('PASS: actual pinned LiveContainer patch preserves ordinals/sections/LC_MAIN; re-sign verified')
