#!/usr/bin/env python3
import json,sys
from pathlib import Path
from macho_audit import MachO,check
root=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
files=sorted(root.glob('*.dylib'));check(len(files)==16,'runtime dylib count differs from build contract')
for f in files:
 m=MachO(f.read_bytes());r=m.report();(out/(f.name+'.json')).write_text(json.dumps(r,indent=2))
 check(not m.abi_violations(),f'ABI broken: {f}')
 check(m.code_pages()['valid'],f'bad code signature pages: {f}')
 if f.name=='DarwinBridgeLCPlugin.dylib':
  check(m.initializers(),'missing plugin initializer')
  native={d['path'].split('/')[-1] for d in m.dependencies if d['command']==0x8000001f}
  check({'Foundation','CoreFoundation','UIKit','CFNetwork','Security'}<=native,'native runtime identities not reexported')
  # A superclass must be an undefined native symbol, not a local Class variable.
  check(m.symbols['_OBJC_CLASS_$_NSObject']['section']==0,'NSObject is still shadowed')
  check(m.symbols['___CFConstantStringClassReference']['section']==0,'CFString isa is still shadowed')
  for name in ('NSColor','NSFont','NSTask'):
   for kind in ('CLASS','METACLASS'):
    original=m.symbols[f'_OBJC_{kind}_$_{name}']
    renamed=m.symbols[f'_OBJC_{kind}_$_DBShim{name}']
    check(original['section']>0 and original['section']==renamed['section'] and
          original['value']==renamed['value'],f'{name} {kind} alias is not the actual class object')
 print('PASS',f.name,'initializers',r['initializers'],'signature valid')
