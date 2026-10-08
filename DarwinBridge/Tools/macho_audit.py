#!/usr/bin/env python3
"""Independent final-byte audit; stdlib only, runs on Linux and macOS."""
import argparse, hashlib, json, struct, uuid
from pathlib import Path

DYLIBS = {0xC, 0x80000018, 0x8000001F, 0x80000023}
LINKEDIT = {0x1D, 0x1E, 0x26, 0x29, 0x2B, 0x2E, 0x80000033, 0x80000034}
ZERO = {1, 0xC, 0x12}
def check(ok, message):
    if not ok: raise ValueError(message)
def sha(b): return hashlib.sha256(b).hexdigest()

class MachO:
    def __init__(self, data):
        if data[:4] in (b'\xca\xfe\xba\xbe', b'\xca\xfe\xba\xbf'):
            fat64 = data[3] == 0xbf
            for i in range(struct.unpack_from('>I', data, 4)[0]):
                pos = 8 + i * (32 if fat64 else 20)
                if struct.unpack_from('>I', data, pos)[0] == 0x100000C:
                    off, size = struct.unpack_from('>QQ' if fat64 else '>II', data, pos+8)
                    check(off + size <= len(data), 'fat slice exceeds EOF')
                    data = data[off:off+size]; break
            else: raise ValueError('no ARM64 slice')
        self.data = data
        check(len(data) >= 32 and self.u32(0) == 0xfeedfacf, 'not little-endian Mach-O 64')
        self.commands, self.sections, self.segments, self.dependencies = [], [], [], []
        self.symbols = {}
        self.regions = []
        self.end = 32 + self.u32(20)
        check(self.end <= len(data), 'load command table exceeds EOF')
        off = 32
        for _ in range(self.u32(16)):
            check(off + 8 <= self.end, 'truncated command')
            cmd, size = self.u32(off), self.u32(off+4)
            check(size >= 8 and size % 8 == 0 and off+size <= self.end, 'invalid command size')
            self.commands.append((cmd, off, size))
            if cmd == 0x19:
                check(size >= 72, 'short segment')
                va, vs, fo, fs = struct.unpack_from('<QQQQ', data, off+24)
                seg = dict(name=self.string(off+8, off+24), address=va, size=vs, offset=fo, file_size=fs)
                check(fo+fs <= len(data), 'segment exceeds EOF')
                self.segments.append(seg)
                if fs and fo: self.region(fo, fs, seg['name'])
                ns = self.u32(off+64)
                check(72 + ns*80 == size, 'section count/segment size mismatch')
                for j in range(ns):
                    p = off+72+j*80
                    section = dict(name=self.string(p,p+16), segment=self.string(p+16,p+32),
                                   address=self.u64(p+32), size=self.u64(p+40), offset=self.u32(p+48), flags=self.u32(p+64))
                    if section['flags'] & 0xff not in ZERO and section['size']:
                        self.region(section['offset'],section['size'],section['name'])
                        check(fo <= section['offset'] and section['offset']+section['size'] <= fo+fs, 'section outside segment')
                    if self.u32(p+60): self.region(self.u32(p+56),self.u32(p+60)*8,'relocations')
                    self.sections.append(section)
            elif cmd in DYLIBS or cmd == 0xD:
                check(size >= 24 and 24 <= self.u32(off+8) < size, 'invalid dylib name offset')
                path = self.string(off+self.u32(off+8),off+size,terminated=True)
                if cmd in DYLIBS: self.dependencies.append(dict(command=cmd,path=path))
            elif cmd in LINKEDIT:
                check(size == 16, 'short linkedit command')
                self.region(self.u32(off+8),self.u32(off+12),hex(cmd))
            elif cmd in (0x22,0x80000022):
                check(size == 48,'short dyld info')
                for p in range(8,48,8): self.region(self.u32(off+p),self.u32(off+p+4),'dyld info')
            elif cmd == 2:
                check(size == 24,'short symtab')
                self.region(self.u32(off+8),16*self.u32(off+12),'symbols')
                self.region(self.u32(off+16),self.u32(off+20),'strings')
            off += size
        check(off == self.end, 'ncmds and sizeofcmds disagree')
        for a,b,n in self.regions: check(a >= self.end, f'{n} overlaps load commands')
        for cmd,o,_ in self.commands:
            if cmd != 2: continue
            sy,ns,st,sz = struct.unpack_from('<IIII',data,o+8)
            for i in range(ns):
                name,t,sec,desc,value = struct.unpack_from('<IBBHQ',data,sy+16*i)
                check(name < sz, 'bad symbol name offset')
                self.symbols[self.string(st+name,st+sz,True)] = dict(type=t,section=sec,desc=desc,value=value)

    def u32(self,o): return struct.unpack_from('<I',self.data,o)[0]
    def u64(self,o): return struct.unpack_from('<Q',self.data,o)[0]
    def string(self,a,b,terminated=False):
        s=self.data[a:b];check(not terminated or b'\0' in s,'unterminated string')
        return s.split(b'\0')[0].decode('utf-8')
    def region(self,o,s,label):
        if s: check(o > 0 and o+s <= len(self.data), f'{label} outside file'); self.regions.append((o,o+s,label))
    def fileoff(self,va):
        for s in self.segments:
            if s['address'] <= va < s['address']+s['file_size']: return va-s['address']+s['offset']
        raise ValueError('address is not file backed')
    def section_bytes(self,s):
        return b'' if s['flags'] & 0xff in ZERO else self.data[s['offset']:s['offset']+s['size']]
    def blob(self,cmd):
        for c,o,_ in self.commands:
            if c==cmd: return self.data[self.u32(o+8):self.u32(o+8)+self.u32(o+12)]
        return b''
    def initializers(self):
        result=[]
        text=next(s for s in self.segments if s['name']=='__TEXT')
        for s in self.sections:
            if s['flags'] & 0xff == 0x16: # S_INIT_FUNC_OFFSETS (new ld)
                for i in range(0,s['size'],4): result.append(text['address']+self.u32(s['offset']+i))
            elif s['flags'] & 0xff == 9: # S_MOD_INIT_FUNC_POINTERS
                for i in range(0,s['size'],8):
                    raw=self.u64(s['offset']+i)
                    # Linked arm64 ptr64/ptr64-offset chain encodes target in low 36 bits.
                    blob = self.blob(0x80000034)
                    if not blob: result.append(raw); continue
                    starts = struct.unpack_from('<I',blob,4)[0]
                    segindex = next(i for i,g in enumerate(self.segments) if g['address'] <= s['address'] < g['address']+g['size'])
                    rel = struct.unpack_from('<I',blob,starts+4+segindex*4)[0]
                    check(rel>0,'initializer section missing chain start')
                    fmt = struct.unpack_from('<H',blob,starts+rel+6)[0]
                    check(fmt in (2,6) and raw>>63==0,'unsupported initializer pointer format')
                    target = raw & ((1<<36)-1)
                    result.append(target + text['address'] if fmt==6 else target | (((raw>>36)&255)<<56))
        for va in result:
            check(any(s['name']=='__text' and s['address'] <= va < s['address']+s['size'] for s in self.sections),'initializer is not executable __text')
        return result
    def abi_violations(self):
        bad=[]
        for n,s in self.symbols.items():
            if s['type'] & 0xe != 0xe or not s['section']: continue
            sec=self.sections[s['section']-1]
            if n.startswith(('_OBJC_CLASS_$_','_OBJC_METACLASS_$_')) and (sec['name']!='__objc_data' or s['value']+40 > sec['address']+sec['size']):
                bad.append(dict(symbol=n,address=hex(s['value']),section=sec['name'],reason='export is not a class object'))
            if n in ('___CFConstantStringClassReference','___NSConstantStringClassReference'):
                bad.append(dict(symbol=n,address=hex(s['value']),section=sec['name'],reason='native constant-string class identity is shadowed'))
        return bad
    def code_pages(self):
        blob=self.blob(0x1d)
        if not blob: return {'present':False,'valid':False}
        check(len(blob)>=12,'short signature')
        magic,length,count=struct.unpack_from('>III',blob)
        check(magic==0xfade0cc0 and length<=len(blob),'invalid signature superblob')
        result=[]
        for i in range(count):
            slot,off=struct.unpack_from('>II',blob,12+i*8)
            if struct.unpack_from('>I',blob,off)[0]!=0xfade0c02: continue
            cd=blob[off:]; _,size,ver,flags,hofs,ident,special,ncode,limit=struct.unpack_from('>9I',cd)
            hs,ht,platform,pages=struct.unpack_from('4B',cd,36)
            check(size<=len(cd) and hofs+ncode*hs<=size,'invalid code directory')
            check(limit<=len(self.data),'signature code limit past EOF')
            algorithm={1:'sha1',2:'sha256',3:'sha256',4:'sha384'}.get(ht)
            check(algorithm is not None,'unknown signature hash type')
            page=1<<pages if pages else limit
            check(ncode==(limit+page-1)//page,'signature page count mismatch')
            bad=[]
            for n in range(ncode):
                digest=hashlib.new(algorithm,self.data[n*page:min(limit,(n+1)*page)]).digest()[:hs]
                if digest != cd[hofs+n*hs:hofs+(n+1)*hs]:bad.append(n)
            result.append(dict(hash=algorithm,pages=ncode,bad_pages=bad))
        return dict(present=True,valid=bool(result) and all(not x['bad_pages'] for x in result),directories=result)
    def report(self):
        ids=[str(uuid.UUID(bytes=self.data[o+8:o+24])) for c,o,n in self.commands if c==0x1b]
        platforms=[dict(platform=self.u32(o+8),minos=hex(self.u32(o+12)),sdk=hex(self.u32(o+16))) for c,o,n in self.commands if c==0x32]
        entries=[hex(self.u64(o+8)) for c,o,n in self.commands if c==0x80000028]
        return dict(sha256=sha(self.data),bytes=len(self.data),uuid=ids,platforms=platforms,lc_main=entries,cpu=hex(self.u32(4)),filetype=self.u32(12),
                    command_end=self.end,header_slack=min((r[0] for r in self.regions),default=len(self.data))-self.end,
                    dependencies=self.dependencies,initializers=[hex(v) for v in self.initializers()],
                    abi_violations=self.abi_violations(),signature=self.code_pages(),
                    section_hashes=[dict(**s,sha256=sha(self.section_bytes(s))) for s in self.sections])

def compare(original,patched):
    check(original.u32(4)==patched.u32(4)==0x100000c,'ARM64 required')
    check(len(original.sections)==len(patched.sections),'section count changed')
    for a,b in zip(original.sections,patched.sections):
        check(a==b,'section metadata changed')
        check(original.section_bytes(a)==patched.section_bytes(b),'section contents changed')
    before,after=original.dependencies,patched.dependencies
    check(len(after)==len(before)+1,'must append exactly one dependency')
    for a,b in zip(before,after): check(a['command']==b['command'],'dependency ordinal kind changed')
    check(after[-1]['path']=='@executable_path/Frameworks/DBBootstrap.dylib','missing appended bootstrap')
    # All bind/fixup blobs, including import library ordinals, must be byte-identical.
    for cmd,o,size in original.commands:
        if cmd in (0x22,0x80000022):
            other=next(p for c,p,_ in patched.commands if c==cmd)
            check(original.data[o:o+size]==patched.data[other:other+size],'dyld info offsets changed')
            for i in range(8,48,8):
                a,n=original.u32(o+i),original.u32(o+i+4)
                check(original.data[a:a+n]==patched.data[a:a+n],'dyld bind/rebase/export blob changed')
        elif cmd in LINKEDIT-{0x1d}:
            check(original.blob(cmd)==patched.blob(cmd),'linkedit blob changed')
    # n_desc of undefined symbols encodes two-level ordinals for classic binds.
    for n,s in original.symbols.items():
        if s['type'] & 0xe == 0: check(patched.symbols.get(n)==s,'undefined symbol ordinal changed')
    return dict(sections_identical=True,fixup_blobs_identical=True,library_ordinals_preserved=True,
                original_dependencies=len(before),final_dependencies=len(after))

def main():
    ap=argparse.ArgumentParser();ap.add_argument('binary',type=Path);ap.add_argument('--original',type=Path);ap.add_argument('--output',type=Path);ap.add_argument('--require-abi',action='store_true');ap.add_argument('--require-signature',action='store_true');a=ap.parse_args()
    m=MachO(a.binary.read_bytes());r=m.report()
    if a.original:r['comparison']=compare(MachO(a.original.read_bytes()),m)
    if a.output:a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(r,indent=2))
    else:print(json.dumps(r,indent=2))
    if a.require_abi:check(not r['abi_violations'],'invalid runtime identity exports')
    if a.require_signature:check(r['signature']['valid'],'invalid code signature page hashes')
if __name__=='__main__':main()
