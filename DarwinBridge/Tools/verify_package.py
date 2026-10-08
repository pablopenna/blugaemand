#!/usr/bin/env python3
import argparse,json,plistlib,subprocess,zipfile
from pathlib import Path
from macho_audit import MachO,check,compare
p=argparse.ArgumentParser();p.add_argument('ipa',type=Path);p.add_argument('original',type=Path);p.add_argument('out',type=Path);p.add_argument('--sign',action='store_true');a=p.parse_args()
a.out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.ipa) as z:
 check(z.testzip() is None,'ZIP CRC failure');z.extractall(a.out)
app=a.out/'Payload/LeagueOfLegends.app';info=plistlib.loads((app/'Info.plist').read_bytes());exe=app/info['CFBundleExecutable']
original=MachO(a.original.read_bytes());patched=MachO(exe.read_bytes());comparison=compare(original,patched)
identity=json.loads((app/'DarwinBridge-package.json').read_text())
check(identity['source_arm64_sha256']==original.report()['sha256'],'source hash lost during packaging')
check(identity['source_uuid']==original.report()['uuid'],'source UUID lost during packaging')
check(identity['source_lc_main']==original.report()['lc_main'][0],'source entry offset lost during packaging')
check(identity['source_file_name']==a.original.name,'source filename lost during packaging')
check(identity['role']=='unclassified-executable','package mislabels an unverified executable')
check(patched.u32(12)==2,'packager must keep executable until LiveContainer patches it')
check(not patched.code_pages()['present'],'stale Riot signature still attached')
check(any(c==0x32 and patched.u32(o+8)==2 or c==0x25 for c,o,_ in patched.commands),'iOS platform missing')
check(any(c==0x80000028 for c,_,_ in patched.commands),'LC_MAIN absent')
files=[exe]+sorted((app/'Frameworks').glob('*.dylib'))
report={'comparison':comparison,'source_identity':identity,'binaries':{},'unresolved_bundle_dependencies':[]}
class_owners={}
for f in files:
 m=MachO(f.read_bytes());check(m.u32(4)==0x100000c,'non ARM64 binary');check(not m.abi_violations(),'invalid class export')
 check(any(c==0x32 and m.u32(o+8)==2 or c==0x25 for c,o,_ in m.commands),'non-iOS binary')
 if f.name=='DBBootstrap.dylib':
  check(m.u32(12)==6 and m.initializers(),'invalid bootstrap initializer')
  check(not any(s['name'].startswith('__objc') for s in m.sections),'bootstrap depends on ObjC metadata')
  check(all(d['path'].startswith('/usr/lib/libSystem') for d in m.dependencies),'bootstrap must only depend on libSystem')
 if f.name.startswith('DB') and f.name!='DBBootstrap.dylib':
  check(not any(s['name'].startswith('__objc') for s in m.sections),'forwarder duplicates ObjC classes')
  check(any(d['command']==0x8000001f and d['path']=='@loader_path/DarwinBridgeLCPlugin.dylib' for d in m.dependencies),'missing implementation reexport')
 for n,s in m.symbols.items():
  if n.startswith('_OBJC_CLASS_$_') and s['section']:
   check(n not in class_owners,'duplicate ObjC class definition');class_owners[n]=f.name
 for d in m.dependencies:
  path=d['path']
  target=None
  if path.startswith('@executable_path/'):target=app/path[len('@executable_path/'):]
  if path.startswith('@loader_path/'):target=f.parent/path[len('@loader_path/'):]
  if target is not None:check(target.is_file(),f'missing bundled dependency: {f}: {path}')
  if path.startswith('@rpath/'):
   report['unresolved_bundle_dependencies'].append({'binary':f.name,'path':path,'reason':'minimal packager does not bundle Riot frameworks'})
 if a.sign:
  subprocess.run(['codesign','--force','--sign','-',str(f)],check=True)
  subprocess.run(['codesign','--verify','--strict','--verbose=2',str(f)],check=True)
  m=MachO(f.read_bytes());check(m.code_pages()['valid'],'bad signature page hash')
 report['binaries'][f.name]=m.report()
 if a.sign:
  text=[]
  for args in [['file',str(f)],['otool','-hV',str(f)],['otool','-l',str(f)],['otool','-L',str(f)],['nm','-m',str(f)],['codesign','-dvvv',str(f)],['vtool','-show-build',str(f)]]:
   r=subprocess.run(args,capture_output=True,text=True);text.append('$ '+' '.join(args)+'\n'+r.stdout+r.stderr)
  (a.out/(f.name+'.inspection.txt')).write_text('\n'.join(text))
report['signed_comparison']=compare(original,MachO(exe.read_bytes()))
report['device_execution_proven']=False
report['fixture_structure_passed']=not report['unresolved_bundle_dependencies']
report['full_game_candidate_approved']=False
(a.out/'package-audit.json').write_text(json.dumps(report,indent=2))
if a.sign:
 subprocess.run(['codesign','--force','--sign','-',str(app)],check=True)
 subprocess.run(['codesign','--verify','--deep','--strict',str(app)],check=True)
 with zipfile.ZipFile(a.out/'audited-fixture.ipa','w',zipfile.ZIP_DEFLATED) as z:
  for f in sorted(app.rglob('*')):
   if f.is_file():z.write(f,f.relative_to(a.out))
print(json.dumps({k:v for k,v in report.items() if k!='binaries'},indent=2))
