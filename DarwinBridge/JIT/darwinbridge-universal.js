// DarwinBridge Stage 21G: external loader diagnostics + conservative JIT26.
// log() is emitted by the debugger, even when no guest initializer can run.
// Upstream protocol: LiveContainer 4dbe0f9 Resources/universal.js; LLDB RSP.
// Never skip an unowned BRK. vCont;S is single-step, NOT continue-with-signal.
const commands = {};
const legacyCommands = {};
let tid, pc, x0, x1, x16;
let detached = false;
const epoch = Date.now();
const owned = new Map();
const observed = new Set();
const mappedImages = new Set();
let notification = null;
let imageCache = [];
function event(kind, detail) { log(`[DB21G +${Date.now()-epoch}ms] ${kind} ${detail}`); }
function le(hex) {
    if (typeof hex !== 'string' || !/^(?:[0-9a-f]{2})+$/i.test(hex)) return null;
    return BigInt('0x'+hex.match(/../g).reverse().join(''));
}
function hex64(n) { return BigInt.asUintN(64,n).toString(16).padStart(16,'0').match(/../g).reverse().join(''); }
// Names retained for upstream CMD_NEW_BREAKPOINTS scripts.
const littleEndianHexStringToNumber = le;
const numberToLittleEndianHexString = hex64;
function hexToAscii(hex) {
    if (!/^(?:[0-9a-f]{2})*$/i.test(hex)) throw new Error('invalid memory response');
    let s=''; for(let i=0;i<hex.length;i+=2) { let c=parseInt(hex.slice(i,i+2),16); if(!c) break; s+=String.fromCharCode(c); } return s;
}
function read(address,size) {
    const r=send_command(`m${address.toString(16)},${size.toString(16)}`);
    return typeof r==='string' && r.length===size*2 && /^[0-9a-f]+$/i.test(r) ? r : null;
}
function reg(stop,n) {
    const key=n.toString(16).padStart(2,'0');
    const m=new RegExp(`(?:^T[0-9a-f]{2}|;)${key}:([0-9a-f]{16})(?:;|$)`,'i').exec(stop);
    return m ? le(m[1]) : le(send_command(`p${n.toString(16)};thread:${tid};`));
}
function put(n,value) {
    const r=send_command(`P${n.toString(16)}=${hex64(value)};thread:${tid};`);
    if(r!=='OK') throw new Error(`register write P${n.toString(16)} failed: ${r}`);
}
function frame(stop) {
    const m=/thread:([0-9a-f]+)(?:;|$)/i.exec(stop);
    tid=m ? m[1] : null;
    if(!tid) { const r=send_command('qC'); if(/^QC[0-9a-f]+$/i.test(r)) tid=r.slice(2); }
    if(!tid) throw new Error(`stop has no thread: ${stop}`);
    pc=reg(stop,32); x0=reg(stop,0); x1=reg(stop,1); x16=reg(stop,16);
    event('stop',JSON.stringify({packet:stop,tid,pc:String(pc),x0:String(x0),x1:String(x1),x16:String(x16)}));
    if(pc===null) throw new Error('PC unavailable');
}
function arm(address,label) {
    if(address===0n || owned.has(address.toString()) || observed.has(label) || owned.size>=4) return;
    const r=send_command(`Z1,${address.toString(16)},4`);
    event('breakpoint',`${label} 0x${address.toString(16)} ${r}`);
    if(r==='OK') owned.set(address.toString(),label);
}
function disarm(address) {
    const r=send_command(`z1,${address.toString(16)},4`);
    if(r!=='OK') throw new Error(`cannot remove diagnostic breakpoint: ${r}`);
    owned.delete(address.toString());
}
function inspectImage(img) {
    const name=img.pathname || '';
    if(!/LeagueOfLegends|LeagueofLegends|DBBootstrap|DarwinBridgeLCPlugin/.test(name)) return;
    const base=BigInt(img.load_address);
    const h=read(base,32); if(!h || Number(le(h.slice(0,8)))!==0xfeedfacf) return;
    const count=Number(le(h.slice(32,40))), size=Number(le(h.slice(40,48)));
    if(size>65536 || count>1024) return;
    const raw=read(base+32n,size); if(!raw) return;
    let at=0, textVM=0n, entry=null, offsets=[], initPointers=[];
    function u32(o) { return Number(le(raw.slice(o*2,o*2+8))); }
    function u64(o) { return le(raw.slice(o*2,o*2+16)); }
    function str(o,n) { return hexToAscii(raw.slice(o*2,(o+n)*2)); }
    for(let i=0;i<count && at+8<=size;i++) {
        const cmd=u32(at), len=u32(at+4); if(len<8 || at+len>size) return;
        if(cmd===0x19 && len>=72) {
            if(str(at+8,16)==='__TEXT') textVM=u64(at+24);
            const ns=u32(at+64);
            for(let j=0;j<ns && 72+(j+1)*80<=len;j++) {
                const p=at+72+j*80, kind=u32(p+64)&255;
                const addr=u64(p+32), sz=Number(u64(p+40));
                if(sz>4096) continue;
                if(kind===0x16) offsets.push([addr,sz]);
                if(kind===9) initPointers.push([addr,sz]);
            }
        }
        if(cmd===0x80000028 && len>=24) entry=u64(at+8);
        at+=len;
    }
    event('image',JSON.stringify({name,uuid:img.uuid,base:'0x'+base.toString(16),filetype:Number(le(h.slice(24,32)))}));
    const leaf=name.split('/').pop();
    if(entry!==null) arm(base+entry,leaf+':LC_MAIN');
    for(const [addr,sz] of offsets) {
        const d=read(base+addr-textVM,sz); if(!d) continue;
        for(let j=0;j<sz;j+=4) arm(base+le(d.slice(j*2,j*2+8)),leaf+':initializer:'+j);
    }
    for(const [addr,sz] of initPointers) {
        const d=read(base+addr-textVM,sz); if(!d) continue;
        for(let j=0;j<sz;j+=8) arm(le(d.slice(j*2,j*2+16)),leaf+':initializer:'+j);
    }
}
function images() {
    try {
        const r=send_command('jGetLoadedDynamicLibrariesInfos:{"fetch_all_solibs":true,"information-level":"address-name-uuid"}');
        if(!r || r[0]!=='{') { event('images-unavailable',String(r)); return; }
        const parsed=JSON.parse(r); imageCache=parsed.images || [];
        event('loaded-image-count',String(imageCache.length));
        for(const i of imageCache) {
            const key=String(i.load_address)+':'+String(i.uuid);
            if(!mappedImages.has(key)) { event('image-map',JSON.stringify({path:i.pathname,uuid:i.uuid,base:i.load_address})); mappedImages.add(key); }
        }
        for(const img of imageCache) inspectImage(img);
    } catch(e) { event('image-query-error',String(e)); }
}
function snapshot() {
    event('dyld-state',String(send_command('jGetDyldProcessState')));
    event('threads',String(send_command('qfThreadInfo')));
    if(pc!==undefined && pc!==null) {
        event('pc-memory',`0x${pc.toString(16)} ${read(pc,32)}`);
        event('pc-region',String(send_command(`qMemoryRegionInfo:${pc.toString(16)}`)));
        event('lr',String(send_command(`p1e;thread:${tid};`)));
    }
    images();
}
function setupNotification() {
    const r=send_command('qShlibInfoAddr');
    if(!/^[0-9a-f]+$/i.test(r || '') || /^E[0-9a-f]{2}$/i.test(r)) { event('dyld-notification-unavailable',String(r)); return; }
    const h=read(BigInt('0x'+r),24);
    if(!h) return;
    notification=le(h.slice(32,48)); arm(notification,'dyld:image-notification');
}
function cleanup() {
    for(const address of Array.from(owned.keys())) disarm(BigInt(address));
}
commands[0]=function() { cleanup(); event('detach',String(send_command('D'))); detached=true; };
commands[1]=function() {
    if(x0===null || x1===null || x1<0n || x1>0x100000000n) throw new Error('invalid PREPARE_REGION');
    if(x1===0n) { put(0,x0); return; }
    let rx=x0;
    if(rx===0n) {
        const r=send_command(`_M${x1.toString(16)},rx`);
        if(!/^[0-9a-f]+$/i.test(r || '') || /^E[0-9a-f]{2}$/i.test(r)) { event('allocate-failed',String(r)); put(0,0n); return; }
        rx=BigInt('0x'+r);
    }
    try { const r=prepare_memory_region(rx,x1); event('prepare',`0x${rx.toString(16)} length=${x1} result=${r}`); put(0,r===false ? 0n:rx); }
    catch(e) { event('prepare-failed',String(e)); put(0,0n); }
};
commands[2]=function() {
    if(x0===null || x1===null || x1<=0n || x1>1048576n) throw new Error('invalid NEW_BREAKPOINTS');
    const data=read(x0,Number(x1)); if(!data) throw new Error('cannot read NEW_BREAKPOINTS');
    event('new-breakpoints',`bytes=${x1}`);
    // Official LiveContainer protocol explicitly sends debugger extension code.
    eval(hexToAscii(data));
};
legacyCommands[0x68]=commands[2];
legacyCommands[0xf00d]=function(stop) {
    const fn=commands[Number(x16)]; if(!fn) throw new Error(`unsupported JIT command ${x16}`); fn(stop);
};
let attached=false;
try {
    const pid=get_pid(); event('start',`pid=${pid}`);
    let stop=send_command(`vAttach;${pid.toString(16)}`);
    event('attach',String(stop));
    if(!/^[TS][0-9a-f]{2}/i.test(stop || '')) throw new Error('vAttach did not return a stop');
    attached=true; event('process-info',String(send_command('qProcessInfo'))); frame(stop); snapshot(); setupNotification();
    // The synthetic attach signal is never forwarded. Resume ALL threads.
    let next='vCont;c';
    while(!detached) {
        event('resume',next);
        stop=send_command(next);
        if(/^[WX][0-9a-f]{2}/i.test(stop || '')) { event('exit',stop); attached=false; break; }
        if(!/^[TS][0-9a-f]{2}/i.test(stop || '')) throw new Error(`invalid stop/resume response: ${stop}`);
        frame(stop);
        const trap=stop.slice(1,3).toLowerCase()==='05';
        const key=pc.toString(), label=trap ? owned.get(key) : undefined;
        if(label) {
            event('boundary',label); disarm(pc);
            if(label==='dyld:image-notification') {
                images();
                // One instruction step is owned and intentional; do not alter PC.
                const stepThread=tid, stepPC=pc;
                const step=send_command(`vCont;s:${tid}`);
                if(!/^T05/i.test(step || '')) { event('unexpected-step',String(step)); throw new Error('dyld notification step failed'); }
                frame(step);
                if(tid!==stepThread || pc===stepPC) throw new Error('unexpected thread/PC at notification step');
                arm(notification,label);
            } else { observed.add(label); snapshot(); }
            next='vCont;c'; continue;
        }
        const raw=read(pc,4), insn=raw ? Number(le(raw)) : null;
        const isBRK=insn!==null && ((insn & 0xffe0001f)>>>0)===0xd4200000;
        const imm=isBRK ? (insn>>>5)&65535 : null;
        if(trap && isBRK && legacyCommands[imm] && (imm!==0xf00d || commands[Number(x16)])) {
            event('jit-call',`brk=${imm.toString(16)} command=${x16}`);
            // Only a recognized protocol instruction is advanced.
            put(32,pc+4n); legacyCommands[imm](stop); next='vCont;c'; continue;
        }
        snapshot();
        const sig=stop.slice(1,3);
        event('forward-signal',`${sig} unowned-brk=${imm} PC unchanged`);
        // C = continue with signal; S would single-step and strand other threads.
        next=`vCont;C${sig}:${tid};c`;
    }
} catch(e) {
    event('diagnostic-error',String(e));
    if(attached && !detached) {
        try { cleanup(); event('error-detach',String(send_command('D'))); } catch(cleanupError) { event('detach-error',String(cleanupError)); }
    }
}
